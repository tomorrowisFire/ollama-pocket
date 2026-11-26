package com.ollamapocket.app;
public final class WorkflowPolicyTest {
    private static int n;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(String value){try{WorkflowPolicy.parse(value);throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){n++;}}
    public static void main(String[] args){
        java.util.List<WorkflowPolicy.Step> p=WorkflowPolicy.parse("Draft | READY\nReview | OK");
        ok(p.size()==2);ok(p.get(0).prompt.equals("Draft"));ok(WorkflowPolicy.verified(p.get(0),"READY now"));
        ok(!WorkflowPolicy.verified(p.get(0),"ready"));ok(!WorkflowPolicy.verified(p.get(1),""));
        ok(!WorkflowPolicy.verified(p.get(0),"READY"+"x".repeat(4000)));
        rejects("");rejects("no gate");rejects("prompt | ");rejects(" | gate");rejects("a|b\n".repeat(11));
        ok(WorkflowPolicy.parse("a|b\n".repeat(10)).size()==10);
        ok(WorkflowPolicy.canAttempt(0,100,100));ok(WorkflowPolicy.canAttempt(2,100,100));
        ok(!WorkflowPolicy.canAttempt(3,100,100));ok(!WorkflowPolicy.canAttempt(0,99,100));
        ok(!WorkflowPolicy.canAttempt(0,100+WorkflowPolicy.MAX_MILLIS,100));
        ok(WorkflowPolicy.prompt(p.get(1),"READY now",true).contains("READY now"));
        ok(WorkflowPolicy.prompt(p.get(1),"",true).contains("failed the literal"));
        WorkflowPolicy.Step exact=WorkflowPolicy.parse("p | @exact:OK").get(0);
        ok(WorkflowPolicy.verified(exact," OK "));ok(!WorkflowPolicy.verified(exact,"OK not exact"));
        WorkflowPolicy.Step chars=WorkflowPolicy.parse("p | @minchars:3").get(0);
        ok(WorkflowPolicy.verified(chars,"가나다"));ok(!WorkflowPolicy.verified(chars,"ab"));
        WorkflowPolicy.Step json=WorkflowPolicy.parse("p | @jsonkeys:a,b").get(0);
        ok(WorkflowPolicy.verified(json,"{\"a\":1,\"b\":false}"));
        ok(!WorkflowPolicy.verified(json,"{\"a\":1}"));ok(!WorkflowPolicy.verified(json,"{\"a\":1,\"b\":null}"));
        ok(!WorkflowPolicy.verified(json,"{\"a\":1,\"b\":2} trailing"));
        rejects("p | @minchars:0");rejects("p | @minchars:9999");rejects("p | @jsonkeys:a,a");rejects("p | @unknown:x");
        ok(WorkflowPolicy.verified(WorkflowPolicy.parse("p | @exact:OK",1).get(0),"@exact:OK"));
        System.out.println("PASS: "+n+" workflow policy assertions");
    }
}
