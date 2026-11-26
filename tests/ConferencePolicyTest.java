package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

public final class ConferencePolicyTest {
    private static int n;
    private static void ok(boolean v){if(!v)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}ok(bad);}
    private static ConferencePolicy.Request req(String goal,WorkflowPolicy.Gate gate,String condition){
        return new ConferencePolicy.Request(goal,"local:test","ko",gate,condition);
    }
    private static ConferencePolicy.Reply reply(String raw){return new ConferencePolicy.Reply(raw,false);}
    private static ConferencePolicy.Reply plan(String text)throws Exception{return reply(new JSONObject().put("plan",text).toString());}
    private static ConferencePolicy.Reply artifact(String text)throws Exception{return reply(new JSONObject().put("artifact",text).toString());}
    private static ConferencePolicy.Reply review(String decision,String issues)throws Exception{
        return reply(new JSONObject().put("decision",decision).put("issues",issues).toString());
    }
    private static final class Fixture {
        final List<ConferencePolicy.Call> calls=new ArrayList<>();
        final List<ConferencePolicy.Result> progress=new ArrayList<>();
        ConferencePolicy.Result run(ConferencePolicy.Request request,ConferencePolicy.Reply... replies){
            return ConferencePolicy.run(request,call->{
                calls.add(call);if(calls.size()>replies.length)throw new AssertionError("Unexpected extra call");
                return replies[calls.size()-1];
            },()->false,progress::add);
        }
    }
    private static JSONObject data(ConferencePolicy.Call call)throws Exception{
        int offset=call.prompt.indexOf("\nDATA:\n");
        ok(offset>=0&&DocumentBatchPolicy.fits(call.prompt,ConferencePolicy.MAX_TOKENS));
        return new JSONObject(call.prompt.substring(offset+7));
    }
    private static void state(ConferencePolicy.Result r,ConferencePolicy.Status status,int calls,int turns){
        ok(r.status==status);ok(r.attempts==calls);ok(r.turns.size()==turns);ok(r.activeRole==null);
        ok(r.attempts<=ConferencePolicy.MAX_CALLS);
    }
    public static void main(String[] args)throws Exception{
        ConferencePolicy.Request exact=req("Write a short note",WorkflowPolicy.Gate.EXACT,"OK");
        ok(exact.same(req("Write a short note",WorkflowPolicy.Gate.EXACT,"OK")));
        ok(!exact.same(req("other",WorkflowPolicy.Gate.EXACT,"OK")));
        ok(!exact.same(req("Write a short note",WorkflowPolicy.Gate.CONTAINS,"OK")));
        rejects(()->req("x",WorkflowPolicy.Gate.MIN_CHARS,"0"));
        rejects(()->req("x",WorkflowPolicy.Gate.JSON_VALUES,"{\"x\":null}"));
        rejects(()->req("x\nx",WorkflowPolicy.Gate.EXACT,"OK"));
        rejects(()->req("x".repeat(241),WorkflowPolicy.Gate.EXACT,"OK"));
        rejects(()->new ConferencePolicy.Request("x","m","fr",WorkflowPolicy.Gate.EXACT,"OK"));
        ConferencePolicy.Reply p=plan("Draft and inspect"),okArtifact=artifact(" OK "),bad=artifact("NO");
        ConferencePolicy.Reply yes=review("ACCEPT",""),no=review("REVISE","Fix the draft");
        Fixture f=new Fixture();
        ConferencePolicy.Result first=f.run(exact,p,okArtifact,yes);
        state(first,ConferencePolicy.Status.COMPLETE,3,3);
        ok(first.artifact.equals(" OK ")&&first.predicate==WorkflowPolicy.Sample.MATCH&&first.recommendation==ConferencePolicy.Recommendation.ACCEPT);
        ok(f.calls.get(0).role==ConferencePolicy.Role.PLANNER&&f.calls.get(1).role==ConferencePolicy.Role.PRODUCER&&f.calls.get(2).role==ConferencePolicy.Role.REVIEWER);
        ok(data(f.calls.get(0)).getString("goal").equals(exact.goal));
        ok(data(f.calls.get(1)).getString("plan").equals("Draft and inspect"));
        ok(data(f.calls.get(2)).getString("current_artifact").equals(" OK "));
        ok(data(f.calls.get(2)).getString("condition").equals("OK"));
        ok(f.progress.get(0).turns.isEmpty()&&f.progress.get(0).attempts==0);
        ok(f.progress.get(f.progress.size()-1).turns.size()==3);
        try{first.turns.clear();throw new AssertionError("Mutable transcript");}catch(UnsupportedOperationException expected){n++;}
        Fixture corrected=new Fixture();
        ConferencePolicy.Result second=corrected.run(exact,p,bad,yes,okArtifact,yes);
        state(second,ConferencePolicy.Status.COMPLETE,5,5);
        ok(corrected.calls.get(3).role==ConferencePolicy.Role.PRODUCER&&corrected.calls.get(3).round==2);
        ok(corrected.calls.get(4).role==ConferencePolicy.Role.REVIEWER&&corrected.calls.get(4).round==2);
        JSONObject repair=data(corrected.calls.get(3));
        ok(repair.getString("current_artifact").equals("NO")&&repair.getString("predicate_result").equals("MISMATCH"));
        ok(repair.getString("review_issues").isEmpty());
        ok(data(corrected.calls.get(4)).getString("current_artifact").equals(" OK "));
        ok(second.turns.get(2).recommendation==ConferencePolicy.Recommendation.ACCEPT&&second.turns.get(2).predicate==WorkflowPolicy.Sample.MISMATCH);
        ok(corrected.progress.stream().anyMatch(x->x.turns.size()==4&&x.recommendation==null&&x.predicate==WorkflowPolicy.Sample.MATCH));
        state(new Fixture().run(exact,p,okArtifact,no,okArtifact,yes),ConferencePolicy.Status.COMPLETE,5,5);
        ConferencePolicy.Result mismatch=new Fixture().run(exact,p,bad,yes,bad,yes);
        state(mismatch,ConferencePolicy.Status.BLOCKED,5,5);
        ok(mismatch.cause==ConferencePolicy.Cause.CONDITION_OR_REVIEW&&mismatch.predicate==WorkflowPolicy.Sample.MISMATCH);
        state(new Fixture().run(exact,p,okArtifact,no,okArtifact,no),ConferencePolicy.Status.BLOCKED,5,5);
        ConferencePolicy.Result stale=new Fixture().run(exact,p,bad,yes,okArtifact,reply("{\"decision\":\"ACCEPT\"}"));
        state(stale,ConferencePolicy.Status.FAILED,5,4);
        ok(stale.artifact.equals(" OK ")&&stale.recommendation==null&&stale.cause==ConferencePolicy.Cause.INVALID_REPLY);
        for(ConferencePolicy.Reply malformed:new ConferencePolicy.Reply[]{
            null,reply(""),reply("[]"),reply("{\"plan\":\"a\",\"plan\":\"b\"}"),
            reply("{\"plan\":\"a\",\"tool\":\"shell\"}"),reply("{\"plan\":2}"),
            plan(" "),plan("x".repeat(301)),new ConferencePolicy.Reply(p.text,true),
            reply("x".repeat(4001)),reply(p.text+" trailing")
        }){
            ConferencePolicy.Result rejected=new Fixture().run(exact,malformed);
            state(rejected,ConferencePolicy.Status.FAILED,1,0);ok(rejected.cause==ConferencePolicy.Cause.INVALID_REPLY);
        }
        state(new Fixture().run(exact,p,artifact("x".repeat(601))),ConferencePolicy.Status.FAILED,2,1);
        state(new Fixture().run(exact,p,artifact("\u0000")),ConferencePolicy.Status.FAILED,2,1);
        for(ConferencePolicy.Reply invalidReview:new ConferencePolicy.Reply[]{
            review("PASS",""),review("REVISE",""),review("ACCEPT","x".repeat(201)),
            reply("{\"decision\":\"ACCEPT\",\"issues\":\"\",\"score\":100}"),new ConferencePolicy.Reply(yes.text,true)
        }){
            ConferencePolicy.Result badReview=new Fixture().run(exact,p,okArtifact,invalidReview);
            state(badReview,ConferencePolicy.Status.FAILED,3,2);
            ok(badReview.recommendation==null&&badReview.artifact.equals(" OK "));
        }
        String escaped="Quote \"x\"\n한글 😀";
        ConferencePolicy.Request unicode=req("한글 \"목표\" 😀",WorkflowPolicy.Gate.CONTAINS,"한글");
        Fixture uf=new Fixture();
        ConferencePolicy.Result u=uf.run(unicode,plan("계획"),artifact(escaped),yes);
        state(u,ConferencePolicy.Status.COMPLETE,3,3);
        ok(data(uf.calls.get(2)).getString("current_artifact").equals(escaped)&&u.artifact.equals(escaped));
        for(WorkflowPolicy.Gate gate:WorkflowPolicy.Gate.values()){
            String required,answer;
            switch(gate){
                case CONTAINS:required="ok";answer="all ok";break;
                case EXACT:required="ok";answer="ok";break;
                case MIN_CHARS:required="2";answer="가😀";break;
                case JSON_KEYS:required="x";answer="{\"x\":true}";break;
                case JSON_FIELDS:required="x:integer";answer="{\"x\":3}";break;
                default:required="{\"x\":3}";answer="{\"x\":3}";
            }
            ConferencePolicy.Request request=req("task",gate,required);
            ConferencePolicy.Result checked=new Fixture().run(request,p,artifact(answer),yes);
            state(checked,ConferencePolicy.Status.COMPLETE,3,3);
            ok(checked.predicate==WorkflowPolicy.sample(request.condition,answer,false));
        }
        ConferencePolicy.Result wrongNumber=new Fixture().run(req("task",WorkflowPolicy.Gate.JSON_VALUES,"{\"x\":3}"),
            p,artifact("{\"x\":3.0}"),yes,artifact("{\"x\":3.0}"),yes);
        state(wrongNumber,ConferencePolicy.Status.BLOCKED,5,5);
        ConferencePolicy.Result context=new Fixture().run(req("한".repeat(240),WorkflowPolicy.Gate.EXACT,"OK"),p,artifact("한".repeat(600)));
        state(context,ConferencePolicy.Status.FAILED,2,2);ok(context.cause==ConferencePolicy.Cause.CONTEXT);
        AtomicInteger calls=new AtomicInteger();AtomicBoolean stopped=new AtomicBoolean(true);
        ConferencePolicy.Result cancelled=ConferencePolicy.run(exact,c->{calls.incrementAndGet();return p;},stopped::get,x->{});
        state(cancelled,ConferencePolicy.Status.STOPPED,0,0);ok(calls.get()==0);
        stopped.set(false);calls.set(0);
        ConferencePolicy.Result during=ConferencePolicy.run(exact,c->{
            int count=calls.incrementAndGet();if(count==2){stopped.set(true);return okArtifact;}return p;
        },stopped::get,x->{});
        state(during,ConferencePolicy.Status.STOPPED,2,1);ok(during.artifact==null&&calls.get()==2);
        stopped.set(false);calls.set(0);
        ConferencePolicy.Result beforeReview=ConferencePolicy.run(exact,c->{
            calls.incrementAndGet();return c.role==ConferencePolicy.Role.PLANNER?p:okArtifact;
        },stopped::get,x->{if(x.activeRole==ConferencePolicy.Role.REVIEWER)stopped.set(true);});
        state(beforeReview,ConferencePolicy.Status.STOPPED,2,2);ok(beforeReview.recommendation==null&&calls.get()==2);
        stopped.set(false);calls.set(0);
        ConferencePolicy.Result stopAfterReview=ConferencePolicy.run(exact,c->{
            calls.incrementAndGet();return c.role==ConferencePolicy.Role.PLANNER?p:c.role==ConferencePolicy.Role.PRODUCER?okArtifact:yes;
        },stopped::get,x->{if(x.turns.size()==3)stopped.set(true);});
        state(stopAfterReview,ConferencePolicy.Status.STOPPED,3,3);
        ConferencePolicy.Result error=ConferencePolicy.run(exact,c->{
            if(c.role==ConferencePolicy.Role.PLANNER)return p;throw new java.io.IOException("fixture");
        },()->false,x->{});
        state(error,ConferencePolicy.Status.FAILED,2,1);ok(error.cause==ConferencePolicy.Cause.TRANSPORT);
        ok(first.stopped().status==ConferencePolicy.Status.STOPPED&&first.stopped().turns.size()==3);
        System.out.println("PASS: "+n+" conference role, revision, predicate and cancellation assertions");
    }
}
