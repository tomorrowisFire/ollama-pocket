package com.ollamapocket.app;

/** Android thermal status 3 is SEVERE; unknown telemetry is not proof of safety. */
final class InferenceSafetyPolicy {
    enum Reason { NONE, THERMAL, LOW_MEMORY, PEER_LOST }
    static Reason evaluate(int thermal,boolean lowMemory){
        if(thermal>=3)return Reason.THERMAL;
        if(lowMemory)return Reason.LOW_MEMORY;
        return Reason.NONE;
    }
    static boolean peerSafe(int thermal){return thermal<3;}
    static boolean mayStart(boolean cancelled,boolean interrupted,Reason risk){
        return !cancelled&&!interrupted&&risk==Reason.NONE;
    }
    static boolean mayFallback(boolean cancelled,boolean interrupted,int partialLength,Reason risk){
        return partialLength==0&&mayStart(cancelled,interrupted,risk);
    }
    static Reason retainRisk(Reason previous,Reason current){
        return previous==Reason.NONE?current:previous;
    }
    static void restoreInterrupt(Exception failure){
        if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
    }
    static boolean hasEndpoints(String expected,String available){
        if(expected==null||expected.isEmpty()||available==null||available.isEmpty())return false;
        String set=","+available+",";
        for(String endpoint:expected.split(",",-1))if(endpoint.isEmpty()||!set.contains(","+endpoint+","))return false;
        return true;
    }
}
