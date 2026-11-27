package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;

/** Offline, deliberately rough request estimate. Not a tokenizer or a provider spending ceiling. */
final class CloudRequestAdvice {
    enum State { INVALID, LAN, NO_PRICE, NO_BUDGET, WITHIN_BUDGET, UNKNOWN_USAGE, OVER_BUDGET }
    static final class Estimate {
        final State state;
        final long inputUnits;
        final int outputLimit;
        final BigDecimal requestCost,knownSpent,projected,limit;
        final long unpriced;
        Estimate(State s,long units,int output,BigDecimal request,BigDecimal spent,BigDecimal projection,BigDecimal cap,long unknown){
            state=s;inputUnits=units;outputLimit=output;requestCost=request;knownSpent=spent;
            projected=projection;limit=cap;unpriced=unknown;
        }
        boolean allows(){return state==State.LAN||state==State.NO_PRICE||state==State.NO_BUDGET||state==State.WITHIN_BUDGET;}
    }
    private CloudRequestAdvice(){}
    static boolean validRate(double value){return Double.isFinite(value)&&value>=0&&value<=1000000;}
    static boolean validBudget(double value){return value==-1||validRate(value);}
    static long inputUnits(List<CloudHistory.Message> messages){
        if(messages==null||messages.isEmpty()||messages.size()>25||messages.size()%2==0)return -1;
        long units=128;int chars=0;
        for(int i=0;i<messages.size();i++){
            CloudHistory.Message m=messages.get(i);
            if(m==null||!(i%2==0?"user":"assistant").equals(m.role)||m.content==null||
                !CloudHistory.validMessage(m.content,CloudHistory.MAX_CONTEXT))return -1;
            chars+=m.content.length();if(chars>CloudHistory.MAX_CONTEXT)return -1;
            units+=16L+m.content.getBytes(StandardCharsets.UTF_8).length;
        }
        return units;
    }
    static Estimate estimate(boolean remote,List<CloudHistory.Message> messages,int outputLimit,
                             double inputRate,double outputRate,double budget,CloudUsageLedger.Totals totals){
        long units=inputUnits(messages);
        if(units<0||outputLimit<=0||outputLimit>65536)
            return new Estimate(State.INVALID,units,outputLimit,null,null,null,null,0);
        if(remote)return new Estimate(State.LAN,units,outputLimit,null,null,null,null,0);
        if(!validBudget(budget))return new Estimate(State.INVALID,units,outputLimit,null,null,null,null,0);
        BigDecimal cap=budget<0?null:BigDecimal.valueOf(budget);
        boolean known=totals!=null&&Double.isFinite(totals.estimated)&&totals.estimated>=0&&
            totals.unpriced==0&&totals.unknown==0;
        BigDecimal spent=totals!=null&&Double.isFinite(totals.estimated)&&totals.estimated>=0?
            BigDecimal.valueOf(totals.estimated):null;
        long unknown=totals==null?-1:Math.max(totals.unpriced,totals.unknown);
        BigDecimal cost=null,projected=null;
        if(validRate(inputRate)&&validRate(outputRate)){
            cost=BigDecimal.valueOf(inputRate).multiply(BigDecimal.valueOf(units))
                .add(BigDecimal.valueOf(outputRate).multiply(BigDecimal.valueOf(outputLimit))).movePointLeft(6);
            if(known)projected=spent.add(cost);
        }
        State state;
        if(cap==null)state=cost==null?State.NO_PRICE:State.NO_BUDGET;
        else if(projected==null)state=State.UNKNOWN_USAGE;
        else state=spent.compareTo(cap)<0&&projected.compareTo(cap)<=0?State.WITHIN_BUDGET:State.OVER_BUDGET;
        return new Estimate(state,units,outputLimit,cost,spent,projected,cap,unknown);
    }
}
