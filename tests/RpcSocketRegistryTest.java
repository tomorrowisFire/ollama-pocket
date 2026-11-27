package com.ollamapocket.app;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

public final class RpcSocketRegistryTest {
    private static int n;
    static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static final class FaultSocket extends Socket {
        @Override public synchronized void close()throws IOException{
            super.close();throw new IOException("injected close failure");
        }
    }
    private static void pairs()throws Exception{
        try(RpcSocketRegistry registry=new RpcSocketRegistry(6)){
            Socket[] local=new Socket[3],remote=new Socket[3];
            RpcSocketRegistry.Pair[] leases=new RpcSocketRegistry.Pair[3];
            for(int i=0;i<3;i++){
                local[i]=new Socket();remote[i]=new Socket();
                leases[i]=registry.trackPair(local[i],remote[i]);
                ok(leases[i]!=null&&!local[i].isClosed()&&!remote[i].isClosed());
            }
            Socket overA=new Socket(),overB=new Socket();
            ok(registry.trackPair(overA,overB)==null&&overA.isClosed()&&overB.isClosed());
            Socket alias=new Socket();
            ok(registry.trackPair(local[0],alias)==null&&!local[0].isClosed()&&alias.isClosed());
            leases[1].close();leases[1].close();
            ok(local[1].isClosed()&&remote[1].isClosed());
            Socket same=new Socket();ok(registry.trackPair(same,same)==null&&same.isClosed());
            Socket missing=new Socket();ok(registry.trackPair(null,missing)==null&&missing.isClosed());
            Socket alreadyClosed=new Socket(),fresh=new Socket();alreadyClosed.close();
            ok(registry.trackPair(alreadyClosed,fresh)==null&&fresh.isClosed());
            for(int i=0;i<1000;i++){
                Socket a=new Socket(),b=new Socket();
                RpcSocketRegistry.Pair lease=registry.trackPair(a,b);
                ok(lease!=null);lease.close();lease.close();ok(a.isClosed()&&b.isClosed());
            }
            Socket fault=new FaultSocket(),other=new Socket();
            RpcSocketRegistry.Pair lease=registry.trackPair(fault,other);
            ok(lease!=null);lease.close();ok(fault.isClosed()&&other.isClosed());
            Socket finalA=new Socket(),finalB=new Socket();
            RpcSocketRegistry.Pair last=registry.trackPair(finalA,finalB);ok(last!=null);
            registry.close();
            ok(local[0].isClosed()&&remote[0].isClosed()&&local[2].isClosed()&&remote[2].isClosed());
            ok(finalA.isClosed()&&finalB.isClosed()&&!registry.active());
            last.close();leases[0].close();leases[2].close();
            Socket lateA=new Socket(),lateB=new Socket();
            ok(registry.trackPair(lateA,lateB)==null&&lateA.isClosed()&&lateB.isClosed());
        }
        // Real simultaneous callers, no connect/listen/network operations.
        ExecutorService pool=Executors.newFixedThreadPool(12);
        try{
            for(int round=0;round<50;round++){
                try(RpcSocketRegistry registry=new RpcSocketRegistry(6)){
                    CountDownLatch start=new CountDownLatch(1);
                    List<Socket[]> endpoints=new ArrayList<>();
                    List<Future<RpcSocketRegistry.Pair>> futures=new ArrayList<>();
                    for(int i=0;i<12;i++){
                        Socket[] pair={new Socket(),new Socket()};endpoints.add(pair);
                        futures.add(pool.submit(()->{start.await();return registry.trackPair(pair[0],pair[1]);}));
                    }
                    start.countDown();
                    int admitted=0;
                    List<RpcSocketRegistry.Pair> leases=new ArrayList<>();
                    for(int i=0;i<futures.size();i++){
                        RpcSocketRegistry.Pair lease=futures.get(i).get(5,TimeUnit.SECONDS);
                        Socket[] pair=endpoints.get(i);
                        if(lease==null)ok(pair[0].isClosed()&&pair[1].isClosed());
                        else{admitted++;leases.add(lease);ok(!pair[0].isClosed()&&!pair[1].isClosed());}
                    }
                    ok(admitted==3);
                    registry.close();
                    for(Socket[] pair:endpoints)ok(pair[0].isClosed()&&pair[1].isClosed());
                    for(RpcSocketRegistry.Pair lease:leases)lease.close();
                }
            }
        }finally{pool.shutdownNow();}
    }
    public static void main(String[] args)throws Exception{
        RpcSocketRegistry first=new RpcSocketRegistry(2);
        Socket one=new Socket(),two=new Socket(),over=new Socket();
        ok(first.track(one));ok(first.track(two));ok(first.track(one));ok(!first.track(over)&&over.isClosed());
        first.untrack(two);Socket three=new Socket();ok(first.track(three));first.close();
        ok(one.isClosed()&&three.isClosed());ok(!two.isClosed());two.close();
        Socket late=new Socket();ok(!first.track(late)&&late.isClosed());ok(!first.active());first.close();
        try(RpcSocketRegistry next=new RpcSocketRegistry(1)){
            Socket fresh=new Socket();ok(next.active()&&next.track(fresh));ok(!fresh.isClosed());
            next.close();ok(fresh.isClosed());
        }
        pairs();
        System.out.println("PASS: "+n+" RPC socket ownership checks");
    }
}
