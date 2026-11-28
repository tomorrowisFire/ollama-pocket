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
        ok(p.recommendableCpuRate()==10&&p.recommendableGpuRate()==20);
        BenchmarkProfile unstable=new BenchmarkProfile(model,digest,runtime,now,m(99,100,21,1024,0,1),m(100,100,0,255,0,1));
        ok(unstable.cpuRate()==99&&unstable.gpuRate()==100);
        ok(unstable.recommendableCpuRate()==null&&unstable.recommendableGpuRate()==null);
        BenchmarkProfile mixed=new BenchmarkProfile(model,digest,runtime,now,cpu,m(50,100,0,1024,-1,-1));
        ok(mixed.recommendableCpuRate()==10&&mixed.recommendableGpuRate()==null);
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
        java.util.concurrent.atomic.AtomicInteger rtCalls=new java.util.concurrent.atomic.AtomicInteger(),digestCalls=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Supplier<String> rt=()->{rtCalls.incrementAndGet();return runtime;};
        java.util.function.Supplier<String> live=()->{digestCalls.incrementAndGet();return digest;};
        for(String missing:new String[]{null,"","broken","x".repeat(8193)})
            ok(BenchmarkProfile.measuredRoute(missing,model,rt,live,()->now)==null);
        ok(BenchmarkProfile.measuredRoute(raw,"other",rt,live,()->now)==null);
        ok(BenchmarkProfile.measuredRoute(raw,model,rt,live,()->now-1)==null);
        ok(BenchmarkProfile.measuredRoute(raw,model,rt,live,()->now+BenchmarkProfile.MAX_AGE_MS+1)==null);
        String incomplete=new BenchmarkProfile(model,digest,runtime,now,cpu,null).encode();
        ok(BenchmarkProfile.measuredRoute(incomplete,model,rt,live,()->now)==null);
        ok(rtCalls.get()==0&&digestCalls.get()==0);
        ok("gpu".equals(BenchmarkProfile.measuredRoute(raw,model,rt,live,()->now)));
        ok(rtCalls.get()==1&&digestCalls.get()==1);
        ok(BenchmarkProfile.measuredRoute(raw,model,()->"c".repeat(64),live,()->now)==null&&digestCalls.get()==1);
        ok(BenchmarkProfile.measuredRoute(raw,model,rt,()->"c".repeat(64),()->now)==null);
        ok(BenchmarkProfile.measuredRoute(raw,model,rt,()->"",()->now)==null);
        java.util.concurrent.atomic.AtomicInteger clock=new java.util.concurrent.atomic.AtomicInteger();
        ok(BenchmarkProfile.measuredRoute(raw,model,rt,live,()->clock.getAndIncrement()==0?now:now+BenchmarkProfile.MAX_AGE_MS+1)==null);
        ok(digestCalls.get()==2);
        ok(BenchmarkProfile.measuredRoute(raw,model,rt,()->{throw new IllegalStateException();},()->now)==null);
        String cpuRoute=new BenchmarkProfile(model,digest,runtime,now,cpu,m(10.5,400,0,512,0,1)).encode();
        ok("cpu".equals(BenchmarkProfile.measuredRoute(cpuRoute,model,rt,live,()->now)));
        ok(digestCalls.get()==3);
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
        ok(BenchmarkProfile.recommendableResources(256,0,2,20));
        ok(!BenchmarkProfile.recommendableResources(255,0,2,20));
        ok(!BenchmarkProfile.recommendableResources(1024,-1,2,1));
        ok(!BenchmarkProfile.recommendableResources(1024,1,3,1));
        ok(!BenchmarkProfile.recommendableResources(1024,1,1,20.001));
        ok(!BenchmarkProfile.recommendableResources(1024,1,1,Double.NaN));
        ok(!BenchmarkProfile.recommendableResources(1024,1,1,-1));
        ok(!BenchmarkProfile.recommendableResources(Long.MAX_VALUE,1,1,0));
        ok(EngineLoadPolicy.gpuStartupMs(0)==35000);
        ok(EngineLoadPolicy.gpuStartupMs(1024L*1024*1024)==67000);
        ok(EngineLoadPolicy.gpuStartupMs(Long.MAX_VALUE)==180000);
        ok(EngineLoadPolicy.hostThreads(1)==1&&EngineLoadPolicy.hostThreads(4)==2&&EngineLoadPolicy.hostThreads(12)==4);
        EngineLoadPolicy.GpuRetryBudget retry=new EngineLoadPolicy.GpuRetryBudget();
        String key="model|weights-v1",changed="model|weights-v2";
        ok(retry.remainingMs(key,now,true)==0);
        retry.failed(key,now,false);ok(retry.remainingMs(key,now,true)==0);
        retry.failed(key,now,true);
        ok(retry.remainingMs(key,now,true)==60000);
        ok(retry.remainingMs(key,now,false)==0); // manual selection and benchmarks bypass only the cooldown
        ok(retry.remainingMs(changed,now,true)==0);
        ok(retry.remainingMs(key,now+59999,true)==1);
        ok(retry.remainingMs(key,now+60000,true)==0);
        retry.failed(key,now+60000,true);ok(retry.remainingMs(key,now+60000,true)==120000);
        retry.failed(key,now+180000,true);ok(retry.remainingMs(key,now+180000,true)==240000);
        retry.failed(key,now+420000,true);ok(retry.remainingMs(key,now+420000,true)==300000);
        retry.failed(key,now+720000,true);ok(retry.remainingMs(key,now+720000,true)==300000);
        retry.failed(key,now+720001,false);ok(retry.remainingMs(key,now+720001,true)==299999);
        retry.succeeded(key);ok(retry.remainingMs(key,now+720001,true)==0);
        retry.failed(key,now+720002,true);ok(retry.remainingMs(key,now+720002,true)==60000);
        ok(retry.remainingMs(key,now+720001,true)==0); // backward clock invalidates evidence
        retry.failed(key,now,true);
        ok(retry.remainingMs(key,now+EngineLoadPolicy.GpuRetryBudget.RESET_MS,true)==0);
        retry.failed(key,now+EngineLoadPolicy.GpuRetryBudget.RESET_MS,true);
        ok(retry.remainingMs(key,now+EngineLoadPolicy.GpuRetryBudget.RESET_MS,true)==60000);
        retry=new EngineLoadPolicy.GpuRetryBudget();
        for(int i=0;i<17;i++)retry.failed("model-"+i,now,true);
        ok(retry.remainingMs("model-0",now,true)==0);
        ok(retry.remainingMs("model-16",now,true)==60000);
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
