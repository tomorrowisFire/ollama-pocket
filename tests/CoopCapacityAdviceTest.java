package com.ollamapocket.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CoopCapacityAdviceTest {
    private static int n;
    private static final long M=CoopCapacityAdvice.MIB;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("Capacity advice assertion "+n);}
    private static CoopCapacityAdvice.Peer peer(long safe){return new CoopCapacityAdvice.Peer("Device",true,100,1,safe,8192);}
    private static CoopCapacityAdvice.PeerState state(boolean ready,long age,int thermal,long safe,long total){
        return CoopCapacityAdvice.classify(new CoopCapacityAdvice.Peer("Device",ready,age,thermal,safe,total));
    }
    private static CoopCapacityAdvice.Result run(long bytes,long host,List<CoopCapacityAdvice.Peer> peers){
        return CoopCapacityAdvice.analyze(bytes,true,host,1,false,peers);
    }
    public static void main(String[] args){
        check(state(true,0,0,384,1024)==CoopCapacityAdvice.PeerState.ELIGIBLE);
        check(state(true,29999,2,384,1024)==CoopCapacityAdvice.PeerState.ELIGIBLE);
        check(state(true,30000,1,1024,2048)==CoopCapacityAdvice.PeerState.NOT_READY);
        check(state(true,-1,1,1024,2048)==CoopCapacityAdvice.PeerState.NOT_READY);
        check(state(false,100,1,1024,2048)==CoopCapacityAdvice.PeerState.NOT_READY);
        check(state(true,100,-1,1024,2048)==CoopCapacityAdvice.PeerState.THERMAL_UNKNOWN);
        check(state(true,100,7,1024,2048)==CoopCapacityAdvice.PeerState.THERMAL_UNKNOWN);
        check(state(false,100,3,1024,2048)==CoopCapacityAdvice.PeerState.HOT);
        check(state(true,100,6,1024,2048)==CoopCapacityAdvice.PeerState.HOT);
        check(state(true,100,1,383,1024)==CoopCapacityAdvice.PeerState.RAM_LOW);
        check(state(false,100,1,0,1024)==CoopCapacityAdvice.PeerState.RAM_LOW);
        check(state(true,100,1,-1,1024)==CoopCapacityAdvice.PeerState.RAM_UNKNOWN);
        check(state(true,100,1,1025,1024)==CoopCapacityAdvice.PeerState.RAM_UNKNOWN);
        check(state(true,100,1,1024,0)==CoopCapacityAdvice.PeerState.RAM_UNKNOWN);
        check(state(true,100,1,1024,Long.MAX_VALUE)==CoopCapacityAdvice.PeerState.RAM_UNKNOWN);
        check(CoopCapacityAdvice.classify(null)==CoopCapacityAdvice.PeerState.NOT_READY);
        List<CoopCapacityAdvice.Peer> three=Arrays.asList(peer(1400),peer(2000),peer(1800));
        CoopCapacityAdvice.Result fit=run(3000*M,1800,three);
        check(fit.state==CoopCapacityAdvice.State.FIT&&fit.eligible==3);
        check(fit.full.totalSafeMiB==6424&&fit.full.requiredMiB==3768);
        check(fit.full.selectedIndices.equals(Arrays.asList(0,1,2)));
        check(fit.compact.selectedIndices.equals(Arrays.asList(1,2)));
        check(fit.compact.tensorSplit.equals("1808,1608"));
        check(fit.compact.totalSafeMiB==5216&&fit.lossFits==3);
        CoopCapacityAdvice.Result fragile=run(4000*M,1200,three);
        check(fragile.state==CoopCapacityAdvice.State.FIT);
        check(fragile.compact.selectedIndices.equals(Arrays.asList(0,1,2)));
        check(fragile.lossFits==0);
        CoopCapacityAdvice.Result partial=run(3500*M,1200,three);
        check(partial.state==CoopCapacityAdvice.State.FIT&&partial.lossFits==1);
        CoopCapacityAdvice.Result one=run(128*M,1200,Collections.singletonList(peer(384)));
        check(one.state==CoopCapacityAdvice.State.FIT&&one.lossFits==0);
        check(run(5000*M,1200,three).state==CoopCapacityAdvice.State.SHORTFALL);
        check(run(3000*M,400,three).state==CoopCapacityAdvice.State.HOST_LOW);
        check(run(3000*M,1800,Collections.emptyList()).state==CoopCapacityAdvice.State.NO_REMOTE);
        check(run(3000*M,1800,Arrays.asList(peer(100),new CoopCapacityAdvice.Peer("unknown",true,100,-1,2000,8192))).eligible==0);
        check(run(0,1800,three).state==CoopCapacityAdvice.State.MODEL_UNKNOWN);
        check(run(127*M,1800,three).state==CoopCapacityAdvice.State.MODEL_UNKNOWN);
        check(run(127*M+1,1800,three).modelMiB==128);
        check(run(Long.MAX_VALUE,1800,three).state==CoopCapacityAdvice.State.MODEL_UNKNOWN);
        check(run(3000*M,-1,three).state==CoopCapacityAdvice.State.HOST_UNKNOWN);
        check(run(3000*M,1800,null).state==CoopCapacityAdvice.State.BAD_SAMPLE);
        check(run(3000*M,1800,Arrays.asList(peer(1),peer(1),peer(1),peer(1))).state==CoopCapacityAdvice.State.BAD_SAMPLE);
        check(CoopCapacityAdvice.analyze(3000*M,false,1800,1,false,three).state==CoopCapacityAdvice.State.HOST_UNKNOWN);
        check(CoopCapacityAdvice.analyze(3000*M,true,1800,-1,false,three).state==CoopCapacityAdvice.State.HOST_UNKNOWN);
        check(CoopCapacityAdvice.analyze(3000*M,true,1800,7,false,three).state==CoopCapacityAdvice.State.HOST_UNKNOWN);
        check(CoopCapacityAdvice.analyze(3000*M,true,1800,3,false,three).state==CoopCapacityAdvice.State.HOST_RISK);
        check(CoopCapacityAdvice.analyze(3000*M,true,1800,1,true,three).state==CoopCapacityAdvice.State.HOST_RISK);
        check(CoopCapacityAdvice.label(null).isEmpty());
        check(CoopCapacityAdvice.label("A\nB\u202eC\u2028D").equals("ABCD"));
        check(CoopCapacityAdvice.label("a".repeat(60)).length()==48);
        check(CoopCapacityAdvice.label("😀".repeat(60)).codePointCount(0,96)==48);
        check(fit.full.tensorSplit.equals(CoopMemoryPlanner.evaluate(3000,1800,Arrays.asList(1400L,2000L,1800L)).tensorSplit));
        check(fit.compact.tensorSplit.equals(CoopMemoryPlanner.evaluateCompact(3000,1800,Arrays.asList(1400L,2000L,1800L)).tensorSplit));
        System.out.println("PASS: "+n+" cooperative capacity, readiness, compact and peer-loss assertions");
    }
}
