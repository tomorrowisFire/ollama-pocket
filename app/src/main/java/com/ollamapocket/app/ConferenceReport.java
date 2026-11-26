package com.ollamapocket.app;

import java.util.Arrays;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only, bounded report of an exact executed request and terminal role prefix. */
final class ConferenceReport {
    static final int MAX_JSON_CHARS=32000;
    final ConferencePolicy.Request request;
    final ConferencePolicy.Result result;
    final ConferencePolicy.Capacity capacity;
    final String stopReason;
    final long elapsedMillis;
    final int artifactRound;
    final int reviewedRound;

    ConferenceReport(ConferencePolicy.Request req,ConferencePolicy.Result result,String stopReason,long elapsedMillis){
        if(req==null||result==null||result.request!=req||result.status==null||result.cause==null||
            result.status==ConferencePolicy.Status.RUNNING||result.activeRole!=null||elapsedMillis<0||
            result.turns.size()>ConferencePolicy.MAX_CALLS||result.attempts<result.turns.size()||
            result.attempts>ConferencePolicy.MAX_CALLS||result.attempts>result.turns.size()+1||
            result.round<0||result.round>2)
            throw new IllegalArgumentException("Invalid report identity or scope");
        if(result.status==ConferencePolicy.Status.STOPPED){
            if(result.cause!=ConferencePolicy.Cause.STOP||
                !Arrays.asList("USER","DEADLINE","THERMAL","MEMORY","SERVER","COOP","INTERRUPTED","UNKNOWN").contains(stopReason))
                throw new IllegalArgumentException("Invalid stop metadata");
        }else if(stopReason!=null)throw new IllegalArgumentException("Unexpected stop reason");
        ConferencePolicy.Capacity cap=ConferencePolicy.capacity(req);
        if(!cap.ready()&&(result.attempts!=0||!result.turns.isEmpty()||
            (result.status!=ConferencePolicy.Status.STOPPED&&
                (result.status!=ConferencePolicy.Status.FAILED||result.cause!=cap.cause))))
            throw new IllegalArgumentException("Invalid capacity evidence");

        ConferencePolicy.Role[] order={ConferencePolicy.Role.PLANNER,ConferencePolicy.Role.PRODUCER,
            ConferencePolicy.Role.REVIEWER,ConferencePolicy.Role.PRODUCER,ConferencePolicy.Role.REVIEWER};
        String artifact=null;WorkflowPolicy.Sample predicate=null;
        ConferencePolicy.Recommendation recommendation=null;
        int artifactRound=0,reviewedRound=0;
        for(int i=0;i<result.turns.size();i++){
            ConferencePolicy.Turn t=result.turns.get(i);
            int round=i<3?1:2;
            if(t==null||t.role!=order[i]||t.round!=round||!ConferencePolicy.validTurnText(t,cap))
                throw new IllegalArgumentException("Invalid completed role");
            switch(t.role){
                case PLANNER:
                    if(t.predicate!=null||t.recommendation!=null)throw new IllegalArgumentException("Invalid planner evidence");
                    break;
                case PRODUCER:
                    if(i==3&&predicate==WorkflowPolicy.Sample.MATCH&&recommendation==ConferencePolicy.Recommendation.ACCEPT)
                        throw new IllegalArgumentException("Correction after acceptance");
                    artifact=t.text;artifactRound=round;reviewedRound=0;recommendation=null;
                    predicate=WorkflowPolicy.sample(req.condition,artifact,false);
                    if(t.predicate!=predicate||t.recommendation!=null)
                        throw new IllegalArgumentException("Invalid artifact predicate");
                    break;
                case REVIEWER:
                    if(artifactRound!=round||t.predicate!=predicate||t.recommendation==null)
                        throw new IllegalArgumentException("Stale review");
                    recommendation=t.recommendation;reviewedRound=round;break;
            }
        }
        if(!Objects.equals(artifact,result.artifact)||predicate!=result.predicate||
            recommendation!=result.recommendation)throw new IllegalArgumentException("Invalid current result");
        int minRound=result.turns.isEmpty()?0:result.turns.get(result.turns.size()-1).round;
        int maxRound=result.turns.size()>=3?2:1;
        if(result.round<minRound||result.round>maxRound||(result.attempts>0&&result.round==0))
            throw new IllegalArgumentException("Invalid terminal round");
        boolean accepted=artifactRound>0&&artifactRound==reviewedRound&&predicate==WorkflowPolicy.Sample.MATCH&&
            recommendation==ConferencePolicy.Recommendation.ACCEPT;
        if(result.status==ConferencePolicy.Status.COMPLETE){
            if(result.cause!=ConferencePolicy.Cause.NONE||!accepted||
                (result.turns.size()!=3&&result.turns.size()!=5)||result.attempts!=result.turns.size()||
                result.round!=artifactRound)
                throw new IllegalArgumentException("False completion");
        }else if(result.status==ConferencePolicy.Status.BLOCKED){
            if(result.cause!=ConferencePolicy.Cause.CONDITION_OR_REVIEW||accepted||result.turns.size()!=5||
                result.attempts!=5||result.round!=2||reviewedRound!=2)
                throw new IllegalArgumentException("Invalid exhausted correction");
        }else if(result.status==ConferencePolicy.Status.FAILED){
            if(!Arrays.asList(ConferencePolicy.Cause.CONTEXT,ConferencePolicy.Cause.IMPOSSIBLE_CONDITION,
                ConferencePolicy.Cause.INVALID_REPLY,ConferencePolicy.Cause.TRANSPORT).contains(result.cause)||
                (result.cause==ConferencePolicy.Cause.IMPOSSIBLE_CONDITION&&cap.ready()))
                throw new IllegalArgumentException("Invalid failure cause");
        }
        this.request=req;this.result=result;this.capacity=cap;this.stopReason=stopReason;
        this.elapsedMillis=elapsedMillis;this.artifactRound=artifactRound;this.reviewedRound=reviewedRound;
    }
    boolean completed(){return result.status==ConferencePolicy.Status.COMPLETE;}
    boolean hasArtifact(){return result.artifact!=null;}
    private static Object nullable(Object value){return value==null?JSONObject.NULL:value;}
    private static Object name(Enum<?> value){return value==null?JSONObject.NULL:value.name();}
    static String boundedJson(String value){
        if(value==null||value.length()>MAX_JSON_CHARS)throw new IllegalArgumentException("Report size");
        return value;
    }
    String json()throws Exception{
        JSONArray turns=new JSONArray();
        for(ConferencePolicy.Turn t:result.turns)
            turns.put(new JSONObject().put("role",t.role.name()).put("round",t.round).put("text",t.text)
                .put("predicate",name(t.predicate)).put("model_recommendation",name(t.recommendation)));
        JSONObject caps=new JSONObject().put("ready",capacity.ready()).put("cause",capacity.cause.name())
            .put("plan_serialized_estimate",capacity.plan).put("artifact_serialized_estimate",capacity.artifact)
            .put("issues_serialized_estimate",capacity.issues).put("producer_output_tokens",capacity.producerTokens)
            .put("planner_reviewer_output_tokens",ConferencePolicy.PLAN_REVIEW_TOKENS);
        return boundedJson(new JSONObject().put("schema",1).put("kind","LOCAL_ROLE_CONFERENCE")
            .put("status",result.status.name()).put("cause",result.cause.name()).put("stop_reason",nullable(stopReason))
            .put("model",request.model).put("language",request.language).put("goal",request.goal)
            .put("gate",request.condition.gate.name()).put("condition",request.condition.required)
            .put("role_mode","ONE_MODEL_SEQUENTIAL").put("factual_verification",false).put("independent_verification",false)
            .put("capacity",caps).put("capacity_is_estimate",true)
            .put("role_request_attempts",result.attempts).put("completed_turns",result.turns.size())
            .put("analysis_elapsed_ms",elapsedMillis).put("elapsed_excludes_final_cleanup",true)
            .put("artifact",nullable(result.artifact)).put("artifact_round",artifactRound)
            .put("reviewed_round",reviewedRound).put("predicate",name(result.predicate))
            .put("model_recommendation",name(result.recommendation)).put("conference_completed",completed())
            .put("turns",turns).toString(2));
    }
}
