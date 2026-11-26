package com.ollamapocket.app;

import java.util.List;
import java.util.Map;

/** Read-only capacity hints. No admission, engine activation, download or routing decision. */
final class ModelResourceAdvice {
    static final long MIB=1024L*1024L, MAX_MIB=1_048_576L, HEADROOM_MIB=512L;
    enum State { UNSUPPORTED, THERMAL, LOW_MEMORY, UNKNOWN, SHORTFALL, FIT_ESTIMATE }
    enum Route { NO_RECOMMENDATION, CPU_CANDIDATE, MEASURE_FIRST, HISTORICAL_CPU, HISTORICAL_GPU }
    static final class Result {
        final State state;
        final long weightMiB,requiredMiB,budgetMiB;
        Result(State state,long weightMiB,long requiredMiB,long budgetMiB){
            this.state=state;this.weightMiB=weightMiB;this.requiredMiB=requiredMiB;this.budgetMiB=budgetMiB;
        }
    }
    private ModelResourceAdvice(){}
    static long sampledMiB(double gib){
        if(!Double.isFinite(gib)||gib<=0||gib>MAX_MIB/1024.0)return 0;
        return (long)Math.floor(gib*1024.0);
    }
    /** Unknown or invalid thermal data must not be treated as an affirmative safety reading. */
    static InferenceSafetyPolicy.Reason sampledRisk(int thermal,boolean lowMemory){
        if(thermal<0||thermal>6)return lowMemory?InferenceSafetyPolicy.Reason.LOW_MEMORY:null;
        return InferenceSafetyPolicy.evaluate(thermal,lowMemory);
    }
    static Result assess(boolean arm64,InferenceSafetyPolicy.Reason risk,long total,long available,long bytes){
        long weight=bytes>0&&bytes<=MAX_MIB*MIB?1+(bytes-1)/MIB:0;
        long required=weight==0?0:weight+(weight+4)/5+768;
        long budget=total>0&&total<=MAX_MIB&&available>0&&available<=MAX_MIB?Math.min(total,available):0;
        State state;
        if(!arm64)state=State.UNSUPPORTED;
        else if(risk==InferenceSafetyPolicy.Reason.THERMAL)state=State.THERMAL;
        else if(risk==InferenceSafetyPolicy.Reason.LOW_MEMORY)state=State.LOW_MEMORY;
        else if(risk!=InferenceSafetyPolicy.Reason.NONE||weight==0||budget==0)state=State.UNKNOWN;
        else state=required+HEADROOM_MIB<=budget?State.FIT_ESTIMATE:State.SHORTFALL;
        return new Result(state,weight,required,budget);
    }
    /** Same existing catalog grouping, strictly smaller known installed tag; largest fitting weight,
     * lexicographic tie-break. Size is not a quality score. */
    static String alternative(Result current,String selected,List<String> installed,Map<String,Long> bytes){
        if(current==null||current.state!=State.SHORTFALL||selected==null||selected.isEmpty()||
            installed==null||bytes==null||!installed.contains(selected))return null;
        Long original=bytes.get(selected);
        if(original==null||original<=0||original>MAX_MIB*MIB)return null;
        String group=ModelGroupings.localKey(selected),best=null;
        long bestBytes=0;
        for(String tag:installed){
            if(tag==null||tag.isEmpty()||tag.equals(selected)||!group.equals(ModelGroupings.localKey(tag)))continue;
            Long size=bytes.get(tag);
            if(size==null||size<=0||size>=original)continue;
            Result candidate=assess(true,InferenceSafetyPolicy.Reason.NONE,current.budgetMiB,current.budgetMiB,size);
            if(candidate.state==State.FIT_ESTIMATE&&(size>bestBytes||(size==bestBytes&&best!=null&&tag.compareTo(best)<0))){
                best=tag;bestBytes=size;
            }
        }
        return best;
    }
    /** Profile must have already passed cached model/digest/runtime and age validation. */
    static Route route(Result estimate,boolean gpuAvailable,BenchmarkProfile profile){
        if(estimate==null||estimate.state!=State.FIT_ESTIMATE)return Route.NO_RECOMMENDATION;
        if(!gpuAvailable)return Route.CPU_CANDIDATE;
        if(profile==null||profile.cpu==null||profile.gpu==null||
            !profile.cpu.stableCandidate()||!profile.gpu.stableCandidate())return Route.MEASURE_FIRST;
        String mode=profile.preferredLocalMode();
        return "gpu".equals(mode)?Route.HISTORICAL_GPU:"cpu".equals(mode)?Route.HISTORICAL_CPU:Route.MEASURE_FIRST;
    }
}
