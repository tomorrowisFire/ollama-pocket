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
}
