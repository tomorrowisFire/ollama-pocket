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
        System.out.println("PASS: "+n+" workflow policy assertions");
    }
}
