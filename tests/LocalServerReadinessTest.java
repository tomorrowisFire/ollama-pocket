package com.ollamapocket.app;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalServerReadinessTest {
    private static int n;
    private static void ok(boolean v){n++;if(!v)throw new AssertionError("check "+n);}
    interface Action {void run()throws Exception;}
    private static void rejects(String code,Action a)throws Exception{
        try{a.run();throw new AssertionError("Unexpected ready");}
        catch(IOException e){ok(code.equals(e.getMessage()));}
    }
    public static void main(String[] args)throws Exception{
        AtomicLong clock=new AtomicLong();AtomicInteger probes=new AtomicInteger();
        LocalServerReadiness.await(()->clock.get()>=600,timeout->{ok(timeout==1000);return probes.incrementAndGet()==3;},()->false,clock::get,clock::addAndGet);
        ok(clock.get()==1000&&probes.get()==3);
        clock.set(0);probes.set(0);
        LocalServerReadiness.await(()->true,timeout->{if(probes.incrementAndGet()<2)throw new IOException("connection refused");return true;},()->false,clock::get,clock::addAndGet);
        ok(clock.get()==200&&probes.get()==2);
        clock.set(0);probes.set(0);
        rejects("LOCAL_SERVER_NOT_READY",()->LocalServerReadiness.await(()->false,t->{probes.incrementAndGet();return true;},()->false,clock::get,clock::addAndGet));
        ok(clock.get()==LocalServerReadiness.WAIT_MS&&probes.get()==0);
        clock.set(0);
        rejects("LOCAL_SERVER_NOT_READY",()->LocalServerReadiness.await(()->true,t->{clock.set(30_000);return true;},()->false,clock::get,clock::addAndGet));
        clock.set(0);AtomicBoolean cancelled=new AtomicBoolean();
        rejects("CHAT_CANCELLED",()->LocalServerReadiness.await(()->true,t->{cancelled.set(true);return true;},cancelled::get,clock::get,clock::addAndGet));
        rejects("CHAT_CANCELLED",()->LocalServerReadiness.await(()->{throw new AssertionError("Probe after cancellation");},t->true,cancelled::get,clock::get,clock::addAndGet));
        cancelled.set(false);clock.set(0);
        rejects("CHAT_CANCELLED",()->LocalServerReadiness.await(()->false,t->true,()->clock.get()>=400,clock::get,clock::addAndGet));
        ok(clock.get()==400);
        clock.set(0);AtomicBoolean alive=new AtomicBoolean(true);
        rejects("LOCAL_SERVER_NOT_READY",()->LocalServerReadiness.await(alive::get,t->{alive.set(false);return true;},()->false,clock::get,clock::addAndGet));
        try{rejects("CHAT_CANCELLED",()->LocalServerReadiness.await(()->true,t->{throw new InterruptedException();},()->false,clock::get,clock::addAndGet));ok(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();}
        try{clock.set(0);rejects("CHAT_CANCELLED",()->LocalServerReadiness.await(()->false,t->true,()->false,clock::get,m->{throw new InterruptedException();}));ok(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();}
        clock.set(0);probes.set(0);cancelled.set(false);
        rejects("CHAT_CANCELLED",()->LocalServerReadiness.await(()->{cancelled.set(true);return true;},t->{probes.incrementAndGet();return true;},cancelled::get,clock::get,clock::addAndGet));
        ok(probes.get()==0);
        clock.set(0);probes.set(0);cancelled.set(false);
        rejects("LOCAL_SERVER_NOT_READY",()->LocalServerReadiness.await(()->{clock.set(30_000);return true;},t->{probes.incrementAndGet();return true;},cancelled::get,clock::get,clock::addAndGet));
        ok(probes.get()==0);
        clock.set(0);probes.set(0);
        LocalServerReadiness.await(()->{clock.set(29_950);return true;},t->{ok(t==50);probes.incrementAndGet();return true;},()->false,clock::get,clock::addAndGet);
        ok(probes.get()==1);
        System.out.println("PASS: "+n+" local server readiness assertions");
    }
}
