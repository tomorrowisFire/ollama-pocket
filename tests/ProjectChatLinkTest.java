package com.ollamapocket.app;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class ProjectChatLinkTest {
    private static int n;
    private static void ok(boolean v){if(!v)throw new AssertionError("link "+n);n++;}
    interface Action{void run()throws Exception;}
    private static void reject(Action a)throws Exception{try{a.run();}catch(IllegalArgumentException e){n++;return;}throw new AssertionError("accepted invalid link");}
    public static void main(String[] args)throws Exception{
        ProjectLibrary.Link link=new ProjectLibrary.Link("qwen:7b","123");
        ProjectLibrary.Link other=new ProjectLibrary.Link("qwen:7b","124");
        ok(link.equals(new ProjectLibrary.Link("qwen:7b","123"))&&!link.equals(other));
        reject(()->new ProjectLibrary.Link("bad model","123"));reject(()->new ProjectLibrary.Link("a","1_2"));
        reject(()->new ProjectLibrary.Link("a",""));reject(()->new ProjectLibrary.Link("a","1".repeat(20)));
        reject(()->new ProjectLibrary.Link("","1"));reject(()->new ProjectLibrary.Link("a",null));
        ProjectLibrary.State initial=ProjectLibrary.createProject(ProjectLibrary.empty(),"p","Project","note");
        initial=ProjectLibrary.createArtifact(initial,"p","a","Artifact","v1",1);
        byte[] v1=ProjectLibrary.encode(initial);
        ok(new JSONObject(new String(v1,StandardCharsets.UTF_8)).getInt("schema")==1);
        ok(Arrays.equals(v1,ProjectLibrary.encode(ProjectLibrary.decode(v1))));
        ProjectLibrary.State first=ProjectLibrary.addLink(initial,"p",link);
        ok(first.revision==initial.revision+1&&initial.project("p").links.isEmpty());
        ok(ProjectLibrary.addLink(first,"p",link)==first);
        byte[] v2=ProjectLibrary.encode(first);
        ok(new JSONObject(new String(v2,StandardCharsets.UTF_8)).getInt("schema")==2);
        ProjectLibrary.State decoded=ProjectLibrary.decode(v2);
        ok(decoded.project("p").links.size()==1&&decoded.project("p").links.get(0).equals(link));
        ok(Arrays.equals(v2,ProjectLibrary.encode(decoded)));
        ok(decoded.project("p").artifact("a").current().text.equals("v1"));
        reject(()->ProjectLibrary.removeLink(first,"p",other));
        reject(()->ProjectLibrary.addLink(first,"missing",link));
        reject(()->ProjectLibrary.addLink(first,"p",null));
        reject(()->ProjectLibrary.decode(new String(v2,StandardCharsets.UTF_8).replaceFirst("\"schema\":2","\"schema\":1").getBytes(StandardCharsets.UTF_8)));
        ProjectLibrary.State changed=ProjectLibrary.editProject(decoded,"p","Renamed","new note");
        changed=ProjectLibrary.createArtifact(changed,"p","b","B","",2);
        changed=ProjectLibrary.editArtifact(changed,"p","a","v2",2);
        changed=ProjectLibrary.renameArtifact(changed,"p","a","Renamed A");
        changed=ProjectLibrary.restoreArtifact(changed,"p","a",1,3);
        changed=ProjectLibrary.deleteArtifact(changed,"p","b");
        ok(changed.project("p").links.contains(link)&&changed.project("p").artifact("a").versions.size()==3);
        ProjectLibrary.State two=ProjectLibrary.addLink(changed,"p",other);
        ok(two.project("p").links.size()==2);
        ProjectLibrary.State removed=ProjectLibrary.removeLink(two,"p",link);
        ok(removed.project("p").links.size()==1&&removed.project("p").links.contains(other));
        removed=ProjectLibrary.removeLink(removed,"p",other);
        ok(new JSONObject(new String(ProjectLibrary.encode(removed),StandardCharsets.UTF_8)).getInt("schema")==1);
        ok(ProjectLibrary.deleteProject(two,"p").projects.isEmpty());
        ProjectLibrary.State cap=first;
        for(int i=0;i<ProjectLibrary.MAX_LINKS-1;i++)cap=ProjectLibrary.addLink(cap,"p",new ProjectLibrary.Link("model",String.valueOf(i)));
        final ProjectLibrary.State full=cap;
        ok(full.project("p").links.size()==ProjectLibrary.MAX_LINKS);
        reject(()->ProjectLibrary.addLink(full,"p",other));
        try{full.project("p").links.clear();throw new AssertionError("mutable");}catch(UnsupportedOperationException e){n++;}
        String index="[{\"id\":\"123\",\"title\":\"Chat\"}]",history="[{\"role\":\"user\",\"content\":\"hello\"}]";
        ok(ProjectChatLinkPolicy.inspect(link,index,history,true)==ProjectChatLinkPolicy.Status.AVAILABLE);
        ok(ProjectChatLinkPolicy.inspect(link,index,"[]",true)==ProjectChatLinkPolicy.Status.AVAILABLE);
        ok(ProjectChatLinkPolicy.inspect(link,index,history,false)==ProjectChatLinkPolicy.Status.MODEL_UNAVAILABLE);
        ok(ProjectChatLinkPolicy.inspect(link,null,history,true)==ProjectChatLinkPolicy.Status.MISSING);
        ok(ProjectChatLinkPolicy.inspect(link,index,null,true)==ProjectChatLinkPolicy.Status.MISSING);
        ok(ProjectChatLinkPolicy.inspect(other,index,history,true)==ProjectChatLinkPolicy.Status.MISSING);
        ok(ProjectChatLinkPolicy.inspect(link,"[{\"id\":\"123\"},{\"id\":\"123\"}]",history,true)==ProjectChatLinkPolicy.Status.UNREADABLE);
        ok(ProjectChatLinkPolicy.inspect(link,"[{\"id\":123}]",history,true)==ProjectChatLinkPolicy.Status.UNREADABLE);
        ok(ProjectChatLinkPolicy.inspect(link,index,"not json",true)==ProjectChatLinkPolicy.Status.UNREADABLE);
        ok(ProjectChatLinkPolicy.inspect(link,index,"[{}]",true)==ProjectChatLinkPolicy.Status.UNREADABLE);
        ok(ProjectChatLinkPolicy.inspect(link,index,"[".repeat(9)+"]".repeat(9),true)==ProjectChatLinkPolicy.Status.UNREADABLE);
        ok(ProjectChatLinkPolicy.inspect(link,index,"x".repeat(512001),true)==ProjectChatLinkPolicy.Status.UNREADABLE);
        ok(ProjectChatLinkPolicy.inspect(link,index,history+"[]",true)==ProjectChatLinkPolicy.Status.UNREADABLE);
        final byte[][] bytes={v1};
        ProjectTransactions<ProjectLibrary.State> tx=new ProjectTransactions<>(new ProjectTransactions.Backend(){
            public byte[] read(){return bytes[0];}
            public void write(byte[] b){bytes[0]=b;}
        },ProjectLibrary.CODEC,new Object(),ProjectLibrary.empty(),ProjectLibrary.MAX_BYTES);
        ProjectTransactions.Snapshot<ProjectLibrary.State> reviewed=tx.read();
        ProjectTransactions.Snapshot<ProjectLibrary.State> saved=tx.commit(reviewed,s->ProjectLibrary.addLink(s,"p",link));
        ok(saved.value.project("p").links.contains(link));
        try{tx.commit(reviewed,s->ProjectLibrary.addLink(s,"p",other));throw new AssertionError("stale");}
        catch(ProjectTransactions.TransactionFailure e){ok(e.reason==ProjectTransactions.Failure.STALE);}
        ok(tx.read().value.project("p").links.size()==1);
        System.out.println("PASS: "+n+" durable local chat link schema, identity, resolution and transaction assertions");
    }
}
