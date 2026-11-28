package com.ollamapocket.app;
import org.json.JSONArray;
public final class WorkflowRunJournalTest {
    private static int n;
    interface Action {void run()throws Exception;}
    static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    static void rejects(Action action)throws Exception{boolean bad=false;try{action.run();}catch(Exception e){bad=true;}ok(bad);}
    public static void main(String[] args)throws Exception{
        WorkflowRunJournal.Reason none=WorkflowRunJournal.Reason.NONE;
        InferenceSafetyPolicy.Reason safe=InferenceSafetyPolicy.Reason.NONE;
        ok(WorkflowRunJournal.stopReason(false,false,false,safe,true,true)==null);
        ok(WorkflowRunJournal.stopReason(true,true,true,safe,false,false)==WorkflowRunJournal.Reason.USER_CANCEL);
        ok(WorkflowRunJournal.stopReason(false,true,true,safe,false,false)==WorkflowRunJournal.Reason.USER_PAUSE);
        ok(WorkflowRunJournal.stopReason(false,false,true,safe,true,true)==WorkflowRunJournal.Reason.TIME_LIMIT);
        ok(WorkflowRunJournal.stopReason(false,false,false,InferenceSafetyPolicy.Reason.THERMAL,true,true)==WorkflowRunJournal.Reason.THERMAL);
        ok(WorkflowRunJournal.stopReason(false,false,false,InferenceSafetyPolicy.Reason.LOW_MEMORY,true,true)==WorkflowRunJournal.Reason.MEMORY);
        ok(WorkflowRunJournal.stopReason(false,false,false,safe,false,true)==WorkflowRunJournal.Reason.SERVER_OFF);
        ok(WorkflowRunJournal.stopReason(false,false,false,safe,true,false)==WorkflowRunJournal.Reason.COOP_ACTIVE);
        ok(WorkflowRunJournal.stopState(WorkflowRunJournal.Reason.RECOVERED)==WorkflowRunJournal.State.PAUSED);
        ok(WorkflowRunJournal.stopState(WorkflowRunJournal.Reason.USER_CANCEL)==WorkflowRunJournal.State.CANCELLED);
        ok(WorkflowRunJournal.stopState(WorkflowRunJournal.Reason.TRANSPORT)==WorkflowRunJournal.State.BLOCKED);
        ok(WorkflowRunJournal.interruptedState("RUNNING")&&WorkflowRunJournal.interruptedState("VERIFYING")&&!WorkflowRunJournal.interruptedState("COMPLETED"));
        ok(WorkflowRunJournal.failure(new java.io.IOException("WORKFLOW_OUTPUT_LIMIT"))==WorkflowRunJournal.Reason.OUTPUT_LIMIT);
        ok(WorkflowRunJournal.failure(new java.io.IOException("WORKFLOW_INVALID_TOKENS"))==WorkflowRunJournal.Reason.INVALID_TOKENS);
        ok(WorkflowRunJournal.failure(new java.io.IOException("CHAT_STREAM_INVALID_TOKENS"))==WorkflowRunJournal.Reason.INVALID_TOKENS);
        ok(WorkflowRunJournal.failure(new java.io.IOException("CHAT_STREAM_LIMIT"))==WorkflowRunJournal.Reason.OUTPUT_LIMIT);
        ok(WorkflowRunJournal.failure(new java.io.IOException("private payload"))==WorkflowRunJournal.Reason.TRANSPORT);
        ok(WorkflowRunJournal.failure(new InterruptedException())==WorkflowRunJournal.Reason.INTERRUPTED);
        ok(WorkflowRunJournal.failure(new java.io.InterruptedIOException())==WorkflowRunJournal.Reason.INTERRUPTED);
        ok(WorkflowRunJournal.failure(new java.net.SocketTimeoutException())==WorkflowRunJournal.Reason.TRANSPORT);
        ok(WorkflowRunJournal.failure(null)==WorkflowRunJournal.Reason.TRANSPORT);
        ok(WorkflowRunJournal.stopState(WorkflowRunJournal.Reason.INTERRUPTED)==WorkflowRunJournal.State.PAUSED);
        ok(WorkflowRunJournal.stopState(WorkflowRunJournal.Reason.CONTEXT_LIMIT)==WorkflowRunJournal.State.BLOCKED);
        WorkflowRunJournal contextBlocked=new WorkflowRunJournal();
        contextBlocked.record(1,"QUEUED",none,0,0,0,2,1);
        contextBlocked.record(2,"BLOCKED",WorkflowRunJournal.Reason.CONTEXT_LIMIT,0,0,0,2,1);
        ok(WorkflowRunJournal.read(contextBlocked.json(),2,1).matchesLast("BLOCKED",WorkflowRunJournal.Reason.CONTEXT_LIMIT,0,0,0,2));
        WorkflowRunJournal j=new WorkflowRunJournal();
        ok(j.matchesLast("PAUSED",WorkflowRunJournal.Reason.UNKNOWN,0,0,0,0));
        j.record(1,"QUEUED",none,0,0,0,2,1);
        j.record(2,"RUNNING",none,0,1,1,2,1);
        j.record(3,"PAUSED",WorkflowRunJournal.Reason.RECOVERED,0,1,1,2,1);
        j.record(4,"RUNNING",none,0,2,2,2,1);
        j.record(5,"VERIFYING",none,0,2,2,2,1);
        j.record(6,"QUEUED",WorkflowRunJournal.Reason.VERIFIED,1,0,2,2,1);
        ok(j.events().size()==6);
        j.record(7,"QUEUED",WorkflowRunJournal.Reason.VERIFIED,1,0,2,2,1);
        ok(j.events().size()==6);
        WorkflowRunJournal copy=WorkflowRunJournal.read(j.json(),2,1);
        ok(copy.matchesLast("QUEUED",WorkflowRunJournal.Reason.VERIFIED,1,0,2,7));
        ok(!copy.matchesLast("QUEUED",WorkflowRunJournal.Reason.VERIFIED,0,0,2,7));
        rejects(()->j.record(8,"RUNNING",none,0,1,3,2,1));
        rejects(()->j.record(8,"COMPLETED",WorkflowRunJournal.Reason.VERIFIED,1,0,2,2,1));
        rejects(()->j.record(1,"RUNNING",none,1,1,3,2,1));
        j.record(8,"RUNNING",none,1,1,3,2,1);
        j.record(9,"COMPLETED",WorkflowRunJournal.Reason.VERIFIED,2,0,3,2,1);
        rejects(()->j.record(10,"RUNNING",none,2,1,4,2,1));
        ok(WorkflowRunJournal.read(j.json(),2,1).events().size()==8);
        JSONArray invalid=j.json();invalid.getJSONObject(0).put("reason","UNKNOWN_CODE");
        rejects(()->WorkflowRunJournal.read(invalid,2,1));
        WorkflowRunJournal bounded=new WorkflowRunJournal();
        for(int i=0;i<120;i++)bounded.record(i+1,i%2==0?"RUNNING":"PAUSED",i%2==0?none:WorkflowRunJournal.Reason.USER_PAUSE,0,1,1,2,1);
        ok(bounded.events().size()==96&&bounded.events().get(0).at==25);
        ok(WorkflowRunJournal.read(bounded.json(),2,1).events().size()==96);
        System.out.println("PASS: "+n+" workflow journal and stop-policy assertions");
    }
}
