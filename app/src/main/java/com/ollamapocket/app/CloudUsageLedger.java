package com.ollamapocket.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory estimates only; never represents provider billing or a hard monetary cap. */
final class CloudUsageLedger {
    static final class Totals {
        long input,output,requests,unknown;
        double estimated;
        long unpriced;
    }
    static final int MAX_SCOPES=128;
    private final Map<String,Totals> entries=new LinkedHashMap<>();
    private static long add(long a,long b){return b>Long.MAX_VALUE-a?Long.MAX_VALUE:a+b;}
    Totals get(String scope){
        Totals t=entries.get(scope);
        return t==null?new Totals():t;
    }
    /** No request may be dispatched into a scope that cannot be accounted for. */
    boolean canTrack(String scope){
        return scope!=null&&!scope.isEmpty()&&(entries.containsKey(scope)||entries.size()<MAX_SCOPES);
    }
    int trackedScopes(){return entries.size();}
    /** Detached read-only summary; LAN scopes match exact provider plus address separator. */
    Totals snapshotForProvider(String provider,boolean remote){
        Totals result=new Totals();
        if(provider==null||!provider.matches("[A-Z][A-Z0-9_]{0,31}"))return result;
        String prefix=provider+"@";
        for(Map.Entry<String,Totals> entry:entries.entrySet()){
            String key=entry.getKey();
            if(remote?(!key.startsWith(prefix)||key.length()==prefix.length()):!provider.equals(key))continue;
            Totals t=entry.getValue();
            result.input=add(result.input,t.input);result.output=add(result.output,t.output);
            result.requests=add(result.requests,t.requests);result.unknown=add(result.unknown,t.unknown);
            result.unpriced=add(result.unpriced,t.unpriced);
            if(!remote)result.estimated+=t.estimated;
        }
        return result;
    }
    void record(String scope,long input,long output,double inRate,double outRate){
        if(!canTrack(scope))return;
        Totals t=entries.computeIfAbsent(scope,k->new Totals());
        t.requests=add(t.requests,1);
        if(input>=0)t.input=add(t.input,input);
        if(output>=0)t.output=add(t.output,output);
        if(input<0||output<0)t.unknown=add(t.unknown,1);
        if(input>=0&&output>=0&&Double.isFinite(inRate)&&Double.isFinite(outRate)&&inRate>=0&&outRate>=0)
            t.estimated+=(inRate*input+outRate*output)/1000000d;
        else t.unpriced=add(t.unpriced,1);
    }
    boolean allows(String scope,double budget,double inRate,double outRate){
        if(!canTrack(scope))return false;
        if(budget<0)return true;
        Totals t=get(scope);
        return Double.isFinite(budget)&&Double.isFinite(inRate)&&Double.isFinite(outRate)&&inRate>=0&&outRate>=0&&t.unpriced==0&&t.estimated<budget;
    }
    void clear(){entries.clear();}
}
