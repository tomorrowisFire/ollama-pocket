package com.ollamapocket.app;
public final class ProjectWorkspacePolicyTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("workspace check "+n);n++;}
    public static void main(String[] args)throws Exception{
        ProjectLibrary.State s=ProjectLibrary.createProject(ProjectLibrary.empty(),"p","P","");
        s=ProjectLibrary.createProject(s,"q","Q","");
        s=ProjectLibrary.createArtifact(s,"p","a","A","first",10);
        s=ProjectLibrary.createArtifact(s,"q","b","B","unrelated",10);
        s=ProjectLibrary.editArtifact(s,"p","a","second",20);
        ProjectWorkspacePolicy.Selection x=new ProjectWorkspacePolicy.Selection("p","a",1);
        ok(x.resolve(s).ordinal==1);ok(x.parent().ordinal==0);ok(x.parent().parent().artifact==null);
        ok(x.parent().parent().parent().project==null);
        ok(new ProjectWorkspacePolicy.Selection("p","b",1).resolve(s).artifact==null);
        ok(new ProjectWorkspacePolicy.Selection("missing","a",1).resolve(s).project==null);
        ok(new ProjectWorkspacePolicy.Selection("p","a",9).resolve(s).ordinal==0);
        ok(x.resolve(ProjectLibrary.deleteArtifact(s,"p","a")).artifact==null);
        ok(x.resolve(ProjectLibrary.deleteProject(s,"p")).project==null);
        ProjectLibrary.Artifact a=s.project("p").artifact("a");
        ok(ProjectWorkspacePolicy.canAppend(a));ok(ProjectWorkspacePolicy.canRestore(a,1));
        ok(!ProjectWorkspacePolicy.canRestore(a,0));ok(!ProjectWorkspacePolicy.canRestore(a,2));
        ok(!ProjectWorkspacePolicy.canRestore(a,3));ok(ProjectWorkspacePolicy.savedAt(a,1)==20);
        ok(ProjectWorkspacePolicy.savedAt(a,30)==30);
        s=ProjectLibrary.restoreArtifact(s,"p","a",1,20);
        a=s.project("p").artifact("a");
        ok(a.current().ordinal==3&&a.current().text.equals("first")&&a.version(2).text.equals("second"));
        ok(!ProjectWorkspacePolicy.canRestore(a,1));ok(ProjectWorkspacePolicy.canRestore(a,2));
        s=ProjectLibrary.editArtifact(s,"p","a","four",21);
        s=ProjectLibrary.editArtifact(s,"p","a","five",22);
        s=ProjectLibrary.editArtifact(s,"p","a","six",23);
        a=s.project("p").artifact("a");
        ok(!ProjectWorkspacePolicy.canAppend(a));ok(!ProjectWorkspacePolicy.canRestore(a,1));
        ok(ProjectLibrary.editArtifact(s,"p","a","six",23)==s);
        ok(!ProjectWorkspacePolicy.dirty("A","T","A","T"));ok(ProjectWorkspacePolicy.dirty("A","T","A","T "));
        ok(ProjectWorkspacePolicy.dirty("A","T","B","T"));
        ok(ProjectWorkspacePolicy.failure(new ProjectTransactions.TransactionFailure(ProjectTransactions.Failure.NO_CHANGE,null))==ProjectWorkspacePolicy.Outcome.NO_CHANGE);
        ok(ProjectWorkspacePolicy.failure(new ProjectTransactions.TransactionFailure(ProjectTransactions.Failure.INVALID,null))==ProjectWorkspacePolicy.Outcome.INVALID);
        ok(ProjectWorkspacePolicy.failure(new ProjectTransactions.TransactionFailure(ProjectTransactions.Failure.STALE,null))==ProjectWorkspacePolicy.Outcome.STALE);
        ok(ProjectWorkspacePolicy.failure(new ProjectTransactions.TransactionFailure(ProjectTransactions.Failure.WRITE_UNCERTAIN,null))==ProjectWorkspacePolicy.Outcome.UNCERTAIN);
        for(ProjectTransactions.Failure f:ProjectTransactions.Failure.values()){
            ProjectWorkspacePolicy.Outcome o=ProjectWorkspacePolicy.failure(new ProjectTransactions.TransactionFailure(f,null));
            ok(ProjectWorkspacePolicy.reloadRequired(o)==(f==ProjectTransactions.Failure.STALE||f==ProjectTransactions.Failure.WRITE_UNCERTAIN));
        }
        ok(ProjectWorkspacePolicy.failure(new java.io.IOException())==ProjectWorkspacePolicy.Outcome.UNREADABLE);
        ok(ProjectWorkspacePolicy.reloadRequired(ProjectWorkspacePolicy.Outcome.UNREADABLE));
        ProjectWorkspacePolicy.Session session=new ProjectWorkspacePolicy.Session();
        ok(!session.valid(0));long first=session.open();ok(session.valid(first));session.close();ok(!session.valid(first));
        long next=session.open();ok(session.valid(next)&&!session.valid(first));session.close();ok(!session.valid(next));
        final byte[][] stored={null};
        ProjectTransactions<ProjectLibrary.State> tx=new ProjectTransactions<>(new ProjectTransactions.Backend(){
            public byte[] read(){return stored[0];}
            public void write(byte[] bytes){stored[0]=bytes;}
        },ProjectLibrary.CODEC,new Object(),ProjectLibrary.empty(),ProjectLibrary.MAX_BYTES);
        ProjectTransactions.Snapshot<ProjectLibrary.State> empty=tx.read();
        ok(empty.storedByteCount()==0);
        ProjectTransactions.Snapshot<ProjectLibrary.State> saved=tx.commit(empty,st->ProjectLibrary.createProject(st,"id","Title",""));
        ok(saved.storedByteCount()==stored[0].length&&saved.storedByteCount()>0);
        ok(empty.storedByteCount()==0);
        try{tx.commit(empty,st->ProjectLibrary.createProject(st,"other","Other",""));throw new AssertionError("stale write");}
        catch(ProjectTransactions.TransactionFailure e){ok(ProjectWorkspacePolicy.failure(e)==ProjectWorkspacePolicy.Outcome.STALE);}
        System.out.println("PASS: "+n+" project workspace selection, limits, dirty, lifecycle and reviewed action assertions");
    }
}
