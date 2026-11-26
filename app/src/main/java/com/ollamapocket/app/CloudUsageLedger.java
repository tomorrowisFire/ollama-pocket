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
    private final Map<String,Totals> entries=new LinkedHashMap<>();
    private static long add(long a,long b){return b>Long.MAX_VALUE-a?Long.MAX_VALUE:a+b;}
    Totals get(String scope){
        Totals t=entries.get(scope);
        return t==null?new Totals():t;
    }
    void record(String scope,long input,long output,double inRate,double outRate){
        if(!entries.containsKey(scope)&&entries.size()>=128)return;
        Totals t=entries.computeIfAbsent(scope,k->new Totals());
        t.requests=add(t.requests,1);
        if(input>=0)t.input=add(t.input,input);
        if(output>=0)t.output=add(t.output,output);
        if(input<0||output<0)t.unknown=add(t.unknown,1);
        if(input>=0&&output>=0&&Double.isFinite(inRate)&&Double.isFinite(outRate)&&inRate>=0&&outRate>=0)
            t.estimated+=(inRate*input+outRate*output)/1000000d;
        else t.unpriced=add(t.unpriced,1);
    }
    void clear(){entries.clear();}
}
