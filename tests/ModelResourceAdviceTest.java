package com.ollamapocket.app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ModelResourceAdviceTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("Resource advice assertion "+n);}
    private static final long M=ModelResourceAdvice.MIB;
    private static final InferenceSafetyPolicy.Reason NONE=InferenceSafetyPolicy.Reason.NONE;
    private static ModelResourceAdvice.Result at(long total,long available,long size){
        return ModelResourceAdvice.assess(true,NONE,total,available,size);
    }
    private static BenchmarkProfile profile(BenchmarkProfile.Measurement cpu,BenchmarkProfile.Measurement gpu){
        return new BenchmarkProfile("model","a".repeat(64),"b".repeat(64),1000,cpu,gpu);
    }
    public static void main(String[] args){
        check(ModelResourceAdvice.sampledMiB(1.5)==1536);
        check(ModelResourceAdvice.sampledMiB(0.0001)==0);
        check(ModelResourceAdvice.sampledMiB(Double.NaN)==0);
        check(ModelResourceAdvice.sampledMiB(Double.POSITIVE_INFINITY)==0);
        check(ModelResourceAdvice.sampledMiB(-1)==0);
        check(ModelResourceAdvice.sampledMiB(1025)==0);
        check(ModelResourceAdvice.sampledRisk(-1,false)==null);
        check(ModelResourceAdvice.sampledRisk(7,false)==null);
        check(ModelResourceAdvice.sampledRisk(-1,true)==InferenceSafetyPolicy.Reason.LOW_MEMORY);
        check(ModelResourceAdvice.sampledRisk(3,true)==InferenceSafetyPolicy.Reason.THERMAL);
        check(ModelResourceAdvice.sampledRisk(2,false)==NONE);
        ModelResourceAdvice.Result edge=at(4096,1880,500*M);
        check(edge.weightMiB==500&&edge.requiredMiB==1368&&edge.budgetMiB==1880);
        check(edge.state==ModelResourceAdvice.State.FIT_ESTIMATE);
        check(at(4096,1879,500*M).state==ModelResourceAdvice.State.SHORTFALL);
        check(at(4096,4096,500*M+1).requiredMiB==1370);
        check(at(4096,8192,500*M).budgetMiB==0);
        check(at(4096,8192,500*M).state==ModelResourceAdvice.State.UNKNOWN);
        check(at(4096,4096,500*M).state==ModelResourceAdvice.State.FIT_ESTIMATE);
        check(at(0,4096,M).state==ModelResourceAdvice.State.UNKNOWN);
        check(at(4096,0,M).state==ModelResourceAdvice.State.UNKNOWN);
        check(at(4096,4096,0).state==ModelResourceAdvice.State.UNKNOWN);
        check(at(4096,4096,-1).state==ModelResourceAdvice.State.UNKNOWN);
        check(at(4096,4096,Long.MAX_VALUE).state==ModelResourceAdvice.State.UNKNOWN);
        check(at(Long.MAX_VALUE,4096,M).state==ModelResourceAdvice.State.UNKNOWN);
        check(ModelResourceAdvice.assess(false,NONE,4096,4096,M).state==ModelResourceAdvice.State.UNSUPPORTED);
        check(ModelResourceAdvice.assess(true,InferenceSafetyPolicy.Reason.THERMAL,0,0,0).state==ModelResourceAdvice.State.THERMAL);
        check(ModelResourceAdvice.assess(true,InferenceSafetyPolicy.Reason.LOW_MEMORY,4096,4096,M).state==ModelResourceAdvice.State.LOW_MEMORY);
        check(ModelResourceAdvice.assess(true,null,4096,4096,M).state==ModelResourceAdvice.State.UNKNOWN);
        String selected="qwen2.5:7b-instruct-q8_0",a="qwen2.5:7b-instruct-q4_K_M",b="qwen2.5:7b-instruct-q4_0";
        String tooBig="qwen2.5:7b-instruct-q6_K",unrelated="other:7b-instruct-q4_K_M",missing="qwen2.5:7b-instruct-q2_K",notInstalled="qwen2.5:7b-instruct-q5_K_M";
        Map<String,Long> sizes=new HashMap<>();
        sizes.put(selected,4000*M);sizes.put(a,1700*M);sizes.put(b,1700*M);sizes.put(tooBig,3500*M);
        sizes.put(unrelated,1900*M);sizes.put(notInstalled,1950*M);
        java.util.List<String> installed=Arrays.asList(selected,a,b,tooBig,unrelated,missing);
        ModelResourceAdvice.Result shortfall=at(8192,3500,4000*M);
        check(shortfall.state==ModelResourceAdvice.State.SHORTFALL);
        check(b.equals(ModelResourceAdvice.alternative(shortfall,selected,installed,sizes)));
        check(b.equals(ModelResourceAdvice.alternative(shortfall,selected,Arrays.asList(b,a,selected,tooBig),sizes)));
        check(ModelResourceAdvice.alternative(edge,selected,installed,sizes)==null);
        check(ModelResourceAdvice.alternative(shortfall,selected,Arrays.asList(selected,tooBig,unrelated,missing),sizes)==null);
        check(ModelResourceAdvice.alternative(shortfall,selected,Arrays.asList(a,b),sizes)==null);
        sizes.put(a,1800*M);
        check(a.equals(ModelResourceAdvice.alternative(shortfall,selected,installed,sizes)));
        sizes.put(a,4000*M);
        check(b.equals(ModelResourceAdvice.alternative(shortfall,selected,installed,sizes)));
        BenchmarkProfile.Measurement cpu=new BenchmarkProfile.Measurement(10,500,5,1024,1,1);
        BenchmarkProfile.Measurement gpu=new BenchmarkProfile.Measurement(15,600,10,1024,1,1);
        BenchmarkProfile.Measurement hot=new BenchmarkProfile.Measurement(30,200,0,1024,1,3);
        BenchmarkProfile.Measurement unknown=new BenchmarkProfile.Measurement(30,200,0,1024,-1,-1);
        BenchmarkProfile.Measurement slow=new BenchmarkProfile.Measurement(11,2000,0,1024,1,1);
        BenchmarkProfile p=profile(cpu,gpu);
        check(ModelResourceAdvice.route(edge,true,p)==ModelResourceAdvice.Route.HISTORICAL_GPU);
        check(ModelResourceAdvice.route(edge,true,profile(cpu,slow))==ModelResourceAdvice.Route.HISTORICAL_CPU);
        check(ModelResourceAdvice.route(edge,true,profile(cpu,hot))==ModelResourceAdvice.Route.MEASURE_FIRST);
        check(ModelResourceAdvice.route(edge,true,profile(cpu,unknown))==ModelResourceAdvice.Route.MEASURE_FIRST);
        check(ModelResourceAdvice.route(edge,true,profile(cpu,null))==ModelResourceAdvice.Route.MEASURE_FIRST);
        check(ModelResourceAdvice.route(edge,true,null)==ModelResourceAdvice.Route.MEASURE_FIRST);
        check(ModelResourceAdvice.route(edge,false,p)==ModelResourceAdvice.Route.CPU_CANDIDATE);
        check(ModelResourceAdvice.route(shortfall,true,p)==ModelResourceAdvice.Route.NO_RECOMMENDATION);
        check(!p.fresh("model","c".repeat(64),"b".repeat(64),1001));
        check(!p.fresh("model","a".repeat(64),"b".repeat(64),1000+BenchmarkProfile.MAX_AGE_MS+1));
        check(!p.fresh("model","a".repeat(64),"c".repeat(64),1001));
        System.out.println("PASS: "+n+" resource advisor capacity, alternatives and historical route assertions");
    }
}
