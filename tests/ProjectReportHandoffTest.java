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
        // Count actual generator invocations; a fitting singleton goes directly from note to final synthesis.
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        DocumentBatchPolicy.Result result=DocumentBatchPolicy.analyze(plan,(prompt,chars,tokens)->{
            requests.incrementAndGet();return "Note 😀 [D1-S1]";
        },()->false,x->{});
        ok(result.status==DocumentBatchPolicy.Status.COMPLETED&&result.notes.size()==1&&result.groupsDone==0);
        ok(requests.get()==2); // excerpt and final synthesis; no redundant group call
        reject(()->new DocumentBatchReport(plan,result,"model",null,requests.get()-1,10));
        reject(()->new DocumentBatchReport(plan,result,"model",null,requests.get()+1,10));
        DocumentBatchReport doc=new DocumentBatchReport(plan,result,"model",null,requests.get(),10);
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
        ProjectLibrary.State atomic=h.createProject(empty,"new-p","New project","new-a","Report",0);
        ok(atomic.revision==1&&empty.projects.isEmpty());
        ok(atomic.project("new-p").artifact("new-a").current().text.equals(h.json));
        ok(atomic.project("new-p").artifact("new-a").current().saved==1);
        ok(ProjectLibrary.decode(ProjectLibrary.encode(atomic)).project("new-p").artifact("new-a").current().text.equals(h.json));
        reject(()->h.createProject(empty,"same","Project","same","Report",1));
        reject(()->h.createProject(empty,"p"," ","a","Report",1));
        reject(()->h.createProject(atomic,"new-a","P","a","R",1));
        reject(()->h.createProject(atomic,"p","P","new-p","R",1));
        ProjectLibrary.State fullProjects=empty;
        for(int i=0;i<ProjectLibrary.MAX_PROJECTS;i++)fullProjects=ProjectLibrary.createProject(fullProjects,"full"+i,"Project","");
        final ProjectLibrary.State projectLimit=fullProjects;
        reject(()->h.createProject(projectLimit,"extra","P","a","R",1));
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
        fail[0]=false;
        ProjectTransactions.Snapshot<ProjectLibrary.State> beforeAtomic=tx.read();
        ProjectTransactions.Snapshot<ProjectLibrary.State> afterAtomic=tx.commit(beforeAtomic,st->partial.createProject(st,"p2","Second","a2","Partial",3));
        ok(afterAtomic.revision==beforeAtomic.revision+1);
        ok(afterAtomic.value.project("p2").artifact("a2").current().text.equals(partial.json));
        ok(afterAtomic.value.project("p").artifact("a").current().text.equals(h.json));
        try{tx.commit(afterAtomic,st->h.createProject(st,"p3","Third","a2","Report",4));throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.INVALID);}
        ok(tx.read().revision==afterAtomic.revision&&tx.read().value.projects.size()==2);
        System.out.println("PASS: "+n+" exact terminal report handoff, bounds, version and transaction assertions");
    }
}
