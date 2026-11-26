package com.ollamapocket.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

public final class ProjectLibraryTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("project check "+n);n++;}
    interface Action{void run()throws Exception;}
    private static void rejects(Action a)throws Exception{
        try{a.run();}catch(Exception e){n++;return;}
        throw new AssertionError("accepted invalid project "+n);
    }
    private static void fails(Action a,ProjectTransactions.Failure expected)throws Exception{
        try{a.run();}catch(ProjectTransactions.TransactionFailure e){ok(e.reason==expected);return;}
        throw new AssertionError("accepted invalid transaction "+n);
    }
    private static String hex(byte[] value){
        StringBuilder s=new StringBuilder();
        for(byte b:value)s.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));
        return s.toString();
    }
    private static byte[] pack(String payload)throws Exception{
        String hash=hex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        return ("{\"schema\":1,\"payload\":"+JSONObject.quote(payload)+",\"sha256\":\""+hash+"\"}").getBytes(StandardCharsets.UTF_8);
    }
    private static ProjectLibrary.State canonical(ProjectLibrary.State state)throws Exception{
        byte[] encoded=ProjectLibrary.encode(state);
        ProjectLibrary.State decoded=ProjectLibrary.decode(encoded);
        ok(decoded.revision==state.revision);
        ok(Arrays.equals(encoded,ProjectLibrary.encode(decoded)));
        return decoded;
    }
    private static ProjectLibrary.State setup()throws Exception{
        ProjectLibrary.State s=ProjectLibrary.createProject(ProjectLibrary.empty(),"p","  프로젝트 😀  ","instructions\n한글");
        s=ProjectLibrary.createProject(s,"other","Second","");
        s=ProjectLibrary.createArtifact(s,"p","a","Artifact","  v1 \"quote\" \\path\n😀\u0000\t  ",100);
        s=ProjectLibrary.createArtifact(s,"other","b","Artifact","unrelated",100);
        return s;
    }
    private static final class Memory implements ProjectTransactions.Backend{
        byte[] bytes;int writes;int mode;
        public byte[] read()throws IOException{
            if(mode==4)throw new IOException("unreadable");
            return bytes==null?null:bytes.clone();
        }
        public void write(byte[] value)throws IOException{
            writes++;
            if(mode==1)throw new IOException("before write");
            if(mode!=2)bytes=value.clone();
            if(mode==3)throw new IOException("after write");
        }
    }
    private static ProjectTransactions<ProjectLibrary.State> tx(Memory m,Object lock){
        return new ProjectTransactions<>(m,ProjectLibrary.CODEC,lock,ProjectLibrary.empty(),ProjectLibrary.MAX_BYTES);
    }
    public static void main(String[] args)throws Exception{
        ProjectLibrary.State s=setup(),original=s;
        ok(s.revision==4&&s.projects.size()==2&&s.project("p").title.equals("프로젝트 😀"));
        String v1=s.project("p").artifact("a").current().text;
        s=canonical(s);
        ok(s.project("p").artifact("a").current().text.equals(v1));
        ok(s.project("p").instructions.equals("instructions\n한글"));
        ok(s.project("other").artifact("b").current().text.equals("unrelated"));
        ProjectLibrary.State initial=s;
        rejects(()->initial.project("missing"));
        rejects(()->initial.project("p").artifact("b"));
        rejects(()->ProjectLibrary.createProject(initial,"p","Duplicate",""));
        rejects(()->ProjectLibrary.createProject(initial,"a","Duplicate",""));
        rejects(()->ProjectLibrary.createArtifact(initial,"p","other","Duplicate","",101));
        rejects(()->ProjectLibrary.createArtifact(initial,"p","../path","Bad","",101));
        rejects(()->ProjectLibrary.createProject(initial,"bad"," ",""));
        rejects(()->ProjectLibrary.createProject(initial,"bad","x\nx",""));
        rejects(()->ProjectLibrary.createProject(initial,"bad","x\u202Ey",""));
        rejects(()->ProjectLibrary.createProject(initial,"bad","x".repeat(81),""));
        rejects(()->ProjectLibrary.createProject(initial,"bad","title","x".repeat(2001)));
        rejects(()->ProjectLibrary.editArtifact(initial,"p","a","\uD800",101));
        rejects(()->ProjectLibrary.editArtifact(initial,"p","a","x".repeat(32001),101));
        rejects(()->ProjectLibrary.editArtifact(initial,"p","a","other",99));
        rejects(()->ProjectLibrary.restoreArtifact(initial,"p","a",2,101));
        ok(ProjectLibrary.editArtifact(s,"p","a",v1,101)==s);
        ok(ProjectLibrary.editProject(s,"p",s.project("p").title,s.project("p").instructions)==s);
        ok(ProjectLibrary.renameArtifact(s,"p","a","Artifact")==s);
        s=ProjectLibrary.editArtifact(s,"p","a","new version",101);
        s=ProjectLibrary.restoreArtifact(s,"p","a",1,102);
        ProjectLibrary.Artifact a=s.project("p").artifact("a");
        ok(a.versions.size()==3&&a.current().ordinal==3&&a.current().saved==102);
        ok(a.version(1).text.equals(v1)&&a.version(2).text.equals("new version")&&a.version(3).text.equals(v1));
        ok(original.project("p").artifact("a").versions.size()==1);
        ok(ProjectLibrary.restoreArtifact(s,"p","a",1,103)==s);
        ProjectLibrary.State three=s;
        s=ProjectLibrary.renameArtifact(s,"p","a","Renamed");
        ok(s.project("p").artifact("a").title.equals("Renamed")&&s.project("p").artifact("a").versions.equals(three.project("p").artifact("a").versions));
        s=ProjectLibrary.editProject(s,"p","New title","Updated");
        ok(s.project("other")==three.project("other"));
        s=ProjectLibrary.deleteArtifact(s,"p","a");
        ok(s.project("p").artifacts.isEmpty()&&s.project("other").artifact("b").current().text.equals("unrelated"));
        s=ProjectLibrary.deleteProject(s,"p");
        ok(s.projects.size()==1&&s.project("other").id.equals("other"));
        try{s.projects.clear();throw new AssertionError("Mutable project list");}catch(UnsupportedOperationException expected){n++;}
        s=canonical(ProjectLibrary.deleteProject(s,"other"));ok(s.projects.isEmpty()&&s.revision>0);

        ProjectLibrary.State max=ProjectLibrary.empty();
        for(int i=0;i<ProjectLibrary.MAX_PROJECTS;i++)max=ProjectLibrary.createProject(max,"p"+i,"P","");
        ProjectLibrary.State projectCap=max;
        rejects(()->ProjectLibrary.createProject(projectCap,"overflow","P",""));
        for(int i=0;i<ProjectLibrary.MAX_ARTIFACTS;i++)max=ProjectLibrary.createArtifact(max,"p0","a"+i,"A","",100);
        ProjectLibrary.State artifactCap=max;
        rejects(()->ProjectLibrary.createArtifact(artifactCap,"p0","overflow","A","",100));
        for(int i=2;i<=ProjectLibrary.MAX_VERSIONS;i++)max=ProjectLibrary.editArtifact(max,"p0","a0","v"+i,100+i);
        ProjectLibrary.State versionCap=max;
        rejects(()->ProjectLibrary.editArtifact(versionCap,"p0","a0","seventh",200));
        rejects(()->ProjectLibrary.restoreArtifact(versionCap,"p0","a0",1,200));
        ok(canonical(max).project("p0").artifact("a0").versions.size()==6);
        ok(ProjectLibrary.encode(max).length<ProjectLibrary.MAX_BYTES);

        // Exact 2 MiB canonical envelope boundary including both layers of JSON escaping.
        ProjectLibrary.State size=ProjectLibrary.createProject(ProjectLibrary.empty(),"p","Size","");
        for(int i=0;i<11;i++){
            size=ProjectLibrary.createArtifact(size,"p","a"+i,"A","x".repeat(31999)+"1",1);
            int limit=i==10?5:6;
            for(int j=2;j<=limit;j++)size=ProjectLibrary.editArtifact(size,"p","a"+i,"x".repeat(31999)+j,j);
        }
        ProjectLibrary.State beforeLast=size;
        int remaining=ProjectLibrary.MAX_BYTES-ProjectLibrary.encode(ProjectLibrary.editArtifact(size,"p","a10","",6)).length;
        ok(remaining>0&&remaining<ProjectLibrary.MAX_TEXT);
        ProjectLibrary.State exact=ProjectLibrary.editArtifact(size,"p","a10","x".repeat(remaining),6);
        ok(ProjectLibrary.encode(exact).length==ProjectLibrary.MAX_BYTES);
        ok(canonical(exact).project("p").artifact("a10").current().text.length()==remaining);
        rejects(()->ProjectLibrary.encode(ProjectLibrary.editArtifact(beforeLast,"p","a10","x".repeat(remaining+1),6)));
        ProjectLibrary.State unicode=ProjectLibrary.createProject(ProjectLibrary.empty(),"p","한글","");
        unicode=ProjectLibrary.createArtifact(unicode,"p","a","A","文😀\"\\\n".repeat(4000),1);
        ok(canonical(unicode).project("p").artifact("a").current().text.equals(unicode.project("p").artifact("a").current().text));

        byte[] good=ProjectLibrary.encode(initial);
        String raw=new String(good,StandardCharsets.UTF_8);
        rejects(()->ProjectLibrary.decode(raw.replaceFirst("\"schema\":1","\"schema\":2").getBytes(StandardCharsets.UTF_8)));
        rejects(()->ProjectLibrary.decode((raw+" ").getBytes(StandardCharsets.UTF_8)));
        byte[] corrupt=good.clone();corrupt[corrupt.length-3]=(byte)(corrupt[corrupt.length-3]=='0'?'1':'0');
        rejects(()->ProjectLibrary.decode(corrupt));
        rejects(()->ProjectLibrary.decode(new byte[]{(byte)0xc3,0x28}));
        rejects(()->ProjectLibrary.decode(new byte[0]));
        rejects(()->ProjectLibrary.decode(new byte[ProjectLibrary.MAX_BYTES+1]));
        rejects(()->ProjectLibrary.decode(ProjectLibrary.encode(ProjectLibrary.empty())));
        for(String bad:new String[]{
            "{\"revision\":1,\"revision\":1,\"projects\":[]}",
            "{\"revision\":\"1\",\"projects\":[]}",
            "{\"revision\":1.0,\"projects\":[]}",
            "{\"revision\":1,\"projects\":[],\"extra\":1}",
            "{\"revision\":1,\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\",\"artifacts\":[]},{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\",\"artifacts\":[]}]}",
            "{\"revision\":1,\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\",\"artifacts\":[{\"id\":\"a\",\"title\":\"A\",\"versions\":[]}]}]}",
            "{\"revision\":1,\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\",\"artifacts\":[{\"id\":\"p\",\"title\":\"A\",\"versions\":[{\"ordinal\":1,\"saved\":1,\"text\":\"ok\"}]}]}]}",
            "{\"revision\":1,\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\",\"artifacts\":[{\"id\":\"a\",\"title\":\"A\",\"versions\":[{\"ordinal\":2,\"saved\":1,\"text\":\"ok\"}]}]}]}",
            "{\"revision\":1,\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\",\"artifacts\":[{\"id\":\"a\",\"title\":\"A\",\"versions\":[{\"ordinal\":1,\"saved\":0,\"text\":\"ok\"}]}]}]}",
            "{\"revision\":1,\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\",\"artifacts\":[{\"id\":\"a\",\"title\":\"A\",\"versions\":[{\"ordinal\":1,\"saved\":2,\"text\":\"a\"},{\"ordinal\":2,\"saved\":1,\"text\":\"b\"}]}]}]}",
            "{\"revision\":1,\"projects\":[{\"id\":\"p\",\"title\":\"P\",\"instructions\":\"\\ud800\",\"artifacts\":[]}]}",
            "{\"revision\":1,\"projects\":"+ "[".repeat(20)+"]".repeat(20)+"}",
            "{'revision':1,'projects':[]}",
            "{\"revision\":1,/*comment*/\"projects\":[]}"
        })rejects(()->ProjectLibrary.decode(pack(bad)));

        Object lock=new Object();Memory mem=new Memory();
        ProjectTransactions<ProjectLibrary.State> t=tx(mem,lock);
        ProjectTransactions.Snapshot<ProjectLibrary.State> empty=t.read();
        ok(empty.revision==0&&mem.writes==0&&mem.bytes==null);
        ProjectTransactions.Snapshot<ProjectLibrary.State> one=t.commit(empty,x->ProjectLibrary.createProject(x,"p","P",""));
        ok(one.revision==1&&mem.writes==1);
        fails(()->t.commit(empty,x->ProjectLibrary.createProject(x,"stale","S","")),ProjectTransactions.Failure.STALE);
        fails(()->t.commit(one,x->ProjectLibrary.editProject(x,"p","P","")),ProjectTransactions.Failure.NO_CHANGE);
        fails(()->t.commit(one,x->ProjectLibrary.createArtifact(x,"missing","a","A","",1)),ProjectTransactions.Failure.INVALID);
        ok(mem.writes==1);
        byte[] saved=mem.bytes.clone();mem.mode=1;
        fails(()->t.commit(one,x->ProjectLibrary.editProject(x,"p","Failure","")),ProjectTransactions.Failure.WRITE_UNCERTAIN);
        ok(Arrays.equals(saved,mem.bytes));
        mem.mode=2;
        fails(()->t.commit(one,x->ProjectLibrary.editProject(x,"p","Lost","")),ProjectTransactions.Failure.WRITE_UNCERTAIN);
        ok(Arrays.equals(saved,mem.bytes));
        mem.mode=3;
        fails(()->t.commit(one,x->ProjectLibrary.editProject(x,"p","Committed despite exception","")),ProjectTransactions.Failure.WRITE_UNCERTAIN);
        ok(t.read().revision==2);
        mem.mode=0;
        fails(()->t.commit(one,x->ProjectLibrary.editProject(x,"p","Stale","")),ProjectTransactions.Failure.STALE);
        ProjectTransactions.Snapshot<ProjectLibrary.State> two=t.read();
        AtomicInteger accepted=new AtomicInteger(),stale=new AtomicInteger();CountDownLatch start=new CountDownLatch(1);
        Runnable compete=()->{
            try{start.await();tx(mem,lock).commit(two,x->ProjectLibrary.editProject(x,"p","Winner",""));accepted.incrementAndGet();}
            catch(ProjectTransactions.TransactionFailure e){if(e.reason==ProjectTransactions.Failure.STALE)stale.incrementAndGet();else throw new RuntimeException(e);}
            catch(Exception e){throw new RuntimeException(e);}
        };
        Thread first=new Thread(compete),second=new Thread(compete);
        first.start();second.start();start.countDown();first.join();second.join();
        ok(accepted.get()==1&&stale.get()==1&&t.read().revision==3);
        // Same revision but different bytes still invalidates a reviewed snapshot.
        ProjectTransactions.Snapshot<ProjectLibrary.State> threeSaved=t.read();
        ProjectLibrary.State different=ProjectLibrary.createProject(ProjectLibrary.empty(),"p","Other","");
        different=ProjectLibrary.editProject(different,"p","Different","");
        different=ProjectLibrary.editProject(different,"p","Different again","");
        mem.bytes=ProjectLibrary.encode(different);
        fails(()->t.commit(threeSaved,x->ProjectLibrary.editProject(x,"p","Wrong","")),ProjectTransactions.Failure.STALE);
        int writes=mem.writes;
        mem.mode=4;rejects(()->t.commit(threeSaved,x->ProjectLibrary.editProject(x,"p","Wrong","")));ok(mem.writes==writes);
        mem.mode=0;mem.bytes=new byte[]{1,2,3};
        rejects(()->t.commit(empty,x->ProjectLibrary.createProject(x,"p","Overwrite","")));ok(mem.writes==writes);
        System.out.println("PASS: "+n+" project schema, isolated version history, exact byte limit and transaction assertions");
    }
}
