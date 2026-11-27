package com.ollamapocket.app;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class NativeProcessSlotTest {
    private static int checks;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+checks);checks++;}
    private static void await(CountDownLatch gate){
        try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
    public static void main(String[] args)throws Exception{
        NativeProcessSlot<String> slot=new NativeProcessSlot<>();
        String first=new String("process"),equal=new String("process"),second=new String("second");
        ok(slot.current()==null);ok(slot.detach()==null);ok(!slot.retire(null));
        boolean invalid=false;try{slot.install(null);}catch(IllegalArgumentException expected){invalid=true;}ok(invalid);
        slot.install(first);ok(slot.current()==first);ok(!slot.retire(equal));ok(slot.current()==first);
        slot.install(second);ok(!slot.retire(first));ok(slot.current()==second);ok(slot.retire(second));
        ok(!slot.retire(second));ok(slot.current()==null);
        slot.install(first);ok(slot.detach()==first);slot.install(second);
        ok(!slot.retire(first));ok(slot.current()==second);ok(slot.detach()==second);
        for(int i=0;i<64;i++){
            slot.install(first);CountDownLatch gate=new CountDownLatch(1);AtomicInteger owners=new AtomicInteger();
            Thread stop=new Thread(()->{await(gate);if(slot.detach()==first)owners.incrementAndGet();});
            Thread exit=new Thread(()->{await(gate);if(slot.retire(first))owners.incrementAndGet();});
            stop.start();exit.start();gate.countDown();stop.join();exit.join();
            ok(owners.get()==1);ok(slot.current()==null);
            slot.install(first);CountDownLatch restartGate=new CountDownLatch(1);
            Thread restart=new Thread(()->{await(restartGate);slot.install(second);});
            Thread lateExit=new Thread(()->{await(restartGate);slot.retire(first);});
            restart.start();lateExit.start();restartGate.countDown();restart.join();lateExit.join();
            ok(slot.current()==second);ok(!slot.retire(first));ok(slot.detach()==second);
        }
        System.out.println("PASS: "+checks+" native process ownership assertions");
    }
}
