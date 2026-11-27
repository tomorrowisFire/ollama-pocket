package com.ollamapocket.app;

public final class InferenceHandoffTest {
    private static int n;
    private static void check(boolean ok){if(!ok)throw new AssertionError("check "+n);n++;}
    public static void main(String[] args)throws Exception{
        InferenceSafetyPolicy.Reason[] risks={null,InferenceSafetyPolicy.Reason.NONE,InferenceSafetyPolicy.Reason.THERMAL,InferenceSafetyPolicy.Reason.LOW_MEMORY,InferenceSafetyPolicy.Reason.PEER_LOST};
        for(boolean cancelled:new boolean[]{false,true})for(boolean interrupted:new boolean[]{false,true})
            for(InferenceSafetyPolicy.Reason previous:risks)for(InferenceSafetyPolicy.Reason current:risks){
                InferenceSafetyPolicy.Reason risk=InferenceSafetyPolicy.retainRisk(previous,current);
                check(risk==(previous==InferenceSafetyPolicy.Reason.NONE?current:previous));
                boolean allowed=!cancelled&&!interrupted&&previous==InferenceSafetyPolicy.Reason.NONE&&current==InferenceSafetyPolicy.Reason.NONE;
                check(InferenceSafetyPolicy.mayStart(cancelled,interrupted,risk)==allowed);
                for(int partial:new int[]{-1,0,1,Integer.MAX_VALUE})
                    check(InferenceSafetyPolicy.mayFallback(cancelled,interrupted,partial,risk)==(allowed&&partial==0));
            }
        Thread.currentThread().interrupt();
        try{Thread.sleep(1);throw new AssertionError("sleep did not interrupt");}
        catch(InterruptedException e){
            check(!Thread.currentThread().isInterrupted());
            InferenceSafetyPolicy.restoreInterrupt(e);
            check(Thread.currentThread().isInterrupted());
            check(!InferenceSafetyPolicy.mayFallback(false,Thread.currentThread().isInterrupted(),0,InferenceSafetyPolicy.Reason.NONE));
        }finally{Thread.interrupted();}
        for(Exception e:new Exception[]{new java.io.IOException(),new java.io.InterruptedIOException(),new java.net.SocketTimeoutException(),new IllegalStateException()}){
            InferenceSafetyPolicy.restoreInterrupt(e);
            check(!Thread.currentThread().isInterrupted());
            check(InferenceSafetyPolicy.mayFallback(false,false,0,InferenceSafetyPolicy.Reason.NONE));
        }
        System.out.println("PASS: "+n+" inference handoff checks");
    }
}
