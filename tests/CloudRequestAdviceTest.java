package com.ollamapocket.app;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public final class CloudRequestAdviceTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("request advice "+n);}
    private static List<CloudHistory.Message> question(String q){return Collections.singletonList(new CloudHistory.Message("user",q));}
    private static CloudRequestAdvice.Estimate run(boolean remote,List<CloudHistory.Message> q,int cap,double in,double out,double budget,CloudUsageLedger.Totals t){
        return CloudRequestAdvice.estimate(remote,q,cap,in,out,budget,t);
    }
    private static boolean eq(BigDecimal a,String b){return a!=null&&a.compareTo(new BigDecimal(b))==0;}
    public static void main(String[] args){
        CloudUsageLedger l=new CloudUsageLedger();
        CloudUsageLedger.Totals t=l.get("OPENAI");
        List<CloudHistory.Message> q=question("abc");
        check(CloudRequestAdvice.inputUnits(q)==147);
        check(CloudRequestAdvice.inputUnits(question("한글"))==150);
        check(CloudRequestAdvice.inputUnits(question("😀"))==148);
        check(CloudRequestAdvice.inputUnits(question("日本語"))==153);
        check(CloudRequestAdvice.inputUnits(question("中文"))==150);
        check(CloudRequestAdvice.inputUnits(question(""))==-1);
        check(CloudRequestAdvice.inputUnits(question(" "))==-1);
        check(CloudRequestAdvice.inputUnits(question(null))==-1);
        check(CloudRequestAdvice.inputUnits(question("\uD800"))==-1);
        check(CloudRequestAdvice.inputUnits(question("\uDC00"))==-1);
        check(CloudRequestAdvice.inputUnits(null)==-1);
        check(CloudRequestAdvice.inputUnits(Collections.emptyList())==-1);
        check(CloudRequestAdvice.inputUnits(Collections.singletonList(null))==-1);
        check(CloudRequestAdvice.inputUnits(Collections.singletonList(new CloudHistory.Message("assistant","x")))==-1);
        check(CloudRequestAdvice.inputUnits(Arrays.asList(new CloudHistory.Message("user","a"),new CloudHistory.Message("assistant","b")))==-1);
        List<CloudHistory.Message> context=Arrays.asList(new CloudHistory.Message("user","a"),new CloudHistory.Message("assistant","b"),new CloudHistory.Message("user","c"));
        check(CloudRequestAdvice.inputUnits(context)==179);
        check(CloudRequestAdvice.inputUnits(Arrays.asList(new CloudHistory.Message("user","a"),new CloudHistory.Message("user","b"),new CloudHistory.Message("user","c")))==-1);
        check(CloudRequestAdvice.inputUnits(question("a".repeat(64000)))==64144);
        check(CloudRequestAdvice.inputUnits(question("a".repeat(64001)))==-1);
        List<CloudHistory.Message> many=new ArrayList<>();
        for(int i=0;i<27;i++)many.add(new CloudHistory.Message(i%2==0?"user":"assistant","a"));
        check(CloudRequestAdvice.inputUnits(many)==-1);
        check(CloudRequestAdvice.inputUnits(many.subList(0,25))==553);
        CloudRequestAdvice.Estimate e=run(false,q,2048,2,4,-1,t);
        check(e.state==CloudRequestAdvice.State.NO_BUDGET&&e.allows());
        check(eq(e.requestCost,"0.008486")&&eq(e.projected,"0.008486"));
        check(e.outputLimit==2048&&e.inputUnits==147);
        check(run(false,q,2048,2,4,0.008486,t).state==CloudRequestAdvice.State.WITHIN_BUDGET);
        check(!run(false,q,2048,2,4,0.008485,t).allows());
        check(run(false,q,2048,2,4,0.008485,t).state==CloudRequestAdvice.State.OVER_BUDGET);
        check(!run(false,q,2048,0,0,0,t).allows());
        check(run(false,q,2048,0,0,1,t).allows());
        check(run(false,q,2048,-1,4,-1,t).state==CloudRequestAdvice.State.NO_PRICE);
        check(run(false,q,2048,2,-1,1,t).state==CloudRequestAdvice.State.UNKNOWN_USAGE);
        check(!run(false,q,2048,Double.NaN,4,1,t).allows());
        check(!run(false,q,2048,2,1000001,1,t).allows());
        check(run(false,q,2048,2,4,Double.NaN,t).state==CloudRequestAdvice.State.INVALID);
        check(run(false,q,2048,2,4,-2,t).state==CloudRequestAdvice.State.INVALID);
        check(run(false,q,0,2,4,1,t).state==CloudRequestAdvice.State.INVALID);
        check(run(false,q,65537,2,4,1,t).state==CloudRequestAdvice.State.INVALID);
        l.record("OPENAI",1000000,500000,2,4);t=l.get("OPENAI");
        e=run(false,q,2048,2,4,4.01,t);
        check(eq(e.knownSpent,"4")&&eq(e.projected,"4.008486")&&e.allows());
        check(!run(false,q,2048,2,4,4.008485,t).allows());
        check(run(false,q,2048,2,4,4.008486,t).allows());
        l.record("OPENAI",-1,-1,2,4);
        e=run(false,q,2048,2,4,100,t);
        check(e.state==CloudRequestAdvice.State.UNKNOWN_USAGE&&!e.allows()&&e.projected==null&&e.unpriced==1);
        check(run(false,q,2048,2,4,-1,t).allows());
        check(run(false,q,2048,2,4,-1,t).projected==null);
        check(!run(false,q,2048,2,4,1,null).allows());
        check(run(false,q,2048,2,4,1,l.get("GEMINI")).allows());
        e=run(true,q,2048,-1,-1,0,t);
        check(e.state==CloudRequestAdvice.State.LAN&&e.allows()&&e.requestCost==null&&e.limit==null);
        check(!run(true,question(""),2048,-1,-1,-1,t).allows());
        check(CloudRequestAdvice.validRate(1000000)&&!CloudRequestAdvice.validRate(Double.POSITIVE_INFINITY));
        check(CloudRequestAdvice.validBudget(-1)&&!CloudRequestAdvice.validBudget(-0.1));
        System.out.println("PASS: "+n+" bounded API preflight, Unicode and spending assertions");
    }
}
