package com.ollamapocket.app;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

/** Real filesystem, digest, archive and cancellation tests; no network or Android dependency. */
public final class VoiceModelStoreTest {
    private static int checks;
    private static final byte[] DATA=new byte[140123];
    private static final byte[] SMALL="offline model fixture\n".getBytes(StandardCharsets.UTF_8);
    private static final String PREFIX="voice-fixture";
    private interface Action { void run()throws Exception; }
    private static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void fails(Class<? extends Throwable> expected,Action action,String message)throws Exception{
        checks++;try{action.run();}catch(Throwable error){if(expected.isInstance(error))return;throw new AssertionError(message+": "+error,error);}throw new AssertionError(message+": accepted");
    }
    private static String hash(byte[] data)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static VoiceModelCatalog.Model raw(String id,byte[] data)throws Exception{
        return new VoiceModelCatalog.Model(id,"Test voice","https://huggingface.co/test/model",data.length,hash(data),VoiceModelCatalog.Format.RAW,null,"MIT","https://github.com/test/license","en",new VoiceModelCatalog.Entry("model.bin",data.length,hash(data)));
    }
    private static VoiceModelCatalog.Model archive(byte[] compressed)throws Exception{
        return new VoiceModelCatalog.Model("tar-fixture","Test archive","https://github.com/test/model",compressed.length,hash(compressed),VoiceModelCatalog.Format.TAR_BZ2,PREFIX,"MIT","https://github.com/test/license","en",new VoiceModelCatalog.Entry("model.bin",SMALL.length,hash(SMALL)));
    }
    private static File root(Path sandbox,String name){return sandbox.resolve(name).toFile();}
    private static VoiceModelStore bytes(File root,byte[] data){return new VoiceModelStore(root,url->new ByteArrayInputStream(data));}
    private static void clean(File root)throws Exception{if(!root.exists())return;try(var paths=Files.walk(root.toPath())){for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(path);}}
    private static void noStage(File root){File[] files=root.listFiles();if(files!=null)for(File file:files)ok(!file.getName().startsWith(".install-"),"Staging directory leaked");}
    private static void rawTests(Path sandbox)throws Exception{
        VoiceModelCatalog.Model model=raw("raw-fixture",DATA);File root=root(sandbox,"raw");VoiceModelStore store=bytes(root,DATA);
        ok(!root.exists(),"Constructor wrote files");ok(!store.ready(model),"Missing model ready");
        fails(IOException.class,()->store.acquire(model,()->false),"Missing model acquired");
        AtomicLong last=new AtomicLong();store.install(model,()->false,(done,total)->{ok(done>last.get()&&done<=total,"Progress bound");last.set(done);});
        ok(last.get()==DATA.length,"Progress ended early");ok(store.ready(model),"Installed model not ready");noStage(root);
        VoiceModelStore second=new VoiceModelStore(root);VoiceModelStore.Lease lease=store.acquire(model,()->false),another=second.acquire(model,()->false);
        ok(Arrays.equals(DATA,Files.readAllBytes(lease.file("model.bin").toPath())),"Model bytes changed");
        fails(IOException.class,()->lease.file("../escape"),"Lease path traversal");
        fails(VoiceModelStore.BusyException.class,()->second.remove(model),"Deleted leased model");
        fails(VoiceModelStore.BusyException.class,()->second.install(model,()->false,null),"Replaced leased model");
        lease.close();lease.close();fails(IOException.class,()->lease.file("model.bin"),"Closed lease returned file");
        fails(VoiceModelStore.BusyException.class,()->second.remove(model),"Second lease unprotected");another.close();
        byte[] corrupt=DATA.clone();corrupt[99]^=32;Path installed=root.toPath().resolve(model.id).resolve("model.bin");Files.write(installed,corrupt);
        ok(store.ready(model),"Metadata check unexpectedly hashes large data");
        fails(IOException.class,()->store.acquire(model,()->false),"Same-size corrupt data acquired");
        store.remove(model);ok(!store.ready(model),"Removed model still ready");
        store.install(model,()->false,null);
        AtomicInteger calls=new AtomicInteger();fails(VoiceModelStore.CancelledException.class,()->store.acquire(model,()->calls.incrementAndGet()>2),"Acquire cancellation ignored");
        store.remove(model);ok(!store.present(model),"Failed acquire leaked lease");
        for(String failure:new String[]{"short","long","digest","transport","empty"}){
            byte[] input=failure.equals("short")?Arrays.copyOf(DATA,DATA.length-1):failure.equals("long")?Arrays.copyOf(DATA,DATA.length+1):failure.equals("digest")?corrupt:DATA;
            VoiceModelStore broken=failure.equals("transport")?new VoiceModelStore(root,url->{throw new IOException("offline");}):failure.equals("empty")?new VoiceModelStore(root,url->new InputStream(){public int read(){return 0;}public int read(byte[] b){return 0;}}):bytes(root,input);
            fails(IOException.class,()->broken.install(model,()->false,null),failure+" installed");
            ok(!broken.ready(model)&&!broken.present(model),failure+" activated");noStage(root);
        }
        AtomicBoolean cancelled=new AtomicBoolean();fails(VoiceModelStore.CancelledException.class,()->store.install(model,cancelled::get,(done,total)->cancelled.set(true)),"Mid-download cancellation ignored");
        ok(!store.ready(model)&&!store.present(model),"Cancelled download activated");noStage(root);
        AtomicBoolean requested=new AtomicBoolean();VoiceModelStore early=new VoiceModelStore(root,url->{requested.set(true);return new ByteArrayInputStream(DATA);});
        fails(VoiceModelStore.CancelledException.class,()->early.install(model,()->true,null),"Early cancellation ignored");ok(!requested.get(),"Cancelled operation opened transport");
        File destination=new File(root,model.id);VoiceModelStore race=new VoiceModelStore(root,url->{destination.mkdir();Files.writeString(new File(destination,"preserved").toPath(),"original");return new ByteArrayInputStream(DATA);});
        fails(IOException.class,()->race.install(model,()->false,null),"Destination race overwritten");
        ok(Files.readString(new File(destination,"preserved").toPath()).equals("original"),"Existing destination overwritten");noStage(root);race.remove(model);
        store.install(model,()->false,null);Files.writeString(new File(destination,".installed").toPath(),"incomplete");ok(!store.ready(model),"Wrong marker ready");
        fails(IOException.class,()->store.acquire(model,()->false),"Wrong marker acquired");store.remove(model);
        store.install(model,()->false,null);Files.delete(installed);Files.createSymbolicLink(installed,sandbox.resolve("external.bin"));Files.write(sandbox.resolve("external.bin"),DATA);
        ok(!store.ready(model),"Linked model ready");fails(IOException.class,()->store.acquire(model,()->false),"Linked file acquired");store.remove(model);ok(Files.exists(sandbox.resolve("external.bin")),"Delete followed link");
        Path linked=sandbox.resolve("linked-root");Files.createSymbolicLink(linked,root.toPath());VoiceModelStore linkStore=bytes(linked.toFile(),DATA);
        ok(!linkStore.ready(model),"Linked root ready");fails(IOException.class,()->linkStore.install(model,()->false,null),"Linked root installed");
    }
    private static void concurrency(Path sandbox)throws Exception{
        File root=root(sandbox,"concurrent");VoiceModelCatalog.Model model=raw("concurrent",DATA);CountDownLatch opened=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean closed=new AtomicBoolean();
        VoiceModelStore store=new VoiceModelStore(root,url->new InputStream(){
            boolean first=true;public int read()throws IOException{throw new IOException("unused");}
            public int read(byte[] b)throws IOException{if(first){first=false;opened.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("test timeout");}catch(InterruptedException e){throw new IOException(e);}}return -1;}
            public void close(){closed.set(true);release.countDown();}
        });
        ExecutorService worker=Executors.newSingleThreadExecutor();Future<?> job=worker.submit(()->{try{store.install(model,()->false,null);throw new AssertionError("Cancelled transport installed");}catch(IOException expected){}});
        try{
            ok(opened.await(5,TimeUnit.SECONDS),"Download did not start");VoiceModelStore other=new VoiceModelStore(root);
            ok(!other.ready(model),"In-progress model ready");fails(VoiceModelStore.BusyException.class,()->other.remove(model),"Removed installing model");
            fails(VoiceModelStore.BusyException.class,()->other.acquire(model,()->false),"Acquired installing model");
            fails(VoiceModelStore.BusyException.class,()->other.install(model,()->false,null),"Parallel installation accepted");
            store.cancelPendingInstall();job.get(5,TimeUnit.SECONDS);ok(closed.get(),"Cancel failed to close stream");ok(!store.present(model),"Canceled in-progress install activated");noStage(root);
        }finally{release.countDown();worker.shutdownNow();}
    }
    private static void put(byte[] bytes,int at,String text){byte[] data=text.getBytes(StandardCharsets.US_ASCII);System.arraycopy(data,0,bytes,at,data.length);}
    private static void checksum(byte[] header){Arrays.fill(header,148,156,(byte)' ');long sum=0;for(byte b:header)sum+=b&255;put(header,148,String.format(Locale.ROOT,"%06o",sum));header[154]=0;header[155]=' ';}
    private static byte[] header(String name,long size,int type){byte[] h=new byte[512];put(h,0,name);put(h,100,"0000644");put(h,108,"0000000");put(h,116,"0000000");put(h,124,String.format(Locale.ROOT,"%011o",size));put(h,136,"00000000000");h[156]=(byte)type;put(h,257,"ustar");put(h,263,"00");checksum(h);return h;}
    private static byte[] tar(String path,int type,byte[] contents,boolean root,boolean duplicate,boolean ending)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();if(root)out.write(header(PREFIX+"/",0,'5'));byte[] h=header(path,contents.length,type);
        out.write(h);out.write(contents);out.write(new byte[(512-contents.length%512)%512]);
        if(duplicate){out.write(h);out.write(contents);out.write(new byte[(512-contents.length%512)%512]);}
        if(ending)out.write(new byte[1024]);return out.toByteArray();
    }
    private static byte[] compress(byte[] bytes)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(BZip2CompressorOutputStream compressed=new BZip2CompressorOutputStream(out)){compressed.write(bytes);}return out.toByteArray();}
    private static void archiveTests(Path sandbox)throws Exception{
        byte[] validTar=tar(PREFIX+"/model.bin",'0',SMALL,true,false,true),valid=compress(validTar);VoiceModelCatalog.Model model=archive(valid);File root=root(sandbox,"archive");VoiceModelStore store=bytes(root,valid);
        store.install(model,()->false,null);try(VoiceModelStore.Lease lease=store.acquire(model,()->false)){ok(Arrays.equals(SMALL,Files.readAllBytes(lease.file("model.bin").toPath())),"Tar data changed");}store.remove(model);
        List<byte[]> bad=new ArrayList<>();
        bad.add(tar("../outside",'0',SMALL,true,false,true));bad.add(tar("/tmp/outside",'0',SMALL,true,false,true));
        bad.add(tar(PREFIX+"/../escape",'0',SMALL,true,false,true));bad.add(tar(PREFIX+"/unlisted",'0',SMALL,true,false,true));
        bad.add(tar(PREFIX+"/model.bin",'0',SMALL,true,true,true));bad.add(tar(PREFIX+"/model.bin",'0',SMALL,false,false,true));
        bad.add(tar(PREFIX+"/model.bin",'0',SMALL,true,false,false));
        for(int type:new int[]{'1','2','3','4','5','6','7','x','g','L','K'})bad.add(tar(PREFIX+"/model.bin",type,SMALL,true,false,true));
        byte[] corrupt=validTar.clone();corrupt[512+1]^=1;bad.add(corrupt);
        byte[] padding=validTar.clone();padding[1024+SMALL.length]=1;bad.add(padding);
        byte[] wrongContent=validTar.clone();wrongContent[1024]^=1;bad.add(wrongContent);
        byte[] trailer=Arrays.copyOf(validTar,validTar.length+1);trailer[trailer.length-1]=1;bad.add(trailer);
        byte[] symlink=validTar.clone();put(symlink,512+157,"target");byte[] fileHeader=Arrays.copyOfRange(symlink,512,1024);checksum(fileHeader);System.arraycopy(fileHeader,0,symlink,512,512);bad.add(symlink);
        byte[] huge=validTar.clone();put(huge,512+124,"77777777777");fileHeader=Arrays.copyOfRange(huge,512,1024);checksum(fileHeader);System.arraycopy(fileHeader,0,huge,512,512);bad.add(huge);
        byte[] prefix=validTar.clone();put(prefix,512+345,"outside");fileHeader=Arrays.copyOfRange(prefix,512,1024);checksum(fileHeader);System.arraycopy(fileHeader,0,prefix,512,512);bad.add(prefix);
        byte[] secondTerm=validTar.clone();secondTerm[secondTerm.length-1]=1;bad.add(secondTerm);
        for(int i=0;i<bad.size();i++){
            byte[] compressed=compress(bad.get(i));VoiceModelCatalog.Model invalid=archive(compressed);VoiceModelStore test=bytes(root,compressed);final int index=i;
            fails(IOException.class,()->test.install(invalid,()->false,null),"Unsafe archive "+index+" installed");ok(!test.present(invalid),"Unsafe archive activated");noStage(root);
        }
        byte[] shortBzip=Arrays.copyOf(valid,valid.length-6);VoiceModelCatalog.Model truncated=archive(shortBzip);
        fails(IOException.class,()->bytes(root,shortBzip).install(truncated,()->false,null),"Truncated bzip accepted");noStage(root);
        File extraction=Files.createTempDirectory(sandbox,"extract-cancel-").toFile();AtomicInteger ticks=new AtomicInteger();
        fails(VoiceModelStore.CancelledException.class,()->VoiceModelStore.extractTar(new ByteArrayInputStream(validTar),extraction,model,()->ticks.incrementAndGet()>5),"Tar cancellation ignored");
    }
    private static VoiceModelCatalog.Model nestedModel(byte[] compressed)throws Exception{
        return new VoiceModelCatalog.Model("nested-fixture","Dictionary fixture","https://github.com/test/model",compressed.length,hash(compressed),
            VoiceModelCatalog.Format.TAR_BZ2,PREFIX,"MIT","https://github.com/test/license","zh",
            new VoiceModelCatalog.Entry("dict/sub/words.txt",SMALL.length,hash(SMALL)),
            new VoiceModelCatalog.Entry("model.bin",SMALL.length,hash(SMALL)));
    }
    private static byte[] nestedTar(String... dirs)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(header(PREFIX+"/",0,'5'));
        for(String dir:dirs)out.write(header(PREFIX+"/"+dir,0,'5'));
        for(String name:new String[]{"dict/sub/words.txt","model.bin"}){
            out.write(header(PREFIX+"/"+name,SMALL.length,'0'));
            out.write(SMALL);out.write(new byte[(512-SMALL.length%512)%512]);
        }
        out.write(new byte[1024]);return out.toByteArray();
    }
    private static void nestedTests(Path sandbox)throws Exception{
        for(String bad:new String[]{"../escape","dict/../escape","/absolute","dict//words","dict/.hidden","dict/","dict\\\\words","a/b/c/d","dict/./words"}){
            fails(IllegalArgumentException.class,()->new VoiceModelCatalog.Entry(bad,SMALL.length,hash(SMALL)),"Unsafe catalog path "+bad);
        }
        byte[] valid=compress(nestedTar("dict/","dict/sub/"));
        VoiceModelCatalog.Model model=nestedModel(valid);
        ok(model.directories.equals(Set.of("dict","dict/sub")),"Derived directory allowlist");
        File root=root(sandbox,"nested");VoiceModelStore store=bytes(root,valid);
        store.install(model,()->false,null);
        try(VoiceModelStore.Lease lease=store.acquire(model,()->false)){
            ok(Arrays.equals(SMALL,Files.readAllBytes(lease.file("dict/sub/words.txt").toPath())),"Nested file bytes");
            fails(IOException.class,()->lease.file("dict/sub"),"Directory acquired as file");
            fails(VoiceModelStore.BusyException.class,()->store.remove(model),"Nested lease not protected");
        }
        Path words=root.toPath().resolve(model.id).resolve("dict/sub/words.txt");
        Path injected=words.getParent().resolve("unknown.txt");
        Files.writeString(injected,"unlisted");
        ok(!store.ready(model),"Unexpected installed dictionary file ready");
        fails(IOException.class,()->store.acquire(model,()->false),"Unexpected installed dictionary file leased");
        Files.delete(injected);
        Files.createDirectory(injected);
        ok(!store.ready(model),"Unexpected installed directory ready");
        fails(IOException.class,()->store.acquire(model,()->false),"Unexpected installed directory leased");
        Files.delete(injected);
        Files.createSymbolicLink(injected,words);
        ok(!store.ready(model),"Unexpected installed symlink ready");
        fails(IOException.class,()->store.acquire(model,()->false),"Unexpected installed symlink leased");
        Files.delete(injected);
        Files.delete(words);
        ok(!store.ready(model),"Missing installed dictionary file ready");
        fails(IOException.class,()->store.acquire(model,()->false),"Missing installed dictionary file leased");
        Files.createDirectory(words);
        ok(!store.ready(model),"Declared file replaced by directory ready");
        fails(IOException.class,()->store.acquire(model,()->false),"Declared file replaced by directory leased");
        Files.delete(words);
        Files.write(words,SMALL);
        ok(store.ready(model),"Recovered exact tree not ready");
        try(VoiceModelStore.Lease lease=store.acquire(model,()->false)){
            ok(Arrays.equals(SMALL,Files.readAllBytes(lease.file("dict/sub/words.txt").toPath())),"Recovered dictionary bytes");
        }
        byte[] changed=SMALL.clone();changed[0]^=1;Files.write(words,changed);
        fails(IOException.class,()->store.acquire(model,()->false),"Nested file corruption accepted");
        store.remove(model);ok(!store.present(model),"Nested removal failed");noStage(root);
        for(String[] bad:new String[][]{
            {"dict/sub/"},{"dict/","dict/","dict/sub/"},{"dict/","dict/sub/","extra/"},
            {"dict/","dict/sub/","dict/../"},{"dict/"},{"dict/sub/","dict/"},{"dict/","dict/sub"},
            {"dict/","dict/sub/","model.bin/"}
        }){
            byte[] compressed=compress(nestedTar(bad));VoiceModelCatalog.Model invalid=nestedModel(compressed);
            VoiceModelStore broken=bytes(root,compressed);
            fails(IOException.class,()->broken.install(invalid,()->false,null),"Invalid nested archive accepted");
            ok(!broken.present(invalid),"Invalid nested archive activated");noStage(root);
        }
        fails(IllegalArgumentException.class,()->new VoiceModelCatalog.Model("collision","Collision","https://github.com/test/model",
            valid.length,hash(valid),VoiceModelCatalog.Format.TAR_BZ2,PREFIX,"MIT","https://github.com/test/license","zh",
            new VoiceModelCatalog.Entry("dict",SMALL.length,hash(SMALL)),new VoiceModelCatalog.Entry("dict/words",SMALL.length,hash(SMALL))),"Catalog file/directory collision");
        fails(IllegalArgumentException.class,()->new VoiceModelCatalog.Model("raw-nested","Raw nested","https://github.com/test/model",
            SMALL.length,hash(SMALL),VoiceModelCatalog.Format.RAW,null,"MIT","https://github.com/test/license","zh",
            new VoiceModelCatalog.Entry("dict/words",SMALL.length,hash(SMALL))),"Nested RAW accepted");
        File extraction=Files.createTempDirectory(sandbox,"nested-link-").toFile();
        Files.createSymbolicLink(extraction.toPath().resolve("dict"),sandbox);
        fails(IOException.class,()->VoiceModelStore.extractTar(new ByteArrayInputStream(nestedTar("dict/","dict/sub/")),
            extraction,model,()->false),"Linked nested parent accepted");
    }
    private static void urls()throws Exception{
        for(String good:new String[]{"https://huggingface.co/ggerganov/model","https://cas-bridge.xethub.hf.co/x?signature=test","https://cdn-lfs-us-1.huggingface.co/model","https://github.com/k2-fsa/release","https://release-assets.githubusercontent.com/x","https://objects.githubusercontent.com/x"}){
            VoiceModelStore.validateDownloadUrl(new URL(good));ok(true,"Allowed model URL");
        }
        for(String bad:new String[]{"http://huggingface.co/x","https://huggingface.co.evil.test/x","https://evil-hf.co/x","https://127.0.0.1/x","https://localhost/x","https://github.com:444/x","https://user:pass@github.com/x","https://github.com/x#fragment","https://evil.test/x"})
            fails(IOException.class,()->VoiceModelStore.validateDownloadUrl(new URL(bad)),"Untrusted redirect allowed");
    }
    private static void realArchive(Path sandbox,String archive)throws Exception{
        File file=new File(archive);ok(file.length()==VoiceModelCatalog.TTS.archiveBytes,"Real model archive size changed");
        File root=root(sandbox,"real-tts");VoiceModelStore store=new VoiceModelStore(root,url->new FileInputStream(file));store.install(VoiceModelCatalog.TTS,()->false,null);
        ok(store.ready(VoiceModelCatalog.TTS),"Real TTS model failed installation");
        try(VoiceModelStore.Lease lease=store.acquire(VoiceModelCatalog.TTS,()->false)){for(VoiceModelCatalog.Entry e:VoiceModelCatalog.TTS.entries.values())ok(lease.file(e.name).length()==e.bytes,"Real file mismatch");}
        store.remove(VoiceModelCatalog.TTS);ok(!store.present(VoiceModelCatalog.TTS),"Real archive cleanup");
    }
    public static void main(String[] args)throws Exception{
        new Random(48).nextBytes(DATA);Path sandbox=Files.createTempDirectory("onhand-voice-model-test-");
        try{rawTests(sandbox);concurrency(sandbox);archiveTests(sandbox);nestedTests(sandbox);urls();if(args.length==1)realArchive(sandbox,args[0]);}
        finally{clean(sandbox.toFile());}
        System.out.println("PASS: "+checks+" voice model storage, archive, integrity and cancellation checks");
    }
}
