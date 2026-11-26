package com.ollamapocket.app;
import org.json.JSONArray;
import org.json.JSONObject;
public final class WorkflowLibraryTest {
    private static int n;
    private interface Attempt {void run()throws Exception;}
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(Attempt attempt)throws Exception{
        boolean failed=false;try{attempt.run();}catch(Exception e){failed=true;}ok(failed);
    }
    private static String snapshot(String id)throws Exception{return new JSONObject().put("id",id).put("schema",2).put("status","COMPLETED").toString();}
    public static void main(String[] args)throws Exception{
        ok(WorkflowLibrary.templates(null).isEmpty());
        String plan="Say READY | @exact:READY";
        String raw=WorkflowLibrary.saveTemplate(null,"t1"," Draft ",plan,1);
        ok(WorkflowLibrary.templates(raw).get(0).name.equals("Draft"));
        ok(WorkflowLibrary.templates(raw).get(0).plan.equals(plan));
        rejects(()->WorkflowLibrary.saveTemplate(raw,"t2","draft",plan,2));
        rejects(()->WorkflowLibrary.saveTemplate(raw,"t1","Other",plan,2));
        rejects(()->WorkflowLibrary.saveTemplate(raw,"t2","Other","invalid",2));
        rejects(()->WorkflowLibrary.saveTemplate(raw,"t2","x\nx",plan,2));
        ok(WorkflowLibrary.templates(WorkflowLibrary.deleteTemplate(raw,"t1")).isEmpty());
        rejects(()->WorkflowLibrary.deleteTemplate(raw,"missing"));
        String full=null;for(int i=0;i<12;i++)full=WorkflowLibrary.saveTemplate(full,"id"+i,"Name "+i,plan,i+1);
        ok(WorkflowLibrary.templates(full).size()==12);
        final String bounded=full;rejects(()->WorkflowLibrary.saveTemplate(bounded,"overflow","Overflow",plan,20));
        ok(WorkflowLibrary.history(null).isEmpty());
        String one=WorkflowLibrary.archive(null,snapshot("r1"),10);
        ok(WorkflowLibrary.history(one).get(0).snapshot.equals(snapshot("r1")));
        ok(WorkflowLibrary.history(one).get(0).archived==10);
        ok(WorkflowLibrary.history(WorkflowLibrary.archive(one,snapshot("r1"),20)).size()==1);
        rejects(()->WorkflowLibrary.archive(one,new JSONObject(snapshot("r1")).put("changed",true).toString(),20));
        String history=one;for(int i=2;i<=10;i++)history=WorkflowLibrary.archive(history,snapshot("r"+i),10+i);
        ok(WorkflowLibrary.history(history).size()==8);
        ok(WorkflowLibrary.history(history).get(0).id.equals("r10"));
        ok(WorkflowLibrary.history(history).get(7).id.equals("r3"));
        ok(WorkflowLibrary.history(WorkflowLibrary.deleteHistory(history,"r10")).size()==7);
        JSONArray broken=new JSONArray(one);broken.getJSONObject(0).put("digest","bad");
        rejects(()->WorkflowLibrary.history(broken.toString()));
        rejects(()->WorkflowLibrary.history(one+" trailing"));
        rejects(()->WorkflowLibrary.archive(null,"x".repeat(180001),2));
        System.out.println("PASS: "+n+" workflow library assertions");
    }
}
