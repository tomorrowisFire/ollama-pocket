package com.ollamapocket.app;

import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

public final class ConferenceCapacityTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("capacity check "+n);n++;}
    private static ConferencePolicy.Request request(String goal,String lang,WorkflowPolicy.Gate gate,String required){
        return new ConferencePolicy.Request(goal,"local:test",lang,gate,required);
    }
    private static String fill(String prefix,int max,int budget,int seed){
        String[] pieces={"a","한","文","日","😀","\"","\\","\n","/","\u2028"};
        String value=prefix;
        ok(value.length()<=max&&ConferencePolicy.quoteCost(value)<=budget);
        for(int i=0;i<max;i++){
            String next=value+pieces[(i*7+seed)%pieces.length];
            if(next.length()>max||ConferencePolicy.quoteCost(next)>budget)break;
            value=next;
        }
        return value;
    }
    private static ConferencePolicy.Reply reply(String key,String value)throws Exception{
        return new ConferencePolicy.Reply(new JSONObject().put(key,value).toString(),false);
    }
    private static ConferencePolicy.Reply review(String decision,String issues)throws Exception{
        return new ConferencePolicy.Reply(new JSONObject().put("decision",decision).put("issues",issues).toString(),false);
    }
    private static JSONObject data(ConferencePolicy.Call call)throws Exception{
        int i=call.prompt.indexOf("\nDATA:\n");
        ok(i>=0&&DocumentBatchPolicy.fits(call.prompt,call.maxTokens));
        return new JSONObject(call.prompt.substring(i+7));
    }
    private static void caps(ConferencePolicy.Capacity c){
        ok(c.ready());
        ok(c.plan>=ConferencePolicy.MIN_PLAN_EST&&c.plan<=ConferencePolicy.MAX_PLAN_EST);
        ok(c.artifact>=ConferencePolicy.MIN_ARTIFACT_EST&&c.artifact<=ConferencePolicy.MAX_ARTIFACT_EST);
        ok(c.issues>=ConferencePolicy.MIN_ISSUES_EST&&c.issues<=ConferencePolicy.MAX_ISSUES_EST);
        ok(c.producerTokens==512||c.producerTokens==384||c.producerTokens==256);
        ok(c.outputTokens(ConferencePolicy.Role.PLANNER)==256&&c.outputTokens(ConferencePolicy.Role.REVIEWER)==256);
    }
    private static void rejected(ConferencePolicy.Request req){
        AtomicInteger calls=new AtomicInteger();
        ConferencePolicy.Result result=ConferencePolicy.run(req,call->{calls.incrementAndGet();throw new AssertionError("Unexpected call");},()->false,x->{});
        ok(result.status==ConferencePolicy.Status.FAILED);
        ok(result.cause==ConferencePolicy.Cause.IMPOSSIBLE_CONDITION||result.cause==ConferencePolicy.Cause.CONTEXT);
        ok(result.attempts==0&&result.turns.isEmpty()&&calls.get()==0);
    }
    public static void main(String[] args)throws Exception{
        ConferencePolicy.Request impossible=request("short goal","en",WorkflowPolicy.Gate.MIN_CHARS,"601");
        ok(ConferencePolicy.capacity(impossible).cause==ConferencePolicy.Cause.IMPOSSIBLE_CONDITION);rejected(impossible);
        ConferencePolicy.Request boundary=request("short goal","en",WorkflowPolicy.Gate.MIN_CHARS,"600");
        caps(ConferencePolicy.capacity(boundary));
        ConferencePolicy.Request invalid=request("task","en",WorkflowPolicy.Gate.CONTAINS,"a\u0000b");
        ok(ConferencePolicy.capacity(invalid).cause==ConferencePolicy.Cause.IMPOSSIBLE_CONDITION);rejected(invalid);
        ok(ConferencePolicy.quoteCost("\"\\\n한😀")==DocumentBatchPolicy.estimateTokens(JSONObject.quote("\"\\\n한😀")));
        int tested=0,blocked=0;
        for(String lang:new String[]{"en","ko","ja","zh-Hans","zh-Hant"}){
            for(String unit:new String[]{"a","한","文","😀","\"","\\"}){
                for(int goalLength:new int[]{20,120,240}){
                    String goal=unit.repeat(goalLength/unit.length());
                    for(String literal:new String[]{"OK","文".repeat(80),"한".repeat(180),"\"\\😀"}){
                        ConferencePolicy.Request req=request(goal,lang,WorkflowPolicy.Gate.CONTAINS,literal);
                        ConferencePolicy.Capacity cap=ConferencePolicy.capacity(req);
                        if(!cap.ready()){blocked++;rejected(req);continue;}
                        tested++;caps(cap);
                        ok(cap.artifact>=ConferencePolicy.quoteCost(literal));
                        String plan=fill("Plan",ConferencePolicy.MAX_PLAN,cap.plan,1);
                        String a1=fill(literal,ConferencePolicy.MAX_ARTIFACT,cap.artifact,2);
                        String a2=fill(literal,ConferencePolicy.MAX_ARTIFACT,cap.artifact,3);
                        String issues=fill("Revise",ConferencePolicy.MAX_ISSUES,cap.issues,4);
                        AtomicInteger calls=new AtomicInteger();
                        ConferencePolicy.Result result=ConferencePolicy.run(req,call->{
                            int index=calls.incrementAndGet();
                            JSONObject d=data(call),limits=d.getJSONObject("limits");
                            ok(d.getString("goal").equals(goal)&&d.getString("condition").equals(literal));
                            ok(limits.getInt("plan_json_est")==cap.plan&&limits.getInt("artifact_json_est")==cap.artifact&&limits.getInt("issues_json_est")==cap.issues);
                            ok(call.maxTokens==cap.outputTokens(call.role)&&limits.getInt("producer_output_tokens")==cap.producerTokens);
                            switch(index){
                                case 1:ok(call.role==ConferencePolicy.Role.PLANNER);return reply("plan",plan);
                                case 2:ok(call.role==ConferencePolicy.Role.PRODUCER&&d.getString("plan").equals(plan));return reply("artifact",a1);
                                case 3:ok(call.role==ConferencePolicy.Role.REVIEWER&&d.getString("current_artifact").equals(a1));return review("REVISE",issues);
                                case 4:ok(call.role==ConferencePolicy.Role.PRODUCER&&call.round==2&&d.getString("current_artifact").equals(a1)&&d.getString("review_issues").equals(issues));return reply("artifact",a2);
                                case 5:ok(call.role==ConferencePolicy.Role.REVIEWER&&d.getString("current_artifact").equals(a2));return review("ACCEPT","");
                                default:throw new AssertionError("Unexpected call");
                            }
                        },()->false,x->{});
                        ok(result.status==ConferencePolicy.Status.COMPLETE);
                        ok(calls.get()==5&&result.attempts==5&&result.turns.size()==5);
                        ok(result.artifact.equals(a2)&&result.predicate==WorkflowPolicy.Sample.MATCH&&result.recommendation==ConferencePolicy.Recommendation.ACCEPT);
                    }
                }
            }
        }
        ok(tested>=300&&tested+blocked==360);
        ConferencePolicy.Request longGoal=request("한".repeat(240),"ko",WorkflowPolicy.Gate.CONTAINS,"문".repeat(180));
        ConferencePolicy.Capacity cap=ConferencePolicy.capacity(longGoal);
        caps(cap);
        AtomicInteger calls=new AtomicInteger();
        ConferencePolicy.Result oversized=ConferencePolicy.run(longGoal,call->{
            int index=calls.incrementAndGet();
            if(index==1)return reply("plan","Plan");
            if(index==2)return reply("artifact","문".repeat(600));
            throw new AssertionError("Review must not be dispatched");
        },()->false,x->{});
        ok(oversized.status==ConferencePolicy.Status.FAILED&&oversized.cause==ConferencePolicy.Cause.INVALID_REPLY);
        ok(calls.get()==2&&oversized.turns.size()==1&&oversized.artifact==null);
        System.out.println("PASS: "+n+" conference capacity assertions across "+tested+" eligible and "+blocked+" rejected input combinations");
    }
}
