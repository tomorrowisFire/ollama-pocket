package com.ollamapocket.app;
import java.util.List;
import org.json.JSONObject;
public final class ProjectReportHandoffTest {
    private static int n;
    private static void ok(boolean v){if(!v)throw new AssertionError("handoff "+n);n++;}
    private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException e){n++;return;}throw new AssertionError("accepted invalid input");}
    public static void main(String[] args)throws Exception{
        DocumentBatchPolicy.Plan plan=new DocumentBatchPolicy.Plan("Goal",List.of(
            new DocumentBatchPolicy.Source("source.txt","PRIVATE source",DocumentBatchPolicy.Kind.TEXT,false,0,0,0)));
        DocumentBatchPolicy.Result result=DocumentBatchPolicy.analyze(plan,(prompt,chars,tokens)->"Note 😀 [D1-S1]",()->false,x->{});
        DocumentBatchReport doc=new DocumentBatchReport(plan,result,"model",null,2,10);
        ProjectReportHandoff h=ProjectReportHandoff.of(doc);
        ok(h.json.equals(doc.json())&&h.status.equals("COMPLETED"));
        ok(!new JSONObject(h.json).getBoolean("factual_verification"));
        ok(!h.json.contains("PRIVATE source"));
        DocumentBatchReport stopped=new DocumentBatchReport(plan,new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.STOPPED,List.of(),0,""),"model","USER",0,0);
        ProjectReportHandoff partial=ProjectReportHandoff.of(stopped);
        ok(partial.status.equals("STOPPED")&&partial.json.equals(stopped.json()));
        ConferencePolicy.Request req=new ConferencePolicy.Request("goal","model","en",WorkflowPolicy.Gate.CONTAINS,"OK");
        ConferencePolicy.Result bad=ConferencePolicy.run(req,call->new ConferencePolicy.Reply("not JSON",false),()->false,x->{});
        ConferenceReport failed=new ConferenceReport(req,bad,null,20);
        ProjectReportHandoff conference=ProjectReportHandoff.of(failed);
        ok(conference.status.equals("FAILED")&&conference.json.equals(failed.json()));
        ok(!new JSONObject(conference.json).getBoolean("independent_verification"));
        ProjectReportHandoff.checkSize("x".repeat(32000));n++;
        reject(()->ProjectReportHandoff.checkSize("x".repeat(32001)));
        reject(()->ProjectReportHandoff.checkSize(null));
        reject(()->ProjectReportHandoff.checkSize(""));
        reject(()->ProjectReportHandoff.checkSize(String.valueOf((char)0xd800)));
        reject(()->ProjectReportHandoff.checkSize(String.valueOf((char)0xdc00)));
        ProjectLibrary.State empty=ProjectLibrary.empty();
        reject(()->h.create(empty,"p","a","Report",1));
        ProjectLibrary.State project=ProjectLibrary.createProject(empty,"p","P","");
        ProjectLibrary.State first=h.create(project,"p","a","Report",10);
        ok(first.project("p").artifact("a").current().text.equals(h.json));
        ok(h.append(first,"p","a",20)==first);
        ProjectLibrary.State next=partial.append(first,"p","a",1);
        ok(next.project("p").artifact("a").current().text.equals(partial.json));
        ok(next.project("p").artifact("a").version(1).text.equals(h.json));
        ok(next.project("p").artifact("a").current().saved==10);
        reject(()->conference.append(next,"other","a",10));
        reject(()->conference.append(next,"p","missing",10));
        ProjectLibrary.State full=next;
        for(int i=3;i<=6;i++)full=ProjectLibrary.editArtifact(full,"p","a","v"+i,20);
        final ProjectLibrary.State limit=full;
        reject(()->conference.append(limit,"p","a",30));
        ProjectLibrary.State cap=project;
        for(int i=0;i<ProjectLibrary.MAX_ARTIFACTS;i++)cap=ProjectLibrary.createArtifact(cap,"p","item"+i,"Item","",1);
        final ProjectLibrary.State max=cap;
        reject(()->h.create(max,"p","overflow","Report",1));
        final byte[][] bytes={null};final boolean[] fail={false};
        ProjectTransactions<ProjectLibrary.State> tx=new ProjectTransactions<>(new ProjectTransactions.Backend(){
            public byte[] read(){return bytes[0];}
            public void write(byte[] b)throws java.io.IOException{if(fail[0])throw new java.io.IOException();bytes[0]=b;}
        },ProjectLibrary.CODEC,new Object(),empty,ProjectLibrary.MAX_BYTES);
        ProjectTransactions.Snapshot<ProjectLibrary.State> zero=tx.read();
        ProjectTransactions.Snapshot<ProjectLibrary.State> reviewed=tx.commit(zero,st->ProjectLibrary.createProject(st,"p","P",""));
        ProjectTransactions.Snapshot<ProjectLibrary.State> saved=tx.commit(reviewed,st->h.create(st,"p","a","Report",1));
        ok(saved.value.project("p").artifact("a").current().text.equals(h.json));
        try{tx.commit(reviewed,st->conference.create(st,"p","b","Report",1));throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.STALE);}
        try{tx.commit(saved,st->h.append(st,"p","a",1));throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.NO_CHANGE);}
        fail[0]=true;
        try{tx.commit(saved,st->partial.append(st,"p","a",2));throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.WRITE_UNCERTAIN);}
        ok(tx.read().value.project("p").artifact("a").current().text.equals(h.json));
        System.out.println("PASS: "+n+" exact terminal report handoff, bounds, version and transaction assertions");
    }
}
