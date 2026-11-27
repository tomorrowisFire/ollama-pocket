package com.ollamapocket.app;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class VoiceNativeWorkTest {
    private static int checks;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args) throws Exception {
        VoiceNativeWork.Lease first=VoiceNativeWork.acquire(()->false);
        try{VoiceNativeWork.acquire(()->false,0);throw new AssertionError("overlap");}catch(IOException expected){checks++;}
        AtomicBoolean canceled=new AtomicBoolean();
        AtomicReference<Throwable> error=new AtomicReference<>();
        CountDownLatch waiting=new CountDownLatch(1),done=new CountDownLatch(1);
        Thread stale=new Thread(()->{
            waiting.countDown();
            try(VoiceNativeWork.Lease ignored=VoiceNativeWork.acquire(canceled::get)){error.set(new AssertionError("canceled work ran"));}
            catch(IOException expected){}catch(Throwable e){error.set(e);}finally{done.countDown();}
        });
        stale.start();ok(waiting.await(1,TimeUnit.SECONDS));canceled.set(true);
        ok(done.await(1,TimeUnit.SECONDS));ok(error.get()==null);
        first.close();first.close();
        VoiceNativeWork.Lease second=VoiceNativeWork.acquire(()->false);
        first.close();
        try{VoiceNativeWork.acquire(()->false,0);throw new AssertionError("old release unlocked new engine");}catch(IOException expected){checks++;}
        CountDownLatch released=new CountDownLatch(1);
        Thread next=new Thread(()->{try(VoiceNativeWork.Lease ignored=VoiceNativeWork.acquire(()->false)){released.countDown();}catch(Throwable e){error.set(e);}});
        next.start();second.close();ok(released.await(1,TimeUnit.SECONDS));next.join(1000);ok(error.get()==null);
        try{VoiceNativeWork.acquire(()->true);throw new AssertionError("canceled admission");}catch(IOException expected){checks++;}
        try(VoiceNativeWork.Lease ignored=VoiceNativeWork.acquire(()->false)){checks++;}
        Thread.currentThread().interrupt();
        try{VoiceNativeWork.acquire(()->false);throw new AssertionError("interrupted admission");}catch(IOException expected){checks++;}finally{Thread.interrupted();}
        System.out.println("PASS: "+checks+" native voice ownership/cancellation checks");
    }
}
