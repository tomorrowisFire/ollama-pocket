package com.ollamapocket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable policy tests, not an inference or Android decoder result. */
public final class VisionChatRequestTest {
    private static int checks;
    private interface Action { void run()throws Exception; }
    private static void ok(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void fails(Class<? extends Throwable> type,Action action)throws Exception{
        checks++;
        try{action.run();}catch(Throwable e){if(type.isInstance(e))return;throw new AssertionError(e);}
        throw new AssertionError("Expected "+type.getSimpleName());
    }
    private static String hash(byte[] bytes)throws Exception{
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static void assetTests()throws Exception{
        VisionModelProfile.Asset w=VisionModelProfile.WEIGHTS,p=VisionModelProfile.PROJECTOR;
        ok(VisionModelProfile.TOTAL_BYTES==278824384L,"Combined model bytes");
        ok(VisionModelProfile.matches(VisionModelProfile.REVISION,w.sha256,w.bytes,p.sha256,p.bytes),"Exact pair");
        ok(!VisionModelProfile.matches("main",w.sha256,w.bytes,p.sha256,p.bytes),"Mutable revision");
        ok(!VisionModelProfile.matches(VisionModelProfile.REVISION,p.sha256,p.bytes,w.sha256,w.bytes),"Swapped pair");
        ok(!VisionModelProfile.matches(VisionModelProfile.REVISION,w.sha256,w.bytes,p.sha256,p.bytes-1),"Wrong size");
        ok(!VisionModelProfile.matches(null,null,0,null,0),"Unknown pair");
        byte[] data={'G','G','U','F',1,2,3,4};
        String sha=hash(data);
        VisionModelProfile.Asset asset=new VisionModelProfile.Asset("fixture.gguf",data.length,sha);
        for(String name:new String[]{"../fixture.gguf","/fixture.gguf","fixture.bin","dir/fixture.gguf","dir\\fixture.gguf"})
            fails(IllegalArgumentException.class,()->new VisionModelProfile.Asset(name,8,sha));
        Path temp=Files.createTempDirectory("vision-pair-").toRealPath();
        Path file=temp.resolve(asset.name);
        try {
            fails(IOException.class,()->asset.verify(temp.toFile(),()->false));
            Files.write(file,data);
            ok(asset.verify(temp.toFile(),()->false).equals(file.toFile()),"Verified asset");
            fails(InterruptedIOException.class,()->asset.verify(temp.toFile(),()->true));
            AtomicInteger polls=new AtomicInteger();
            fails(InterruptedIOException.class,()->asset.verify(temp.toFile(),()->polls.incrementAndGet()>=3));
            byte[] changed=data.clone();changed[7]^=1;Files.write(file,changed);
            fails(IOException.class,()->asset.verify(temp.toFile(),()->false));
            Files.write(file,Arrays.copyOf(data,9));
            fails(IOException.class,()->asset.verify(temp.toFile(),()->false));
            Files.write(file,new byte[data.length]);
            fails(IOException.class,()->new VisionModelProfile.Asset(asset.name,data.length,hash(new byte[data.length])).verify(temp.toFile(),()->false));
            Files.delete(file);
            Path other=temp.resolve("other.gguf");Files.write(other,data);
            Files.createSymbolicLink(file,other);
            fails(IOException.class,()->asset.verify(temp.toFile(),()->false));
        }finally{
            try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(path);}
        }
    }
    public static void main(String[] args)throws Exception{
        assetTests();
        byte[] jpeg={(byte)255,(byte)216,(byte)255,1,(byte)255,(byte)217};
        VisionImagePolicy.Prepared prepared=VisionImagePolicy.finish(jpeg,32,32);
        String body=new String(VisionChatRequest.build(prepared,"한국어 \"quote\"\\\n中文 😀",16,()->false),StandardCharsets.UTF_8);
        ok(body.contains("\"model\":\""+VisionModelProfile.ALIAS+"\""),"Exact alias");
        ok(body.contains("data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(jpeg)),"Exact private bytes");
        ok(body.contains("한국어 \\\"quote\\\"\\\\\\n中文 😀"),"Escaped Unicode");
        ok(body.contains("\"max_tokens\":16")&&body.contains("\"stream\":false"),"Bounded single turn");
        ok(!body.contains("\"tools\"")&&!body.contains("\"history\""),"No implicit tools or history");
        byte[] returned=VisionChatRequest.build(prepared,"test",1,()->false);returned[0]=0;
        ok(VisionChatRequest.build(prepared,"test",1,()->false)[0]=='{',"Fresh payload");
        for(String prompt:new String[]{null,""," \n\t","\u00a0","\u200d","bad\u0000","bad\u202e","bad\ud800","bad\udc00","a".repeat(4097)})
            fails(IllegalArgumentException.class,()->VisionChatRequest.build(prepared,prompt,1,()->false));
        for(int n:new int[]{Integer.MIN_VALUE,0,-1,257,Integer.MAX_VALUE})
            fails(IllegalArgumentException.class,()->VisionChatRequest.build(prepared,"test",n,()->false));
        fails(IllegalArgumentException.class,()->VisionChatRequest.build(null,"test",1,()->false));
        fails(InterruptedIOException.class,()->VisionChatRequest.build(prepared,"test",1,()->true));
        AtomicInteger polls=new AtomicInteger();
        fails(InterruptedIOException.class,()->VisionChatRequest.build(prepared,"test",1,()->polls.incrementAndGet()>=2));
        byte[] large=new byte[VisionImagePolicy.MAX_OUTPUT_BYTES];
        large[0]=(byte)255;large[1]=(byte)216;large[2]=(byte)255;large[large.length-2]=(byte)255;large[large.length-1]=(byte)217;
        VisionImagePolicy.Prepared max=VisionImagePolicy.finish(large,1024,1024);
        ok(VisionChatRequest.build(max,"中".repeat(4096),256,()->false).length<=VisionChatRequest.MAX_REQUEST_BYTES,"Maximum bounded request");
        System.out.println("PASS: "+checks+" vision pair and private image request checks");
    }
}
