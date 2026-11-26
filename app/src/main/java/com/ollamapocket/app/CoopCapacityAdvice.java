package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Advisory snapshot only; never used to select or start a live inference route. */
final class CoopCapacityAdvice {
    static final long MIB=1048576L, MAX_MIB=1048576L, MAX_AGE_MS=30000L;
    static final int MAX_PEERS=3;
    enum State { MODEL_UNKNOWN, HOST_UNKNOWN, HOST_RISK, BAD_SAMPLE, NO_REMOTE, HOST_LOW, SHORTFALL, FIT }
    enum PeerState { NOT_READY, THERMAL_UNKNOWN, HOT, RAM_UNKNOWN, RAM_LOW, ELIGIBLE }
    static final class Peer {
        final String name;
        final boolean ready;
        final long ageMs,safeMiB,totalMiB;
        final int thermal;
        Peer(String name,boolean ready,long ageMs,int thermal,long safeMiB,long totalMiB){
            this.name=label(name);this.ready=ready;this.ageMs=ageMs;this.thermal=thermal;
            this.safeMiB=safeMiB;this.totalMiB=totalMiB;
        }
        boolean ramKnown(){return totalMiB>0&&totalMiB<=MAX_MIB&&safeMiB>=0&&safeMiB<=totalMiB;}
    }
    static final class Result {
        final State state;
        final long modelMiB;
        final List<PeerState> peers;
        final CoopMemoryPlanner.Plan full,compact;
        final int eligible,lossFits;
        Result(State state,long modelMiB,List<PeerState> peers,CoopMemoryPlanner.Plan full,
               CoopMemoryPlanner.Plan compact,int eligible,int lossFits){
            this.state=state;this.modelMiB=modelMiB;this.peers=Collections.unmodifiableList(new ArrayList<>(peers));
            this.full=full;this.compact=compact;this.eligible=eligible;this.lossFits=lossFits;
        }
    }
    private CoopCapacityAdvice(){}
    static String label(String input){
        if(input==null)return "";
        StringBuilder out=new StringBuilder();
        int count=0,scanned=0;
        for(int i=0;i<input.length()&&count<48&&scanned++<192;){
            int cp=input.codePointAt(i);i+=Character.charCount(cp);
            int type=Character.getType(cp);
            if(Character.isISOControl(cp)||type==Character.FORMAT||type==Character.SURROGATE||
                type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR)continue;
            out.appendCodePoint(cp);count++;
        }
        return out.toString().trim();
    }
    static PeerState classify(Peer p){
        if(p==null||p.ageMs<0||p.ageMs>=MAX_AGE_MS)return PeerState.NOT_READY;
        if(p.thermal<0||p.thermal>6)return PeerState.THERMAL_UNKNOWN;
        if(p.thermal>=3)return PeerState.HOT;
        if(!p.ramKnown())return PeerState.RAM_UNKNOWN;
        if(p.safeMiB<CoopMemoryPlanner.MIN_REMOTE_MIB)return PeerState.RAM_LOW;
        return p.ready?PeerState.ELIGIBLE:PeerState.NOT_READY;
    }
    static Result analyze(long bytes,boolean hostKnown,long hostBudget,int hostThermal,boolean hostLow,List<Peer> peers){
        long weight=bytes>0&&bytes<=MAX_MIB*MIB?1+(bytes-1)/MIB:0;
        List<PeerState> states=new ArrayList<>();
        List<Long> budgets=new ArrayList<>();
        if(peers==null||peers.size()>MAX_PEERS)return new Result(State.BAD_SAMPLE,weight,states,null,null,0,0);
        int eligible=0;
        for(Peer peer:peers){
            PeerState state=classify(peer);states.add(state);
            budgets.add(state==PeerState.ELIGIBLE?peer.safeMiB:0L);
            if(state==PeerState.ELIGIBLE)eligible++;
        }
        State early=weight<128?State.MODEL_UNKNOWN:
            hostLow||(hostThermal>=3&&hostThermal<=6)?State.HOST_RISK:
            !hostKnown||hostThermal<0||hostThermal>6||hostBudget<0||hostBudget>MAX_MIB?State.HOST_UNKNOWN:null;
        if(early!=null)return new Result(early,weight,states,null,null,eligible,0);
        CoopMemoryPlanner.Plan full=CoopMemoryPlanner.evaluate(weight,hostBudget,budgets);
        CoopMemoryPlanner.Plan compact=CoopMemoryPlanner.evaluateCompact(weight,hostBudget,budgets);
        State state;
        switch(full.reasonCode){
            case NO_REMOTE:state=State.NO_REMOTE;break;
            case HOST_LOW:state=State.HOST_LOW;break;
            case SHORTFALL:state=State.SHORTFALL;break;
            case PASS:state=State.FIT;break;
            default:state=State.MODEL_UNKNOWN;
        }
        int lossFits=0;
        if(full.allowed)for(int i=0;i<budgets.size();i++){
            if(states.get(i)!=PeerState.ELIGIBLE)continue;
            List<Long> remaining=new ArrayList<>(budgets);remaining.set(i,0L);
            if(CoopMemoryPlanner.evaluate(weight,hostBudget,remaining).allowed)lossFits++;
        }
        return new Result(state,weight,states,full,compact,eligible,lossFits);
    }
}
