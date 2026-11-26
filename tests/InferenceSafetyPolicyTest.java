package com.ollamapocket.app;

public final class InferenceSafetyPolicyTest {
    private static int n;
    private static void check(boolean ok){if(!ok)throw new AssertionError("check "+n);n++;}
    public static void main(String[] args){
        check(InferenceSafetyPolicy.evaluate(-1,false)==InferenceSafetyPolicy.Reason.NONE);
        check(InferenceSafetyPolicy.evaluate(0,false)==InferenceSafetyPolicy.Reason.NONE);
        check(InferenceSafetyPolicy.evaluate(2,false)==InferenceSafetyPolicy.Reason.NONE);
        check(InferenceSafetyPolicy.evaluate(3,false)==InferenceSafetyPolicy.Reason.THERMAL);
        check(InferenceSafetyPolicy.evaluate(6,true)==InferenceSafetyPolicy.Reason.THERMAL);
        check(InferenceSafetyPolicy.evaluate(0,true)==InferenceSafetyPolicy.Reason.LOW_MEMORY);
        check(InferenceSafetyPolicy.evaluate(-1,true)==InferenceSafetyPolicy.Reason.LOW_MEMORY);
        check(InferenceSafetyPolicy.peerSafe(2));
        check(!InferenceSafetyPolicy.peerSafe(3));
        check(!InferenceSafetyPolicy.peerSafe(6));
        System.out.println("PASS: "+n+" inference safety policy checks");
    }
}
