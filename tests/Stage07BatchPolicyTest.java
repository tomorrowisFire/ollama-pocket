package com.ollamapocket.app;

import java.util.Arrays;
import java.util.Collections;

public final class Stage07BatchPolicyTest {
    private static int checks;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args){
        CoopMemoryPlanner.Plan p=CoopMemoryPlanner.evaluateCompact(4000,4000,Arrays.asList(500L,1400L,700L));
        ok(p.allowed&&p.selectedIndices.equals(Collections.singletonList(1)));
        ok(p.tensorSplit.equals("1208"));
        p=CoopMemoryPlanner.evaluateCompact(4000,3200,Arrays.asList(1400L,500L,1000L));
        ok(p.allowed&&p.selectedIndices.equals(Arrays.asList(0,2)));
        ok(p.tensorSplit.equals("1208,808"));
        p=CoopMemoryPlanner.evaluateCompact(4000,4000,Arrays.asList(1000L,1000L,1000L));
        ok(p.allowed&&p.selectedIndices.equals(Collections.singletonList(0)));
        p=CoopMemoryPlanner.evaluateCompact(4000,3000,Arrays.asList(500L,500L));
        ok(!p.allowed&&p.selectedIndices.size()==2);
        ok(!CoopMemoryPlanner.evaluateCompact(4000,4000,Arrays.asList(0L,200L)).allowed);
        ok(InferenceSafetyPolicy.hasEndpoints("a,b","c,b,a"));
        ok(!InferenceSafetyPolicy.hasEndpoints("a,b","a,bb"));
        ok(!InferenceSafetyPolicy.hasEndpoints("a,","a"));
        ok(!InferenceSafetyPolicy.hasEndpoints("","a"));
        ok(InferenceSafetyPolicy.mayFallback(false,false,0,InferenceSafetyPolicy.Reason.NONE));
        ok(!InferenceSafetyPolicy.mayFallback(true,false,0,InferenceSafetyPolicy.Reason.NONE));
        ok(!InferenceSafetyPolicy.mayFallback(false,true,0,InferenceSafetyPolicy.Reason.NONE));
        ok(!InferenceSafetyPolicy.mayFallback(false,false,1,InferenceSafetyPolicy.Reason.NONE));
        ok(!InferenceSafetyPolicy.mayFallback(false,false,0,InferenceSafetyPolicy.Reason.THERMAL));
        ok(!InferenceSafetyPolicy.mayFallback(false,false,0,InferenceSafetyPolicy.Reason.LOW_MEMORY));
        ok(!InferenceSafetyPolicy.mayFallback(false,false,0,InferenceSafetyPolicy.Reason.PEER_LOST));
        System.out.println("PASS: "+checks+" Stage 07 batch policy checks");
    }
}
