package com.ollamapocket.app;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline fixtures: no socket, server, Android device or inference calls. */
public final class DocumentRequestControlTest {
    private enum Reason { USER, THERMAL, DEADLINE }
    private static int checks;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    private static final class Stub extends HttpURLConnection {
        final AtomicInteger closes=new AtomicInteger();
        final CountDownLatch entered,allow;
        Stub()throws Exception{this(null,null);}
        Stub(CountDownLatch entered,CountDownLatch allow)throws Exception{
            super(new URL("http://127.0.0.1/"));this.entered=entered;this.allow=allow;
        }
        @Override public void connect(){throw new AssertionError("network forbidden");}
        @Override public boolean usingProxy(){return false;}
        @Override public void disconnect(){
            closes.incrementAndGet();
            if(entered!=null){entered.countDown();try{
                if(!allow.await(5,TimeUnit.SECONDS))throw new AssertionError("close timed out");
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
        }
    }
    public static void main(String[] args)throws Exception{
        DocumentRequestControl<Reason> early=new DocumentRequestControl<>();
        Stub unused=new Stub();
        ok(!early.attach(unused)&&early.stop(Reason.USER));
        ok(early.begin()&&!early.begin()&&!early.attach(unused));
        ok(early.reason()==Reason.USER&&!early.stop(Reason.THERMAL));
        ok(early.takeStopped()==null&&unused.closes.get()==0);
        ok(early.finish()==null&&!early.begin()&&!early.stop(Reason.DEADLINE));
        for(int i=0;i<1000;i++){
            DocumentRequestControl<Reason> c=new DocumentRequestControl<>();
            Stub a=new Stub(),b=new Stub();
            ok(c.begin()&&c.attach(a)&&!c.attach(b)&&!c.attach(a));
            ok(!c.release(b)&&c.takeStopped()==null);
            ok(c.release(a)&&!c.release(a)&&c.attach(b));
            ok(c.stop(Reason.DEADLINE)&&!c.stop(Reason.USER));
            ok(c.reason()==Reason.DEADLINE&&b.closes.get()==0&&!c.attach(a));
            HttpURLConnection claimed=c.takeStopped();ok(claimed==b&&c.takeStopped()==null);
            claimed.disconnect();ok(b.closes.get()==1&&a.closes.get()==0);
            ok(c.finish()==null&&!c.release(b)&&!c.begin());
        }
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try{
            CountDownLatch entered=new CountDownLatch(1),allow=new CountDownLatch(1);
            DocumentRequestControl<Reason> c=new DocumentRequestControl<>();
            Stub blocking=new Stub(entered,allow);
            ok(c.begin()&&c.attach(blocking)&&c.stop(Reason.USER)&&blocking.closes.get()==0);
            Future<?> close=pool.submit(()->{HttpURLConnection connection=c.takeStopped();if(connection!=blocking)throw new AssertionError();connection.disconnect();});
            try{
                ok(entered.await(5,TimeUnit.SECONDS));
                Future<Boolean> probe=pool.submit(()->!c.stop(Reason.THERMAL)&&c.reason()==Reason.USER&&c.finish()==null);
                ok(probe.get(2,TimeUnit.SECONDS));
            }finally{allow.countDown();}
            close.get(5,TimeUnit.SECONDS);ok(blocking.closes.get()==1);
            for(int i=0;i<200;i++){
                DocumentRequestControl<Reason> race=new DocumentRequestControl<>();
                Stub target=new Stub();ok(race.begin());
                CountDownLatch start=new CountDownLatch(1);
                Future<Boolean> attach=pool.submit(()->{start.await();return race.attach(target);});
                Future<Boolean> stop=pool.submit(()->{start.await();return race.stop(Reason.USER);});
                start.countDown();boolean attached=attach.get(5,TimeUnit.SECONDS);ok(stop.get(5,TimeUnit.SECONDS));
                ok(race.reason()==Reason.USER&&!race.attach(new Stub()));
                HttpURLConnection claimed=race.takeStopped();ok(attached?claimed==target:claimed==null);
                ok(race.takeStopped()==null&&race.finish()==null);
            }
            for(int i=0;i<200;i++){
                DocumentRequestControl<Reason> race=new DocumentRequestControl<>();
                Stub target=new Stub();ok(race.begin()&&race.attach(target));
                CountDownLatch start=new CountDownLatch(1);
                Future<HttpURLConnection> finish=pool.submit(()->{start.await();return race.finish();});
                Future<Boolean> stop=pool.submit(()->{start.await();return race.stop(Reason.THERMAL);});
                start.countDown();ok(finish.get(5,TimeUnit.SECONDS)==target);
                boolean stopped=stop.get(5,TimeUnit.SECONDS);
                ok(race.reason()==(stopped?Reason.THERMAL:null));
                ok(!race.stop(Reason.USER)&&!race.attach(new Stub())&&race.takeStopped()==null&&race.finish()==null);
            }
        }finally{pool.shutdownNow();}
        System.out.println("PASS: "+checks+" document request ownership and cancellation assertions");
    }
}
