package com.ollamapocket.app;

public final class CloudUsageLedgerTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        CloudUsageLedger l=new CloudUsageLedger();
        check(l.allows("A",1,2,4));
        check(!l.allows("A",0,2,4));
        check(!l.allows("A",1,-1,4));
        check(!l.allows("A",1,Double.NaN,4));
        l.record("A",1000000,500000,2,4);
        CloudUsageLedger.Totals t=l.get("A");
        check(t.input==1000000&&t.output==500000&&t.requests==1);
        check(Math.abs(t.estimated-4)<1e-9);
        check(!l.allows("A",4,2,4));
        check(l.allows("A",4.01,2,4));
        check(l.allows("B",1,2,4));
        l.record("A",-1,10,2,4);
        check(t.unknown==1&&t.unpriced==1);
        check(!l.allows("A",100,2,4));
        check(l.allows("A",-1,2,4));
        l.record("B",1,2,-1,4);
        check(l.get("B").unpriced==1);
        l.clear();
        check(l.get("A").requests==0);
        for(int i=0;i<10;i++)l.record("A",100000,0,0.1,0);
        check(l.get("A").exactCost().compareTo(new java.math.BigDecimal("0.1"))==0);
        check(!l.allows("A",0.1,0.1,0));
        check(l.allows("A",0.100001,0.1,0));
        check(!l.allows("A",-2,0.1,0));
        l.record("A",1,1,1000001,0);
        check(l.get("A").unpriced==1&&!l.allows("A",1,0,0));
        System.out.println("PASS: 19 cloud session budget checks");
    }
}
