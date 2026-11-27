package com.ollamapocket.app;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class EngineTransitionPolicyTest {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("transition "+checks);checks++;}
    public static void main(String[] args){
        for(long expected:new long[]{Long.MIN_VALUE,-1,0,1,Long.MAX_VALUE})
            for(long actual:new long[]{Long.MIN_VALUE,-1,0,1,Long.MAX_VALUE})
                for(int flags=0;flags<8;flags++){
                    boolean cancelled=(flags&1)!=0,interrupted=(flags&2)!=0,safe=(flags&4)!=0;
                    try{
                        if(interrupted)Thread.currentThread().interrupt();
                        BooleanSupplier abort=EngineTransitionPolicy.abortSignal(expected,()->actual,()->cancelled,()->safe);
                        check(abort.getAsBoolean()==(expected!=actual||cancelled||interrupted||!safe));
                        check(Thread.currentThread().isInterrupted()==interrupted);
                    }finally{Thread.interrupted();}
                }
        AtomicLong epoch=new AtomicLong(7);
        AtomicBoolean cancelled=new AtomicBoolean(false),safe=new AtomicBoolean(true);
        BooleanSupplier abort=EngineTransitionPolicy.abortSignal(7,epoch::get,cancelled::get,safe::get);
        check(!abort.getAsBoolean());epoch.incrementAndGet();check(abort.getAsBoolean());
        epoch.set(7);cancelled.set(true);check(abort.getAsBoolean());
        cancelled.set(false);safe.set(false);check(abort.getAsBoolean());safe.set(true);check(!abort.getAsBoolean());
        check(EngineTransitionPolicy.abortSignal(7,epoch::get,cancelled::get,()->{epoch.incrementAndGet();return true;}).getAsBoolean());
        epoch.set(7);
        check(EngineTransitionPolicy.abortSignal(7,epoch::get,cancelled::get,()->{cancelled.set(true);return true;}).getAsBoolean());
        cancelled.set(false);
        try{
            check(EngineTransitionPolicy.abortSignal(7,epoch::get,cancelled::get,()->{Thread.currentThread().interrupt();return true;}).getAsBoolean());
            check(Thread.currentThread().isInterrupted());
        }finally{Thread.interrupted();}
        for(int invalidateAfter=0;invalidateAfter<=5;invalidateAfter++){
            epoch.set(7);int released=0;
            for(int model=0;model<5;model++){
                if(released==invalidateAfter)epoch.incrementAndGet();
                if(abort.getAsBoolean())break;
                released++;
            }
            check(released==invalidateAfter);
        }
        System.out.println("PASS: "+checks+" engine transition checks");
    }
}
