package com.ollamapocket.app;

public final class WorkflowReadinessTest {
    private static int n;
    private static void ok(boolean v){if(!v)throw new AssertionError("check "+n);n++;}
    private static void samples(String line,String good,String bad,int format){
        WorkflowPolicy.Step original=WorkflowPolicy.parse(line,format).get(0);
        WorkflowPlanEditor editor=new WorkflowPlanEditor(line,format);
        WorkflowPlanEditor.Row row=editor.row(0);
        for(String value:new String[]{good,bad,"",null,"x".repeat(4001)}){
            for(boolean truncated:new boolean[]{false,true}){
                WorkflowPolicy.Sample expected=WorkflowPolicy.sample(original,value,truncated);
                ok(WorkflowPlanEditor.sample(row,value,truncated)==expected);
                ok((expected==WorkflowPolicy.Sample.MATCH)==
                    (WorkflowPolicy.decide(original,value,truncated,1)==WorkflowPolicy.Decision.ADVANCE));
                ok((expected==WorkflowPolicy.Sample.MATCH)==
                    (WorkflowPolicy.decide(original,value,truncated,3)==WorkflowPolicy.Decision.ADVANCE));
            }
        }
        ok(WorkflowPlanEditor.sample(row,good,false)==WorkflowPolicy.Sample.MATCH);
        ok(WorkflowPlanEditor.sample(row,bad,false)==WorkflowPolicy.Sample.MISMATCH);
        ok(WorkflowPlanEditor.sample(row,good,true)==WorkflowPolicy.Sample.TRUNCATED);
        ok(WorkflowPlanEditor.sample(row," ",false)==WorkflowPolicy.Sample.EMPTY);
        ok(WorkflowPlanEditor.sample(row,"x".repeat(4001),false)==WorkflowPolicy.Sample.OVERSIZED);
    }
    private static WorkflowPolicy.Budget budget(String state,int steps,int completed,int attempts,int total,long now,long updated){
        return WorkflowPolicy.budget(state,steps,completed,attempts,total,1000,updated,now);
    }
    public static void main(String[] args){
        samples("draft | @contains:OK","OK now","ok",2);
        samples("draft | @exact:완료"," 완료 ","완료됨",2);
        samples("draft | @minchars:2","한😀","😀",2);
        samples("draft | @jsonkeys:x","{\"x\":false}","{\"x\":null}",2);
        samples("draft | @jsonfields:x:integer","{\"x\":1}","{\"x\":1.0}",2);
        samples("draft | @jsonvalues:{\"count\":3}","{\"count\":3}","{\"count\":3.0}",2);
        samples("legacy | @exact:OK","literal @exact:OK","OK",1);
        ok(WorkflowPlanEditor.sample(null,"x",false)==WorkflowPolicy.Sample.INVALID_CONDITION);
        for(WorkflowPlanEditor.Row row:new WorkflowPlanEditor.Row[]{
            new WorkflowPlanEditor.Row("",WorkflowPolicy.Gate.EXACT,"OK"),
            new WorkflowPlanEditor.Row("p",WorkflowPolicy.Gate.MIN_CHARS,"0"),
            new WorkflowPlanEditor.Row("p",WorkflowPolicy.Gate.JSON_VALUES,"{\"x\":null}"),
            new WorkflowPlanEditor.Row("p",WorkflowPolicy.Gate.JSON_FIELDS,"x:unknown"),
            new WorkflowPlanEditor.Row("p",null,"OK")
        })ok(WorkflowPlanEditor.sample(row,"OK",false)==WorkflowPolicy.Sample.INVALID_CONDITION);
        WorkflowPlanEditor ed=new WorkflowPlanEditor("p | @exact:OK",2);
        String before=ed.encode();ok(WorkflowPlanEditor.sample(ed.row(0),"OK",false)==WorkflowPolicy.Sample.MATCH);
        ok(ed.encode().equals(before));
        long end=1000+WorkflowPolicy.MAX_MILLIS;
        WorkflowPolicy.Budget first=budget("QUEUED",10,0,0,0,1000,1000);
        ok(first.allowed()&&first.stepAttempts==3&&first.totalAttempts==30&&first.millis==WorkflowPolicy.MAX_MILLIS);
        WorkflowPolicy.Budget near=budget("PAUSED",10,9,2,29,end-1,end-2);
        ok(near.allowed()&&near.stepAttempts==1&&near.totalAttempts==1&&near.millis==1);
        ok(budget("PAUSED",10,9,2,29,end,end-2).state==WorkflowPolicy.BudgetState.EXPIRED);
        ok(budget("PAUSED",10,0,0,0,999,0).state==WorkflowPolicy.BudgetState.CLOCK);
        ok(budget("PAUSED",10,1,0,1,1100,1200).state==WorkflowPolicy.BudgetState.CLOCK);
        ok(budget("PAUSED",10,1,0,1,1200,1200).allowed());
        ok(budget("BLOCKED",10,1,3,4,1200,1100).state==WorkflowPolicy.BudgetState.ATTEMPTS);
        ok(budget("BLOCKED",10,9,2,30,1200,1100).stepAttempts==0);
        ok(!budget("BLOCKED",10,9,2,30,1200,1100).allowed());
        ok(budget("COMPLETED",10,10,0,10,1200,1100).state==WorkflowPolicy.BudgetState.COMPLETE);
        ok(budget("CANCELLED",10,1,0,1,1200,1100).state==WorkflowPolicy.BudgetState.CANCELLED);
        ok(budget("COMPLETED",10,9,0,9,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",10,10,0,10,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",0,0,0,0,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",11,0,0,0,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",10,0,-1,0,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",10,0,4,0,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",10,0,0,-1,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",10,0,0,31,1200,1100).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",10,0,0,0,1200,900).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("UNKNOWN",10,0,0,0,1200,0).state==WorkflowPolicy.BudgetState.INVALID);
        ok(budget("PAUSED",10,1,0,0,1200,0).allowed()); // legacy stats incomplete; no invented usage
        ok(WorkflowPolicy.reviewedResume("snapshot","snapshot","p","p","m","m"));
        ok(!WorkflowPolicy.reviewedResume("snapshot","changed","p","p","m","m"));
        ok(!WorkflowPolicy.reviewedResume("snapshot","snapshot","p","changed","m","m"));
        ok(!WorkflowPolicy.reviewedResume("snapshot","snapshot","p","p","m","other"));
        ok(!WorkflowPolicy.reviewedResume(null,null,"p","p","m","m"));
        ok(!WorkflowPolicy.reviewedResume("","","p","p","m","m"));
        ok(WorkflowPolicy.resumeChoiceEnabled(true,true,"plan","plan","m","m"));
        ok(!WorkflowPolicy.resumeChoiceEnabled(false,true,"plan","plan","m","m"));
        ok(!WorkflowPolicy.resumeChoiceEnabled(true,false,"plan","plan","m","m"));
        ok(!WorkflowPolicy.resumeChoiceEnabled(true,true,"plan","changed","m","m"));
        ok(!WorkflowPolicy.resumeChoiceEnabled(true,true,"plan","plan","m","other"));
        ok(!WorkflowPolicy.resumeChoiceEnabled(true,true,null,null,"m","m"));
        ok(!WorkflowPolicy.resumeChoiceEnabled(true,true,"plan","plan","",""));
        System.out.println("PASS: "+n+" workflow sample equivalence, budget and reviewed resume assertions");
    }
}
