package com.ollamapocket.app;
import java.util.List;
public final class KnowledgeWorkspacePolicyTest {
    private static int n;
    private static void ok(boolean v){if(!v)throw new AssertionError("review "+n);n++;}
    private static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException e){n++;return;}throw new AssertionError("invalid accepted");}
    public static void main(String[] args)throws Exception{
        KnowledgeLibrary.State empty=KnowledgeLibrary.empty();
        String exact="  한국語 😀 中文\n  exact  ";
        KnowledgeWorkspacePolicy.Review review=KnowledgeWorkspacePolicy.review(empty,"source","  title  ",exact);
        ok(empty.sources.isEmpty());ok(review.id.equals("source"));ok(review.title.equals("title"));ok(review.text.equals(exact));
        KnowledgeLibrary.State saved=review.apply(empty);
        ok(saved.revision==1&&saved.source("source").text.equals(exact));
        List<KnowledgeLibrary.Hit> hits=KnowledgeLibrary.search(saved,"😀");
        ok(hits.size()==1&&hits.get(0).start==exact.indexOf("😀")+1&&hits.get(0).end==exact.indexOf("😀")+2);
        rejects(()->review.apply(saved));rejects(()->KnowledgeWorkspacePolicy.review(empty,"a"," ","x"));
        rejects(()->KnowledgeWorkspacePolicy.review(empty,"a","title","  "));
        rejects(()->KnowledgeWorkspacePolicy.review(empty,"a","title","x".repeat(32001)));
        rejects(()->KnowledgeWorkspacePolicy.review(empty,"a","title",String.valueOf((char)0xd800)));
        rejects(()->KnowledgeWorkspacePolicy.review(empty,"a","x".repeat(81),"x"));
        KnowledgeLibrary.State full=empty;
        for(int i=0;i<KnowledgeLibrary.MAX_SOURCES;i++)full=KnowledgeLibrary.add(full,"s"+i,"title","text");
        final KnowledgeLibrary.State cap=full;rejects(()->KnowledgeWorkspacePolicy.review(cap,"new","title","text"));
        ok(KnowledgeWorkspacePolicy.fitsEdit(31999,0,1,32000));
        ok(!KnowledgeWorkspacePolicy.fitsEdit(31999,0,2,32000));
        ok(KnowledgeWorkspacePolicy.fitsEdit(32000,2,2,32000));
        ok(!KnowledgeWorkspacePolicy.fitsEdit(0,1,0,32000));
        ok(!KnowledgeWorkspacePolicy.fitsEdit(Integer.MAX_VALUE,0,Integer.MAX_VALUE,32000));
        ok(!KnowledgeWorkspacePolicy.fitsEdit(0,0,-1,32000));
        byte[][] bytes={null};boolean[] fail={false};
        ProjectTransactions<KnowledgeLibrary.State> tx=new ProjectTransactions<>(new ProjectTransactions.Backend(){
            public byte[] read(){return bytes[0];}
            public void write(byte[] b)throws java.io.IOException{if(fail[0])throw new java.io.IOException();bytes[0]=b;}
        },KnowledgeLibrary.CODEC,new Object(),empty,KnowledgeLibrary.MAX_BYTES);
        ProjectTransactions.Snapshot<KnowledgeLibrary.State> zero=tx.read();
        ProjectTransactions.Snapshot<KnowledgeLibrary.State> committed=tx.commit(zero,review::apply);
        ok(committed.value.source("source").text.equals(exact));
        try{tx.commit(zero,review::apply);throw new AssertionError();}catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.STALE);}
        try{tx.commit(committed,st->st);throw new AssertionError();}catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.NO_CHANGE);}
        fail[0]=true;
        try{tx.commit(committed,st->KnowledgeLibrary.remove(st,"source"));throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.WRITE_UNCERTAIN);}
        ok(tx.read().value.source("source").text.equals(exact));
        ProjectWorkspacePolicy.Session session=new ProjectWorkspacePolicy.Session();
        long token=session.open();ok(session.valid(token));session.close();ok(!session.valid(token));ok(!session.valid(session.open()-2));
        System.out.println("PASS: "+n+" manual knowledge review, input limits, exact search, transactions and lifecycle assertions");
    }
}
