package com.ollamapocket.app;

import java.io.File;
import java.nio.file.Files;
public final class BenchmarkProfileTest {
    private static int n;
    static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    static void bad(Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException expected){rejected=true;}ok(rejected);}
    static BenchmarkProfile.Measurement m(double speed,double first,double decay,long free,int start,int end){
        return new BenchmarkProfile.Measurement(speed,first,decay,free,start,end);
    }
    public static void main(String[] args)throws Exception{
        String digest="a".repeat(64),runtime="b".repeat(64),model="example:latest";
        long now=1_000_000_000L;
        BenchmarkProfile.Measurement cpu=m(10,500,2,1024,0,1);
        BenchmarkProfile.Measurement gpu=m(20,600,4,512,0,1);
        BenchmarkProfile p=new BenchmarkProfile(model,"sha256:"+digest,runtime,now,cpu,gpu);
        ok("gpu".equals(p.preferredLocalMode()));
        ok(p.fresh(model,digest,runtime,now+BenchmarkProfile.MAX_AGE_MS));
        ok(!p.fresh(model,digest,runtime,now+BenchmarkProfile.MAX_AGE_MS+1));
        ok(!p.fresh(model,digest,runtime,now-1));
        ok(!p.fresh("other",digest,runtime,now));
        ok(!p.fresh(model,"c".repeat(64),runtime,now));
        ok(!p.fresh(model,digest,"d".repeat(64),now));
        ok(BenchmarkProfile.normalizeDigest("SHA256:"+"A".repeat(64)).equals(digest));
        ok(BenchmarkProfile.normalizeDigest("invalid").isEmpty());
        String raw=p.encode();
        BenchmarkProfile decoded=BenchmarkProfile.read(raw,model,digest,runtime,now);
        ok(decoded!=null&&decoded.cpuRate()==10&&decoded.gpuRate()==20);
        ok(BenchmarkProfile.read(raw,model,"c".repeat(64),runtime,now)==null);
        ok(BenchmarkProfile.read(raw,model,digest,runtime,now+BenchmarkProfile.MAX_AGE_MS+1)==null);
        ok(BenchmarkProfile.read("broken",model,digest,runtime,now)==null);
        ok(BenchmarkProfile.read("x".repeat(8193),model,digest,runtime,now)==null);
        ok("cpu".equals(new BenchmarkProfile(model,digest,runtime,now,cpu,m(10.9,400,0,512,0,1)).preferredLocalMode()));
        ok("cpu".equals(new BenchmarkProfile(model,digest,runtime,now,cpu,m(25,2000,0,512,0,1)).preferredLocalMode()));
        ok("cpu".equals(new BenchmarkProfile(model,digest,runtime,now,cpu,m(25,500,21,512,0,1)).preferredLocalMode()));
        ok("cpu".equals(new BenchmarkProfile(model,digest,runtime,now,cpu,m(25,500,0,255,0,1)).preferredLocalMode()));
        ok(new BenchmarkProfile(model,digest,runtime,now,cpu,m(25,500,0,512,-1,-1)).preferredLocalMode()==null);
        ok(new BenchmarkProfile(model,digest,runtime,now,m(10,500,0,128,0,1),gpu).preferredLocalMode()==null);
        ok(new BenchmarkProfile(model,digest,runtime,now,cpu,null).preferredLocalMode()==null);
        ok(BenchmarkProfile.read(new BenchmarkProfile(model,digest,runtime,now,null,gpu).encode(),model,digest,runtime,now).cpuRate()==null);
        bad(()->m(Double.NaN,0,0,512,0,0));
        bad(()->m(10,-1,0,512,0,0));
        bad(()->m(10,1,101,512,0,0));
        bad(()->new BenchmarkProfile(model,digest,runtime,now,null,null));
        ok(BenchmarkProfile.adequateTokens(58,72));
        ok(!BenchmarkProfile.adequateTokens(57,72));
        ok(!BenchmarkProfile.adequateTokens(73,72));
        ok(!BenchmarkProfile.adequateTokens(0,0));
        ok(EngineLoadPolicy.gpuStartupMs(0)==35000);
        ok(EngineLoadPolicy.gpuStartupMs(1024L*1024*1024)==67000);
        ok(EngineLoadPolicy.gpuStartupMs(Long.MAX_VALUE)==180000);
        ok(EngineLoadPolicy.hostThreads(1)==1&&EngineLoadPolicy.hostThreads(4)==2&&EngineLoadPolicy.hostThreads(12)==4);
        File f=File.createTempFile("weight-policy",".gguf");
        try{
            Files.write(f.toPath(),new byte[]{1,2});
            String first=EngineLoadPolicy.weightKey(f);
            Files.write(f.toPath(),new byte[]{1,2,3});
            ok(!first.equals(EngineLoadPolicy.weightKey(f)));
        }finally{Files.deleteIfExists(f.toPath());}
        System.out.println("PASS: "+n+" benchmark scope and engine load policy assertions");
    }
}
