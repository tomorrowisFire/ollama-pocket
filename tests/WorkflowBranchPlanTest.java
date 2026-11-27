package com.ollamapocket.app;

import java.util.*;
import static com.ollamapocket.app.WorkflowBranchPlan.*;

public final class WorkflowBranchPlanTest {
    private static final String DEF="1".repeat(64), EVIDENCE="2".repeat(64);
    private static int checks;
    private static void ok(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static void rejects(Runnable action){checks++;try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid graph/checkpoint accepted");}
    private static Node task(String id,String next){return Node.task(id,DEF,next);}
    private static Node branch(String id,String yes,String no){return Node.condition(id,DEF,yes,no);}
    private static Visit visit(String id,Outcome outcome){return new Visit(id,outcome,EVIDENCE);}
    private static WorkflowBranchPlan example(){return new WorkflowBranchPlan(List.of(branch("check","alpha","beta"),task("alpha","finish"),task("beta","finish"),task("finish",COMPLETE)));}
    private static void paths(){
        WorkflowBranchPlan p=example();
        for(boolean yes:new boolean[]{true,false}){
            Snapshot s=p.start();ok(s.status==Status.READY&&s.nextNode.equals("check"),"entry");
            s=p.advance(s,visit("check",yes?Outcome.CONDITION_TRUE:Outcome.CONDITION_FALSE));
            String selected=yes?"alpha":"beta";ok(s.nextNode.equals(selected),"verified branch");
            s=p.advance(s,visit(selected,Outcome.VERIFIED));ok(s.nextNode.equals("finish"),"converge");
            s=p.advance(s,visit("finish",Outcome.VERIFIED));ok(s.status==Status.COMPLETED&&s.nextNode==null&&s.visits.size()==3,"complete actual path only");
            ok(s.visits.get(1).node.equals(selected),"no synthetic result for skipped task");
            Snapshot restored=p.restore(p.sha256,s.visits,s.status,s.nextNode);ok(restored.visits.size()==3&&restored.status==s.status,"terminal replay");
            Snapshot done=s;rejects(()->p.advance(done,visit("finish",Outcome.VERIFIED)));
        }
        WorkflowBranchPlan stop=new WorkflowBranchPlan(List.of(branch("check",COMPLETE,BLOCK)));
        ok(stop.advance(stop.start(),visit("check",Outcome.CONDITION_FALSE)).status==Status.BLOCKED,"explicit block route");
        for(Outcome failure:new Outcome[]{Outcome.UNVERIFIED,Outcome.INVALID,Outcome.TRUNCATED,Outcome.UNCERTAIN}){
            Snapshot blocked=p.advance(p.start(),new Visit("check",failure,null));
            ok(blocked.status==Status.BLOCKED&&blocked.nextNode==null,"uncertain condition never false branch");
            ok(p.restore(p.sha256,blocked.visits,Status.BLOCKED,null).status==Status.BLOCKED,"blocked replay");
        }
        ok(p.advance(p.start(),new Visit("check",Outcome.CANCELLED,null)).status==Status.CANCELLED,"cancel terminal");
        rejects(()->p.advance(p.start(),visit("check",Outcome.VERIFIED)));
        Snapshot selected=p.advance(p.start(),visit("check",Outcome.CONDITION_TRUE));
        rejects(()->p.advance(selected,visit("alpha",Outcome.CONDITION_FALSE)));
        rejects(()->p.advance(selected,visit("beta",Outcome.VERIFIED)));
        rejects(()->p.advance(selected,visit("finish",Outcome.VERIFIED)));
        ok(p.advance(selected,new Visit("alpha",Outcome.UNVERIFIED,null)).status==Status.BLOCKED,"task mismatch blocked");
        rejects(()->new Visit("check",Outcome.CONDITION_TRUE,null));
        rejects(()->new Visit("check",Outcome.VERIFIED,"wrong"));
        rejects(()->new Visit("check",Outcome.INVALID,"wrong"));
    }
    private static void invalidGraphs(){
        rejects(()->new WorkflowBranchPlan(null));rejects(()->new WorkflowBranchPlan(List.of()));
        rejects(()->new WorkflowBranchPlan(Arrays.asList((Node)null)));
        rejects(()->new WorkflowBranchPlan(List.of(task("a","b"),task("a",COMPLETE))));
        rejects(()->new WorkflowBranchPlan(List.of(task("a","unknown"))));
        rejects(()->new WorkflowBranchPlan(List.of(task("a","a"))));
        rejects(()->new WorkflowBranchPlan(List.of(task("a","b"),task("b","a"))));
        rejects(()->new WorkflowBranchPlan(List.of(task("a",COMPLETE),task("b",COMPLETE))));
        rejects(()->new WorkflowBranchPlan(List.of(branch("a","b","c"),task("b",COMPLETE),task("c","d"))));
        rejects(()->branch("a",COMPLETE,COMPLETE));rejects(()->branch("a",COMPLETE,null));
        rejects(()->task("!complete",COMPLETE));rejects(()->task("a\n",COMPLETE));
        rejects(()->task("a".repeat(33),COMPLETE));rejects(()->Node.task("a","X".repeat(64),COMPLETE));
        List<Node> oversized=new ArrayList<>();for(int i=0;i<11;i++)oversized.add(task("n"+i,i==10?COMPLETE:"n"+(i+1)));
        rejects(()->new WorkflowBranchPlan(oversized));
        List<Node> mutable=new ArrayList<>(List.of(task("a",COMPLETE)));
        WorkflowBranchPlan p=new WorkflowBranchPlan(mutable);mutable.clear();ok(p.nodes().size()==1,"defensive graph copy");
        try{p.nodes().clear();throw new AssertionError("Mutable plan");}catch(UnsupportedOperationException expected){checks++;}
        Snapshot s=p.advance(p.start(),visit("a",Outcome.VERIFIED));
        try{s.visits.clear();throw new AssertionError("Mutable checkpoint");}catch(UnsupportedOperationException expected){checks++;}
    }
    private static void replay(){
        WorkflowBranchPlan p=example();ok(p.sha256.equals(example().sha256),"deterministic graph identity");
        WorkflowBranchPlan changed=new WorkflowBranchPlan(List.of(branch("check","alpha","beta"),Node.task("alpha",EVIDENCE,"finish"),task("beta","finish"),task("finish",COMPLETE)));
        ok(!p.sha256.equals(changed.sha256),"definition identity");
        WorkflowBranchPlan edges=new WorkflowBranchPlan(List.of(branch("check","beta","alpha"),task("alpha","finish"),task("beta","finish"),task("finish",COMPLETE)));
        ok(!p.sha256.equals(edges.sha256),"edge identity");
        rejects(()->changed.advance(p.start(),visit("check",Outcome.CONDITION_TRUE)));
        rejects(()->p.restore(changed.sha256,List.of(),Status.READY,"check"));
        rejects(()->p.restore(p.sha256,List.of(),Status.COMPLETED,null));
        rejects(()->p.restore(p.sha256,List.of(visit("check",Outcome.CONDITION_FALSE)),Status.READY,"alpha"));
        rejects(()->p.restore(p.sha256,List.of(visit("alpha",Outcome.VERIFIED)),Status.READY,"finish"));
        rejects(()->p.restore(p.sha256,List.of(visit("check",Outcome.CONDITION_TRUE),visit("finish",Outcome.VERIFIED)),Status.COMPLETED,null));
        rejects(()->p.restore(p.sha256,List.of(new Visit("check",Outcome.UNCERTAIN,null),visit("alpha",Outcome.VERIFIED)),Status.COMPLETED,null));
        rejects(()->p.restore(p.sha256,Arrays.asList((Visit)null),Status.READY,"check"));
        Snapshot partial=p.advance(p.start(),visit("check",Outcome.CONDITION_FALSE));
        Snapshot restored=p.restore(p.sha256,partial.visits,Status.READY,"beta");
        ok(restored.nextNode.equals("beta")&&restored.visits.size()==1,"exact partial resume cursor");
    }
    private static void boundedRoutes(){
        // Exhaust all boolean combinations for graphs of up to ten forward-only nodes.
        for(int count=1;count<=10;count++){
            List<Node> nodes=new ArrayList<>();
            for(int i=0;i<count;i++){
                String next=i+1<count?"n"+(i+1):COMPLETE;
                String skip=i+2<count?"n"+(i+2):COMPLETE;
                nodes.add(next.equals(skip)?task("n"+i,next):branch("n"+i,next,skip));
            }
            WorkflowBranchPlan p=new WorkflowBranchPlan(nodes);
            for(int mask=0;mask<(1<<count);mask++){
                Snapshot s=p.start();int index=0,visits=0;
                while(s.status==Status.READY){
                    ok(s.nextNode.equals("n"+index)&&visits<count,"bounded ordered route");
                    Node n=nodes.get(index);boolean yes=(mask&(1<<index))!=0;
                    s=p.advance(s,visit(n.id,n.kind==Kind.TASK?Outcome.VERIFIED:yes?Outcome.CONDITION_TRUE:Outcome.CONDITION_FALSE));
                    index+=n.kind==Kind.TASK||yes?1:2;visits++;
                }
                ok(s.status==Status.COMPLETED&&s.visits.size()==visits,"bounded terminal path");
                ok(p.restore(p.sha256,s.visits,Status.COMPLETED,null).visits.size()==visits,"deterministic replay");
            }
        }
    }
    public static void main(String[] args){paths();invalidGraphs();replay();boundedRoutes();System.out.println("PASS: "+checks+" conditional workflow graph, bounded path and checkpoint replay checks");}
}
