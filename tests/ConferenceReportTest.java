package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class ConferenceReportTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("report check "+n);n++;}
    private static void rejects(Runnable action){
        try{action.run();}catch(IllegalArgumentException e){n++;return;}
        throw new AssertionError("accepted invalid snapshot "+n);
    }
    private static ConferencePolicy.Request request(String required){
        return new ConferencePolicy.Request("한글 목표 😀","local:model","ko",WorkflowPolicy.Gate.CONTAINS,required);
    }
    private static ConferencePolicy.Reply reply(String key,String value)throws Exception{
        return new ConferencePolicy.Reply(new JSONObject().put(key,value).toString(),false);
    }
    private static ConferencePolicy.Reply review(String decision,String issues)throws Exception{
        return new ConferencePolicy.Reply(new JSONObject().put("decision",decision).put("issues",issues).toString(),false);
    }
    private static ConferencePolicy.Result run(ConferencePolicy.Request req,ConferencePolicy.Reply... replies){
        AtomicInteger calls=new AtomicInteger();
        return ConferencePolicy.run(req,call->replies[calls.getAndIncrement()],()->false,x->{});
    }
    private static ConferencePolicy.Result forged(ConferencePolicy.Result base,ConferencePolicy.Status state,
            ConferencePolicy.Cause cause,List<ConferencePolicy.Turn> turns,int attempts,int round,String artifact,
            WorkflowPolicy.Sample predicate,ConferencePolicy.Recommendation recommendation){
        return new ConferencePolicy.Result(base.request,state,cause,turns,attempts,null,round,artifact,predicate,recommendation);
    }
    private static void report(ConferenceReport value,String status,int turns,int artifactRound,int reviewedRound)throws Exception{
        JSONObject json=new JSONObject(value.json());
        ok(json.getInt("schema")==1&&json.getString("kind").equals("LOCAL_ROLE_CONFERENCE"));
        ok(json.getString("status").equals(status)&&json.getInt("completed_turns")==turns);
        ok(json.getString("goal").equals(value.request.goal)&&json.getString("condition").equals(value.request.condition.required));
        ok(json.getString("model").equals(value.request.model)&&json.getString("language").equals(value.request.language));
        ok(json.getString("role_mode").equals("ONE_MODEL_SEQUENTIAL"));
        ok(!json.getBoolean("factual_verification")&&!json.getBoolean("independent_verification"));
        ok(json.getInt("artifact_round")==artifactRound&&json.getInt("reviewed_round")==reviewedRound);
        ok(json.getBoolean("conference_completed")==value.completed());
        ok(json.getInt("role_request_attempts")==value.result.attempts&&json.getLong("analysis_elapsed_ms")==value.elapsedMillis);
        ok(json.getBoolean("elapsed_excludes_final_cleanup")&&json.getBoolean("capacity_is_estimate"));
        ok(json.getJSONObject("capacity").getInt("producer_output_tokens")==value.capacity.producerTokens);
        ok(json.getJSONArray("turns").length()==turns);
        for(int i=0;i<turns;i++){
            JSONObject turn=json.getJSONArray("turns").getJSONObject(i);ConferencePolicy.Turn expected=value.result.turns.get(i);
            ok(turn.getString("role").equals(expected.role.name())&&turn.getInt("round")==expected.round);
            ok(turn.getString("text").equals(expected.text));
        }
        if(value.hasArtifact())ok(json.getString("artifact").equals(value.result.artifact));
        else ok(json.isNull("artifact"));
        if(value.stopReason==null)ok(json.isNull("stop_reason"));
        else ok(json.getString("stop_reason").equals(value.stopReason));
        ok(value.json().length()<=ConferenceReport.MAX_JSON_CHARS);
    }
    public static void main(String[] args)throws Exception{
        ConferencePolicy.Request req=request("OK");
        String artifact="  OK 한글 😀\n\"quoted\"\\path  ";
        ConferencePolicy.Result initial=run(req,reply("plan","Plan"),reply("artifact",artifact),review("ACCEPT",""));
        ConferenceReport complete=new ConferenceReport(req,initial,null,1234);
        ok(complete.completed()&&complete.hasArtifact()&&initial.artifact.equals(artifact));
        report(complete,"COMPLETE",3,1,1);
        ConferencePolicy.Result revised=run(req,reply("plan","Plan"),reply("artifact","draft"),review("REVISE","Fix"),
            reply("artifact",artifact),review("ACCEPT",""));
        report(new ConferenceReport(req,revised,null,1500),"COMPLETE",5,2,2);
        ConferencePolicy.Result blocked=run(req,reply("plan","Plan"),reply("artifact","draft"),review("ACCEPT",""),
            reply("artifact","still no match"),review("ACCEPT",""));
        ConferenceReport blockReport=new ConferenceReport(req,blocked,null,1500);
        ok(!blockReport.completed()&&blockReport.hasArtifact());
        report(blockReport,"BLOCKED",5,2,2);
        ConferencePolicy.Result stale=run(req,reply("plan","Plan"),reply("artifact","draft"),review("REVISE","Fix"),
            reply("artifact",artifact),new ConferencePolicy.Reply("{\"decision\":\"ACCEPT\"}",false));
        report(new ConferenceReport(req,stale,null,1500),"FAILED",4,2,0);
        ConferencePolicy.Result failed=run(req,new ConferencePolicy.Reply("not JSON",false));
        report(new ConferenceReport(req,failed,null,200),"FAILED",0,0,0);
        ConferencePolicy.Result interrupted=initial.stopped();
        ConferenceReport stopReport=new ConferenceReport(req,interrupted,"USER",1500);
        ok(!stopReport.completed()&&stopReport.hasArtifact());
        report(stopReport,"STOPPED",3,1,1);
        AtomicBoolean cancelled=new AtomicBoolean(true);
        ConferencePolicy.Result before=ConferencePolicy.run(req,call->{throw new AssertionError("unexpected request");},cancelled::get,x->{});
        report(new ConferenceReport(req,before,"UNKNOWN",0),"STOPPED",0,0,0);
        ConferencePolicy.Request impossible=new ConferencePolicy.Request("goal","model","en",WorkflowPolicy.Gate.MIN_CHARS,"601");
        ConferencePolicy.Result rejected=ConferencePolicy.run(impossible,call->{throw new AssertionError("unexpected request");},()->false,x->{});
        report(new ConferenceReport(impossible,rejected,null,0),"FAILED",0,0,0);
        rejects(()->new ConferenceReport(request("OK"),initial,null,0));
        rejects(()->new ConferenceReport(req,initial,"USER",0));
        rejects(()->new ConferenceReport(req,initial,null,-1));
        rejects(()->new ConferenceReport(req,interrupted,null,0));
        rejects(()->new ConferenceReport(req,interrupted,"invented",0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.RUNNING,ConferencePolicy.Cause.NONE,
            initial.turns,3,1,artifact,initial.predicate,initial.recommendation),null,0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            initial.turns,2,1,artifact,initial.predicate,initial.recommendation),null,0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            initial.turns,5,1,artifact,initial.predicate,initial.recommendation),null,0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            initial.turns,3,1,artifact+"!",initial.predicate,initial.recommendation),null,0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            initial.turns,3,1,artifact,WorkflowPolicy.Sample.MISMATCH,initial.recommendation),null,0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            initial.turns,3,1,artifact,initial.predicate,null),null,0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.TRANSPORT,
            initial.turns,3,1,artifact,initial.predicate,initial.recommendation),null,0));
        rejects(()->new ConferenceReport(req,forged(blocked,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            blocked.turns,5,2,blocked.artifact,blocked.predicate,blocked.recommendation),null,0));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.BLOCKED,ConferencePolicy.Cause.CONDITION_OR_REVIEW,
            initial.turns,3,1,artifact,initial.predicate,initial.recommendation),null,0));
        List<ConferencePolicy.Turn> badOrder=new ArrayList<>(initial.turns);
        badOrder.set(0,initial.turns.get(1));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            badOrder,3,1,artifact,initial.predicate,initial.recommendation),null,0));
        List<ConferencePolicy.Turn> badText=new ArrayList<>(initial.turns);
        badText.set(0,new ConferencePolicy.Turn(ConferencePolicy.Role.PLANNER,1,"a".repeat(301),null,null));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            badText,3,1,artifact,initial.predicate,initial.recommendation),null,0));
        List<ConferencePolicy.Turn> staleReview=new ArrayList<>(revised.turns);
        staleReview.set(4,revised.turns.get(2));
        rejects(()->new ConferenceReport(req,forged(revised,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            staleReview,5,2,artifact,revised.predicate,revised.recommendation),null,0));
        List<ConferencePolicy.Turn> extra=new ArrayList<>(initial.turns);
        extra.add(new ConferencePolicy.Turn(ConferencePolicy.Role.PRODUCER,2,artifact,null,WorkflowPolicy.Sample.MATCH));
        extra.add(new ConferencePolicy.Turn(ConferencePolicy.Role.REVIEWER,2,"",ConferencePolicy.Recommendation.ACCEPT,WorkflowPolicy.Sample.MATCH));
        rejects(()->new ConferenceReport(req,forged(initial,ConferencePolicy.Status.COMPLETE,ConferencePolicy.Cause.NONE,
            extra,5,2,artifact,initial.predicate,initial.recommendation),null,0));
        rejects(()->ConferenceReport.boundedJson(null));
        rejects(()->ConferenceReport.boundedJson("a".repeat(ConferenceReport.MAX_JSON_CHARS+1)));
        ok(ConferenceReport.boundedJson("a".repeat(ConferenceReport.MAX_JSON_CHARS)).length()==ConferenceReport.MAX_JSON_CHARS);
        System.out.println("PASS: "+n+" conference terminal snapshot and export assertions");
    }
}
