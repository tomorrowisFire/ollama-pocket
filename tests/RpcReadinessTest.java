package com.ollamapocket.app;

import java.io.IOException;
import java.net.SocketTimeoutException;

public final class RpcReadinessTest {
    private static int checks;
    private static void check(boolean ok){if(!ok)throw new AssertionError("RPC check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        String a="127.0.0.1:4001",b="127.0.0.1:4002",both=a+","+b;
        String[] live={both,b+","+a,both+",127.0.0.1:4003",a,b,"",null,a+"0,"+b};
        String[] approved={null,both,b+","+a,a,b,"",a+"0,"+b};
        for(int mask=0;mask<64;mask++){
            boolean generation=(mask&1)!=0,alive=(mask&2)!=0,health=(mask&4)!=0,session=(mask&8)!=0,
                cancelled=(mask&16)!=0,interrupted=(mask&32)!=0;
            for(InferenceSafetyPolicy.Reason risk:new InferenceSafetyPolicy.Reason[]{null,InferenceSafetyPolicy.Reason.NONE,InferenceSafetyPolicy.Reason.THERMAL,InferenceSafetyPolicy.Reason.LOW_MEMORY,InferenceSafetyPolicy.Reason.PEER_LOST})
                for(int i=0;i<live.length;i++)for(int j=0;j<approved.length;j++){
                    boolean expected=generation&&alive&&health&&session&&!cancelled&&!interrupted&&risk==InferenceSafetyPolicy.Reason.NONE&&i<3&&j<3;
                    check(InferenceSafetyPolicy.mayPublishRpc(generation,alive,health,session,cancelled,interrupted,risk,both,live[i],approved[j])==expected);
                }
        }
        for(String expected:new String[]{null,"",a+",",","+a})
            check(!InferenceSafetyPolicy.mayPublishRpc(true,true,true,true,false,false,InferenceSafetyPolicy.Reason.NONE,expected,both,null));
        for(String old:new String[]{null,"","s1","s2"})for(String now:new String[]{null,"","s1","s2"})for(String role:new String[]{null,"off","guest","host"})
            check(InferenceSafetyPolicy.sameHostSession(old,now,role)==("host".equals(role)&&old!=null&&!old.isEmpty()&&old.equals(now)));
        Thread.currentThread().interrupt();
        try{Thread.sleep(1);throw new AssertionError("sleep did not interrupt");}
        catch(InterruptedException e){
            check(!Thread.currentThread().isInterrupted());
            InferenceSafetyPolicy.restoreInterrupt(e);
            check(Thread.currentThread().isInterrupted());
            check(!InferenceSafetyPolicy.mayPublishRpc(true,true,true,true,false,Thread.currentThread().isInterrupted(),InferenceSafetyPolicy.Reason.NONE,both,both,null));
        }finally{Thread.interrupted();}
        for(Exception e:new Exception[]{new IOException(),new SocketTimeoutException()}){
            InferenceSafetyPolicy.restoreInterrupt(e);
            check(!Thread.currentThread().isInterrupted());
            check(InferenceSafetyPolicy.mayPublishRpc(true,true,true,true,false,false,InferenceSafetyPolicy.Reason.NONE,both,both,null));
        }
        System.out.println("PASS: "+checks+" RPC readiness checks");
    }
}
