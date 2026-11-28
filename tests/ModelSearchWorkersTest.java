package com.ollamapocket.app;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModelSearchWorkersTest {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static boolean waitFor(CountDownLatch gate)throws InterruptedException{return gate.await(5,TimeUnit.SECONDS);}
    private static void stubborn(CountDownLatch entered,CountDownLatch release){
        entered.countDown();
        boolean interrupted=false;
        try{
            for(;;){try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("release deadline");return;}
                catch(InterruptedException e){interrupted=true;}}
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    public static void main(String[] args)throws Exception{
        ModelSearchWorkers workers=new ModelSearchWorkers();
        CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1),latest=new CountDownLatch(2);
        AtomicInteger stale=new AtomicInteger();
        Runnable blocked=()->stubborn(entered,release);
        try{
            workers.replace(blocked,blocked);
            ok(waitFor(entered),"both workers entered");
            for(int i=0;i<1000;i++){
                workers.replace(stale::incrementAndGet,stale::incrementAndGet);
                ok(workers.queued()==2,"only latest two pending tasks");
            }
            workers.replace(latest::countDown,latest::countDown);
            ok(workers.queued()==2,"latest replaces stale tasks");
            release.countDown();
            ok(waitFor(latest),"latest tasks eventually run");
            ok(stale.get()==0,"superseded queued jobs never execute");
        }finally{release.countDown();workers.close();}
        ok(workers.awaitTermination(5000),"workers terminate");
        workers.replace(stale::incrementAndGet,stale::incrementAndGet);
        ok(workers.queued()==0&&stale.get()==0,"closed owner cannot restart");
        workers.close();workers.cancel();
        ModelSearchWorkers cancelled=new ModelSearchWorkers();
        CountDownLatch enteredAgain=new CountDownLatch(2),releaseAgain=new CountDownLatch(1);
        try{
            Runnable hold=()->stubborn(enteredAgain,releaseAgain);
            cancelled.replace(hold,hold);ok(waitFor(enteredAgain),"occupied workers");
            cancelled.replace(stale::incrementAndGet,null);
            ok(cancelled.queued()==1,"one pending provider");
            cancelled.cancel();ok(cancelled.queued()==0,"cancel removes pending jobs");
        }finally{releaseAgain.countDown();cancelled.close();}
        ok(cancelled.awaitTermination(5000),"cancelled owner terminates");
        ok(stale.get()==0,"no cancelled task ran");
        System.out.println("PASS: "+checks+" bounded search worker assertions");
    }
}
