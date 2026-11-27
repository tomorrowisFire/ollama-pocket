package com.ollamapocket.app;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** App-private, data-only model installation. All large I/O belongs on a worker thread. */
final class VoiceModelStore {
    interface Progress { void update(long downloaded,long total); }
    /** In-memory/fault-injection transport for host tests; production always uses verified HTTPS. */
    interface Source { InputStream open(String url) throws IOException; }
    static final class BusyException extends IOException { BusyException(){super("Voice model in use");} }
    static final class CancelledException extends IOException { CancelledException(){super("Voice model operation cancelled");} }
    private static final String MARKER=".installed";
    private static final long NETWORK_NANOS=30L*60*1000*1000*1000;
    private static final Map<String,State> STATES=new HashMap<>();
    private static final class State {
        final File root;
        final Map<String,Integer> leases=new HashMap<>();
        final Set<String> changing=new HashSet<>();
        State(File root){this.root=root;}
    }
    private final File requestedRoot;
    private final Source source;
    private volatile Download activeDownload;
    VoiceModelStore(File root){this(root,null);}
    VoiceModelStore(File root,Source source){
        if(root==null)throw new IllegalArgumentException("Model root");
        this.requestedRoot=root.getAbsoluteFile();this.source=source;
    }
    private State state(boolean create)throws IOException{
        File parent=requestedRoot.getParentFile();
        if(parent==null)throw new IOException("Model root");
        File root=new File(parent.getCanonicalFile(),requestedRoot.getName());
        if(Files.isSymbolicLink(root.toPath()))throw new IOException("Linked model root");
        if(create&&!root.isDirectory()&&!root.mkdir())throw new IOException("Cannot create model root");
        if(!root.isDirectory()||!root.getCanonicalFile().equals(root))throw new IOException("Invalid model root");
        synchronized(STATES){return STATES.computeIfAbsent(root.getPath(),key->new State(root));}
    }
    private static File target(State state,VoiceModelCatalog.Model model)throws IOException{
        File directory=new File(state.root,model.id);
        if(!directory.getCanonicalFile().equals(directory)||Files.isSymbolicLink(directory.toPath()))throw new IOException("Linked model directory");
        return directory;
    }
    private static byte[] marker(VoiceModelCatalog.Model model){
        return ("onhand-voice-model-v1\n"+model.id+"\n"+model.sha256+"\n").getBytes(StandardCharsets.US_ASCII);
    }
    private static File checkedFile(File directory,String name)throws IOException{
        File file=new File(directory,name);
        if(!file.getCanonicalFile().equals(file)||!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid model file");
        return file;
    }
    private static boolean metadata(File directory,VoiceModelCatalog.Model model)throws IOException{
        if(!VoiceModelTree.matches(directory.toPath(),model.entries.keySet(),model.directories,MARKER))return false;
        byte[] expected=marker(model);File stamp=checkedFile(directory,MARKER);
        if(stamp.length()!=expected.length)return false;
        byte[] actual=new byte[expected.length];
        try(InputStream in=new FileInputStream(stamp)){readExactly(in,actual,actual.length,()->false);if(in.read()!=-1)return false;}
        if(!MessageDigest.isEqual(expected,actual))return false;
        for(VoiceModelCatalog.Entry entry:model.entries.values())if(checkedFile(directory,entry.name).length()!=entry.bytes)return false;
        return true;
    }
    /** Metadata only. It is safe to use for UI availability; acquire performs full verification. */
    boolean ready(VoiceModelCatalog.Model model){
        try{State state=state(false);synchronized(state){return !state.changing.contains(model.id)&&metadata(target(state,model),model);}}
        catch(IOException|RuntimeException e){return false;}
    }
    boolean present(VoiceModelCatalog.Model model){
        try{State state=state(false);synchronized(state){return new File(state.root,model.id).exists()||Files.isSymbolicLink(new File(state.root,model.id).toPath());}}
        catch(IOException|RuntimeException e){return false;}
    }
    final class Lease implements AutoCloseable {
        private final State state;
        private final VoiceModelCatalog.Model model;
        private final File directory;
        private boolean closed;
        private Lease(State state,VoiceModelCatalog.Model model,File directory){this.state=state;this.model=model;this.directory=directory;}
        File directory(){synchronized(state){if(closed)throw new IllegalStateException("Closed model lease");return directory;}}
        File file(String name)throws IOException{
            synchronized(state){
                if(closed||!model.entries.containsKey(name))throw new IOException("Model file not leased");
                return checkedFile(directory,name);
            }
        }
        @Override public void close(){synchronized(state){
            if(closed)return;closed=true;int count=state.leases.getOrDefault(model.id,0);
            if(count<=1)state.leases.remove(model.id);else state.leases.put(model.id,count-1);
        }}
    }
    Lease acquire(VoiceModelCatalog.Model model,BooleanSupplier cancelled)throws IOException{
        check(cancelled);State state=state(false);final Lease lease;
        synchronized(state){
            if(state.changing.contains(model.id))throw new BusyException();
            File directory=target(state,model);
            if(!metadata(directory,model))throw new IOException("Voice model is not installed");
            state.leases.put(model.id,state.leases.getOrDefault(model.id,0)+1);lease=new Lease(state,model,directory);
        }
        try{
            for(VoiceModelCatalog.Entry entry:model.entries.values())verify(lease.file(entry.name),entry.bytes,entry.sha256,cancelled);
            check(cancelled);return lease;
        }catch(IOException|RuntimeException|Error error){lease.close();throw error;}
    }
    void remove(VoiceModelCatalog.Model model)throws IOException{
        State state=state(false);File directory;
        synchronized(state){
            if(state.changing.contains(model.id)||state.leases.getOrDefault(model.id,0)>0)throw new BusyException();
            directory=new File(state.root,model.id);state.changing.add(model.id);
        }
        try{deleteTree(directory);}finally{synchronized(state){state.changing.remove(model.id);}}
    }
    void install(VoiceModelCatalog.Model model,BooleanSupplier cancelled,Progress progress)throws IOException{
        check(cancelled);State state=state(true);final File destination;
        synchronized(state){
            if(state.changing.contains(model.id)||state.leases.getOrDefault(model.id,0)>0)throw new BusyException();
            destination=target(state,model);
            if(destination.exists())throw new IOException("Remove the existing voice model before installing again");
            if(state.root.getUsableSpace()<model.requiredSpace())throw new IOException("Not enough storage for voice model");
            state.changing.add(model.id);
        }
        Download download=new Download(cancelled);File staging=null;
        try{
            synchronized(this){if(activeDownload!=null)throw new BusyException();activeDownload=download;}
            cleanupAbandoned(state.root,model.id);
            staging=Files.createTempDirectory(state.root.toPath(),".install-"+model.id+"-").toFile();
            if(model.format==VoiceModelCatalog.Format.FILES){
                long completed=0;
                for(VoiceModelCatalog.Entry entry:model.entries.values()){
                    download.check();
                    long offset=completed;
                    File output=new File(staging,entry.name);
                    if(!output.getCanonicalFile().equals(output))throw new IOException("Linked model output");
                    download.download(model.fileUrl(entry),entry.bytes,entry.sha256,output,
                        progress==null?null:(done,ignored)->progress.update(offset+done,model.archiveBytes));
                    completed=Math.addExact(completed,entry.bytes);
                }
                if(completed!=model.archiveBytes)throw new IOException("Incomplete model file set");
            }else{
                File archive=new File(staging,".download.part");
                download.download(model.url,model.archiveBytes,model.sha256,archive,progress);
                download.check();
                if(model.format==VoiceModelCatalog.Format.RAW){
                    VoiceModelCatalog.Entry entry=model.entries.values().iterator().next();
                    Files.move(archive.toPath(),new File(staging,entry.name).toPath(),StandardCopyOption.ATOMIC_MOVE);
                }else{
                    try(InputStream raw=new FileInputStream(archive);
                        InputStream decoded=new BZip2CompressorInputStream(raw,false)){
                        extractTar(decoded,staging,model,download::cancelled);
                    }
                    Files.delete(archive.toPath());
                }
            }
            download.check();
            for(VoiceModelCatalog.Entry entry:model.entries.values())verify(checkedFile(staging,entry.name),entry.bytes,entry.sha256,download::cancelled);
            try(FileOutputStream stamp=new FileOutputStream(new File(staging,MARKER))){stamp.write(marker(model));stamp.getFD().sync();}
            if(!metadata(staging,model))throw new IOException("Unexpected staged voice model tree");
            synchronized(state){
                download.check();
                if(destination.exists())throw new IOException("Voice model destination changed");
                Files.move(staging.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
                staging=null;
            }
        }finally{
            download.disconnect();
            synchronized(this){if(activeDownload==download)activeDownload=null;}
            try{if(staging!=null)deleteTree(staging);}finally{synchronized(state){state.changing.remove(model.id);}}
        }
    }
    /** Request cancellation; potentially blocking transport cleanup never runs on the UI thread. */
    void cancelPendingInstall(){Download download=activeDownload;if(download!=null)download.cancel();}
    private final class Download {
        final BooleanSupplier external;
        final AtomicBoolean stopped=new AtomicBoolean();
        final long started=System.nanoTime();
        volatile HttpURLConnection connection;
        volatile InputStream input;
        Download(BooleanSupplier external){this.external=external;}
        boolean cancelled(){return stopped.get()||external!=null&&external.getAsBoolean()||Thread.currentThread().isInterrupted()||System.nanoTime()-started>NETWORK_NANOS;}
        void check()throws IOException{if(cancelled())throw new CancelledException();}
        void cancel(){if(stopped.compareAndSet(false,true)){
            Thread thread=new Thread(this::disconnect,"voice-model-cancel");thread.setDaemon(true);thread.start();
        }}
        void disconnect(){
            HttpURLConnection current=connection;connection=null;if(current!=null)current.disconnect();
            InputStream currentInput=input;input=null;if(currentInput!=null)try{currentInput.close();}catch(IOException ignored){}
        }
        InputStream network(String address,long expectedBytes)throws IOException{
            URL url=new URL(address);
            for(int redirects=0;redirects<=5;redirects++){
                check();validateDownloadUrl(url);
                HttpURLConnection request=(HttpURLConnection)url.openConnection();connection=request;
                request.setInstanceFollowRedirects(false);request.setConnectTimeout(15000);request.setReadTimeout(15000);
                request.setRequestProperty("Accept-Encoding","identity");request.setRequestProperty("User-Agent","OnHand-Voice-Models/1");
                int status=request.getResponseCode();check();
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=request.getHeaderField("Location");request.disconnect();connection=null;
                    if(location==null||redirects==5)throw new IOException("Voice model redirect limit");
                    url=new URL(url,location);continue;
                }
                if(status!=200)throw new IOException("Voice model download failed");
                String encoding=request.getHeaderField("Content-Encoding");
                if(encoding!=null&&!encoding.isEmpty()&&!"identity".equalsIgnoreCase(encoding))throw new IOException("Encoded voice model response");
                long length=request.getContentLengthLong();
                if(length>=0&&length!=expectedBytes)throw new IOException("Voice model response size changed");
                return request.getInputStream();
            }
            throw new IOException("Voice model redirect limit");
        }
        void download(String url,long expectedBytes,String expectedSha,File output,Progress progress)throws IOException{
            MessageDigest digest=digest();long count=0;byte[] buffer=new byte[65536];
            check();input=source==null?network(url,expectedBytes):source.open(url);
            try(InputStream in=input;
                java.nio.channels.FileChannel file=java.nio.channels.FileChannel.open(output.toPath(),
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                OutputStream out=java.nio.channels.Channels.newOutputStream(file)){
                for(;;){
                    check();int read=in.read(buffer);if(read<0)break;if(read==0)throw new IOException("Empty model read");
                    count+=read;if(count>expectedBytes)throw new IOException("Voice model exceeds expected size");
                    digest.update(buffer,0,read);out.write(buffer,0,read);
                    if(progress!=null)progress.update(count,expectedBytes);
                }
                check();if(count!=expectedBytes||!hex(digest.digest()).equals(expectedSha))throw new IOException("Voice model checksum or size mismatch");
                file.force(true);
            }finally{disconnect();}
        }
    }
    static void validateDownloadUrl(URL url)throws IOException{
        String host=url.getHost().toLowerCase(Locale.ROOT);
        boolean trusted=host.equals("huggingface.co")||host.endsWith(".huggingface.co")||host.equals("hf.co")||host.endsWith(".hf.co")||
            host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com");
        if(!"https".equals(url.getProtocol())||!trusted||url.getUserInfo()!=null||url.getRef()!=null||url.getPort()!=-1&&url.getPort()!=443)
            throw new IOException("Untrusted voice model download URL");
        try{URI uri=url.toURI();if(uri.getHost()==null)throw new IOException("Invalid model URL");}catch(java.net.URISyntaxException e){throw new IOException("Invalid model URL",e);}
    }
    private static void cleanupAbandoned(File root,String id)throws IOException{
        File[] files=root.listFiles();if(files==null)throw new IOException("Cannot inspect model storage");
        if(files.length>128)throw new IOException("Model storage entry limit");
        for(File file:files)if(file.getName().startsWith(".install-"+id+"-"))deleteTree(file);
    }
    private static void deleteTree(File file)throws IOException{
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return;
        Files.walkFileTree(file.toPath(),EnumSet.noneOf(FileVisitOption.class),8,new SimpleFileVisitor<Path>(){
            int entries;
            @Override public FileVisitResult preVisitDirectory(Path dir,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{
                if(++entries>256)throw new IOException("Model cleanup entry limit");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{
                if(++entries>256)throw new IOException("Model cleanup entry limit");Files.delete(path);return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir,IOException error)throws IOException{
                if(error!=null)throw error;Files.delete(dir);return FileVisitResult.CONTINUE;
            }
        });
    }
    private static void check(BooleanSupplier cancelled)throws IOException{if(Thread.currentThread().isInterrupted()||cancelled!=null&&cancelled.getAsBoolean())throw new CancelledException();}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder(64);for(byte value:bytes)out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
    private static void verify(File file,long bytes,String expected,BooleanSupplier cancelled)throws IOException{
        if(file.length()!=bytes)throw new IOException("Voice model size mismatch");
        MessageDigest hash=digest();byte[] buffer=new byte[65536];long count=0;
        try(InputStream in=new FileInputStream(file)){
            for(;;){check(cancelled);int n=in.read(buffer);if(n<0)break;if(n==0)throw new IOException("Empty model read");count+=n;if(count>bytes)throw new IOException("Model grew during verification");hash.update(buffer,0,n);}
        }
        check(cancelled);if(count!=bytes||!expected.equals(hex(hash.digest())))throw new IOException("Voice model checksum mismatch");
    }
    private static void readExactly(InputStream in,byte[] buffer,int length,BooleanSupplier cancelled)throws IOException{
        int count=0;while(count<length){check(cancelled);int n=in.read(buffer,count,length-count);if(n<=0)throw new EOFException("Truncated voice archive");count+=n;}
    }
    private static boolean zero(byte[] block,int start,int end){for(int i=start;i<end;i++)if(block[i]!=0)return false;return true;}
    private static String field(byte[] header,int offset,int width)throws IOException{
        int end=offset;while(end<offset+width&&header[end]!=0){if(header[end]<32||header[end]>126)throw new IOException("Non-ASCII archive metadata");end++;}
        if(!zero(header,end,offset+width))throw new IOException("Invalid archive metadata padding");
        return new String(header,offset,end-offset,StandardCharsets.US_ASCII);
    }
    private static long octal(byte[] header,int offset,int width)throws IOException{
        long value=0;boolean digits=false,ended=false;
        for(int i=offset;i<offset+width;i++){
            int ch=header[i]&255;
            if(ch==0||ch==' '){if(digits)ended=true;continue;}
            if(ended||ch<'0'||ch>'7')throw new IOException("Invalid archive number");
            digits=true;if(value>(Long.MAX_VALUE-7)/8)throw new IOException("Archive number overflow");value=value*8+ch-'0';
        }
        return value;
    }
    private static void requireArchiveParent(String name,Set<String> dirsSeen)throws IOException{
        int slash=name.lastIndexOf('/');
        if(slash>=0&&!dirsSeen.contains(name.substring(0,slash)))throw new IOException("Missing archive parent");
    }
    /** Minimal strict ustar: exact file and derived directory allowlists; no links/extensions. */
    static void extractTar(InputStream in,File directory,VoiceModelCatalog.Model model,BooleanSupplier cancelled)throws IOException{
        Set<String> seen=new HashSet<>(),dirsSeen=new HashSet<>();byte[] header=new byte[512],buffer=new byte[65536];long consumed=0;boolean rootSeen=false;
        for(;;){
            check(cancelled);readExactly(in,header,512,cancelled);consumed+=512;
            if(consumed>model.expandedBytes+1024*1024)throw new IOException("Voice archive expansion limit");
            if(zero(header,0,512)){
                readExactly(in,header,512,cancelled);consumed+=512;if(!zero(header,0,512))throw new IOException("Invalid tar terminator");
                for(;;){check(cancelled);int n=in.read(buffer);if(n<0)break;if(n==0)throw new IOException("Empty tar read");consumed+=n;
                    if(consumed>model.expandedBytes+1024*1024||!zero(buffer,0,n))throw new IOException("Unexpected tar trailer");}
                break;
            }
            long sum=0;for(int i=0;i<512;i++)sum+=i>=148&&i<156?32:header[i]&255;
            if(sum!=octal(header,148,8))throw new IOException("Tar header checksum mismatch");
            if(!"ustar".equals(new String(header,257,5,StandardCharsets.US_ASCII)))throw new IOException("Unsupported tar format");
            if(!field(header,157,100).isEmpty()||!field(header,345,155).isEmpty())throw new IOException("Tar links or prefixes are forbidden");
            String path=field(header,0,100);long size=octal(header,124,12);int type=header[156]&255;
            String prefix=model.archivePrefix+"/";
            if(type=='5'&&path.equals(prefix)){
                if(rootSeen||size!=0)throw new IOException("Unexpected archive root");
                rootSeen=true;continue;
            }
            if(!rootSeen||!path.startsWith(prefix))throw new IOException("Archive path outside model or before root");
            String name=path.substring(prefix.length());
            if(type=='5'){
                if(size!=0||!name.endsWith("/"))throw new IOException("Invalid archive directory");
                name=name.substring(0,name.length()-1);
                if(!model.directories.contains(name)||!dirsSeen.add(name))throw new IOException("Unexpected archive directory");
                requireArchiveParent(name,dirsSeen);
                File child=new File(directory,name);
                if(!child.getCanonicalFile().equals(child))throw new IOException("Linked archive directory");
                Files.createDirectory(child.toPath());
                continue;
            }
            if(type!=0&&type!='0')throw new IOException("Unsupported archive entry type");
            VoiceModelCatalog.Entry entry=model.entries.get(name);
            if(entry==null||!seen.add(name)||size!=entry.bytes)throw new IOException("Unexpected voice model archive entry");
            requireArchiveParent(name,dirsSeen);
            File output=new File(directory,name);
            if(!output.getCanonicalFile().equals(output)||Files.exists(output.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Archive target already exists");
            MessageDigest hash=digest();long remaining=size;
            try(FileOutputStream out=new FileOutputStream(output)){
                while(remaining>0){check(cancelled);int take=(int)Math.min(remaining,buffer.length);readExactly(in,buffer,take,cancelled);out.write(buffer,0,take);hash.update(buffer,0,take);remaining-=take;consumed+=take;}
                out.getFD().sync();
            }
            if(!entry.sha256.equals(hex(hash.digest())))throw new IOException("Extracted voice model checksum mismatch");
            int padding=(int)((512-size%512)%512);if(padding>0){readExactly(in,header,padding,cancelled);consumed+=padding;if(!zero(header,0,padding))throw new IOException("Invalid tar payload padding");}
        }
        if(!rootSeen||seen.size()!=model.entries.size()||!dirsSeen.equals(model.directories))throw new IOException("Incomplete voice model archive");
    }
}
