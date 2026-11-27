package com.ollamapocket.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class VisionModelStoreTest {
    private static int checks;
    private interface Action {void run()throws Exception;}
    private static void ok(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static void fails(Class<? extends Throwable> type,Action action)throws Exception{
        checks++;
        try{action.run();}catch(Throwable e){if(type.isInstance(e))return;throw new AssertionError(e);}
        throw new AssertionError("Expected "+type.getSimpleName());
    }
    private static void clean(Path path)throws IOException{
        try(java.util.stream.Stream<Path> walk=Files.walk(path)){
            for(Path p:walk.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);
        }
    }
    private static void noStage(Path root)throws IOException{
        Path models=root.resolve("vision-models");
        if(!Files.exists(models))return;
        try(DirectoryStream<Path> files=Files.newDirectoryStream(models)){
            for(Path p:files)ok(!p.getFileName().toString().startsWith(".install-"),"No abandoned staging");
        }
    }
    private static void basics(Path root)throws Exception{
        VoiceModelCatalog.Model pair=VisionModelStore.PAIR;
        ok(pair.format==VoiceModelCatalog.Format.FILES&&pair.entries.size()==2,"Exact flat pair");
        ok(pair.archiveBytes==VisionModelProfile.TOTAL_BYTES&&pair.expandedBytes==pair.archiveBytes,"Exact pair bytes");
        ok(!VoiceModelCatalog.ALL.contains(pair),"Not a voice settings model");
        AtomicInteger opens=new AtomicInteger();
        VisionModelStore s=new VisionModelStore(root.toFile(),url->{opens.incrementAndGet();return new ByteArrayInputStream(new byte[]{'G','G','U','F'});});
        ok(!s.ready()&&!s.present()&&!Files.exists(root.resolve("vision-models")),"No constructor write");
        fails(IllegalArgumentException.class,()->s.install(false,()->false,null));
        ok(opens.get()==0&&!Files.exists(root.resolve("vision-models")),"No unapproved download");
        fails(VoiceModelStore.CancelledException.class,()->s.install(true,()->true,null));
        ok(opens.get()==0,"Pre-cancelled download");
        fails(IOException.class,()->s.install(true,()->false,null));
        ok(opens.get()==1&&!s.ready()&&!s.present(),"Short asset not installed");
        noStage(root);
        fails(IOException.class,()->s.acquire(()->false));
        Path external=Files.createDirectory(root.resolve("outside"));
        Path alias=root.resolve("alias");Files.createSymbolicLink(alias,external);
        fails(IOException.class,()->new VisionModelStore(alias.toFile()));
        Files.delete(alias);
        Files.delete(root.resolve("vision-models"));
        Files.createSymbolicLink(root.resolve("vision-models"),external);
        fails(IOException.class,()->new VisionModelStore(root.toFile()));
        Files.delete(root.resolve("vision-models"));
    }
    private static void real(Path root,Path fixture)throws Exception{
        VoiceModelCatalog.Model pair=VisionModelStore.PAIR;
        List<String> requested=new ArrayList<>();
        VoiceModelStore.Source source=url->{
            requested.add(url);
            if(url.equals(pair.fileUrl(pair.entries.get(VisionModelProfile.WEIGHTS.name))))
                return Files.newInputStream(fixture.resolve(VisionModelProfile.WEIGHTS.name));
            if(url.equals(pair.fileUrl(pair.entries.get(VisionModelProfile.PROJECTOR.name))))
                return Files.newInputStream(fixture.resolve(VisionModelProfile.PROJECTOR.name));
            throw new IOException("Unexpected asset URL");
        };
        VisionModelStore s=new VisionModelStore(root.toFile(),source);
        AtomicBoolean cancel=new AtomicBoolean();
        fails(VoiceModelStore.CancelledException.class,()->s.install(true,cancel::get,
                (done,total)->{if(done>=VisionModelProfile.WEIGHTS.bytes)cancel.set(true);}));
        ok(requested.size()==1&&!s.present()&&!s.ready(),"Cancelled pair never visible");
        noStage(root);
        requested.clear();
        AtomicLong progress=new AtomicLong();
        s.install(true,()->false,(done,total)->{
            if(total!=VisionModelProfile.TOTAL_BYTES||done<=progress.get()||done>total)
                throw new AssertionError("Invalid aggregate progress");
            progress.set(done);
        });
        ok(progress.get()==VisionModelProfile.TOTAL_BYTES&&requested.size()==2,"Both pinned assets installed");
        ok(s.present()&&s.ready(),"Installed metadata");
        VisionModelStore other=new VisionModelStore(root.toFile());
        Path weights;
        try(VisionModelStore.Lease lease=s.acquire(()->false)){
            weights=lease.weights().toPath();
            ok(lease.weights().length()==VisionModelProfile.WEIGHTS.bytes&&
                    lease.projector().length()==VisionModelProfile.PROJECTOR.bytes,"Exact leased pair");
            fails(VoiceModelStore.BusyException.class,other::remove);
            fails(IOException.class,()->other.install(true,()->false,null));
        }
        try(VisionModelStore.Lease lease=s.acquire(()->false)){
            lease.close();fails(IOException.class,lease::weights);
        }
        Path extra=weights.resolveSibling("unlisted.gguf");
        Files.write(extra,new byte[]{1});
        ok(!s.ready(),"Unlisted file invalidates tree");
        fails(IOException.class,()->s.acquire(()->false));
        Files.delete(extra);
        try(RandomAccessFile file=new RandomAccessFile(weights.toFile(),"rw")){
            file.seek(0);file.write('X');
        }
        ok(s.ready(),"Metadata is not a digest guarantee");
        fails(IOException.class,()->s.acquire(()->false));
        other.remove();
        ok(!s.present()&&!s.ready(),"Pair removed after failed lease");
        noStage(root);
        System.out.println("PASS: actual pinned VLM weights and projector installed, leased, corruption rejected, removed");
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("vision-store-").toRealPath();
        try{
            basics(root);
            if(args.length==1)real(root,Path.of(args[0]).toRealPath());
            else if(args.length!=0)throw new IllegalArgumentException("Fixture arguments");
        }finally{clean(root);}
        System.out.println("PASS: "+checks+" verified vision model pair storage and lifecycle checks");
    }
}
