package com.ollamapocket.app;
import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public final class KnowledgeLibraryTest {
    private static int n;
    private static void ok(boolean b){if(!b)throw new AssertionError("knowledge "+n);n++;}
    interface Action{void run()throws Exception;}
    private static void reject(Action a)throws Exception{
        try{a.run();}catch(IllegalArgumentException|IOException e){n++;return;}
        throw new AssertionError("invalid accepted");
    }
    private static byte[] seal(byte[] payload)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(payload);
        out.write(MessageDigest.getInstance("SHA-256").digest(payload));return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        KnowledgeLibrary.State empty=KnowledgeLibrary.empty();
        ok(empty.revision==0&&empty.sources.isEmpty());
        KnowledgeLibrary.State a=KnowledgeLibrary.add(empty,"id1","한국어","😀한글한글 中文 日本語\nabcABC aaa");
        KnowledgeLibrary.State b=KnowledgeLibrary.add(a,"id2","日本語","한글\t😀");
        ok(a.sources.size()==1&&b.revision==2&&empty.sources.isEmpty());
        byte[] bytes=KnowledgeLibrary.encode(b);
        KnowledgeLibrary.State round=KnowledgeLibrary.decode(bytes);
        ok(Arrays.equals(bytes,KnowledgeLibrary.encode(round)));
        ok(round.source("id1").text.equals(a.source("id1").text));
        List<KnowledgeLibrary.Hit> hits=KnowledgeLibrary.search(round,"한글");
        ok(hits.size()==3&&hits.get(0).start==3&&hits.get(0).end==4);
        ok(hits.get(1).start==5&&hits.get(1).end==6&&hits.get(2).id.equals("id2"));
        for(KnowledgeLibrary.Hit h:hits)ok(h.match.equals(round.source(h.id).text.substring(h.start-1,h.end)));
        ok(KnowledgeLibrary.search(round,"😀").get(0).end==2);
        ok(KnowledgeLibrary.search(round,"中文").size()==1&&KnowledgeLibrary.search(round,"日本語").size()==1);
        ok(KnowledgeLibrary.search(round,"ABC").size()==1&&KnowledgeLibrary.search(round,"Abc").isEmpty());
        ok(KnowledgeLibrary.search(round,"aa").size()==1);
        ok(KnowledgeLibrary.search(round,"missing").isEmpty());
        KnowledgeLibrary.State many=KnowledgeLibrary.add(empty,"a","A","x".repeat(100));
        ok(KnowledgeLibrary.search(many,"x").size()==40&&KnowledgeLibrary.search(many,"x").get(39).end==40);
        reject(()->KnowledgeLibrary.search(b,""));reject(()->KnowledgeLibrary.search(b,"  "));
        reject(()->KnowledgeLibrary.search(b,"x".repeat(121)));
        reject(()->KnowledgeLibrary.search(b,String.valueOf((char)0xd800)));
        reject(()->KnowledgeLibrary.add(b,"id1","duplicate","text"));
        reject(()->KnowledgeLibrary.add(b,"bad id","title","text"));
        reject(()->KnowledgeLibrary.add(b,"","title","text"));
        reject(()->KnowledgeLibrary.add(b,"new"," ","text"));
        reject(()->KnowledgeLibrary.add(b,"new","Title","\u3000"));
        reject(()->KnowledgeLibrary.add(b,"new","Title",String.valueOf((char)0xdc00)));
        reject(()->KnowledgeLibrary.add(b,"new","Title","x".repeat(32001)));
        ok(KnowledgeLibrary.add(b,"max","Title","x".repeat(32000)).source("max").text.length()==32000);
        reject(()->KnowledgeLibrary.remove(b,"missing"));
        KnowledgeLibrary.State removed=KnowledgeLibrary.remove(b,"id1");
        ok(removed.sources.size()==1&&removed.revision==3&&b.sources.size()==2);
        try{b.sources.clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException e){n++;}
        try{hits.clear();throw new AssertionError("mutable hits");}catch(UnsupportedOperationException e){n++;}
        KnowledgeLibrary.State cap=empty;
        for(int i=0;i<24;i++)cap=KnowledgeLibrary.add(cap,"s"+i,"S","text");
        final KnowledgeLibrary.State full=cap;
        reject(()->KnowledgeLibrary.add(full,"overflow","S","text"));
        KnowledgeLibrary.State big=empty;
        for(int i=0;i<24;i++)big=KnowledgeLibrary.add(big,"big"+i,"S","한".repeat(32000));
        final KnowledgeLibrary.State oversized=big;
        reject(()->KnowledgeLibrary.encode(oversized));
        byte[] bad=bytes.clone();bad[20]^=1;reject(()->KnowledgeLibrary.decode(bad));
        reject(()->KnowledgeLibrary.decode(Arrays.copyOf(bytes,bytes.length-1)));
        reject(()->KnowledgeLibrary.decode(new byte[KnowledgeLibrary.MAX_BYTES+1]));
        reject(()->KnowledgeLibrary.decode(KnowledgeLibrary.encode(empty)));
        byte[] payload=Arrays.copyOf(bytes,bytes.length-32);
        byte[] schema=payload.clone();ByteBuffer.wrap(schema).putInt(4,2);reject(()->KnowledgeLibrary.decode(seal(schema)));
        byte[] count=payload.clone();ByteBuffer.wrap(count).putInt(16,25);reject(()->KnowledgeLibrary.decode(seal(count)));
        byte[] length=payload.clone();ByteBuffer.wrap(length).putInt(20,Integer.MAX_VALUE);reject(()->KnowledgeLibrary.decode(seal(length)));
        byte[] utf=payload.clone();utf[24]=(byte)0xff;reject(()->KnowledgeLibrary.decode(seal(utf)));
        reject(()->KnowledgeLibrary.decode(seal(Arrays.copyOf(payload,payload.length+1))));
        final byte[][] disk={null};final boolean[] fail={false};
        ProjectTransactions<KnowledgeLibrary.State> tx=new ProjectTransactions<>(new ProjectTransactions.Backend(){
            public byte[] read(){return disk[0];}
            public void write(byte[] b)throws IOException{if(fail[0])throw new IOException();disk[0]=b;}
        },KnowledgeLibrary.CODEC,new Object(),empty,KnowledgeLibrary.MAX_BYTES);
        ProjectTransactions.Snapshot<KnowledgeLibrary.State> zero=tx.read();
        ProjectTransactions.Snapshot<KnowledgeLibrary.State> saved=tx.commit(zero,s->KnowledgeLibrary.add(s,"a","A","hello"));
        ok(saved.revision==1&&saved.value.source("a").text.equals("hello"));
        try{tx.commit(zero,s->KnowledgeLibrary.add(s,"b","B","other"));throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.STALE);}
        try{tx.commit(saved,s->s);throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.NO_CHANGE);}
        fail[0]=true;
        try{tx.commit(saved,s->KnowledgeLibrary.remove(s,"a"));throw new AssertionError();}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.WRITE_UNCERTAIN);}
        ok(tx.read().value.source("a").text.equals("hello"));
        disk[0]=bad;reject(()->tx.read());
        System.out.println("PASS: "+n+" private knowledge snapshot, exact Unicode search, byte bounds and transaction assertions");
    }
}
