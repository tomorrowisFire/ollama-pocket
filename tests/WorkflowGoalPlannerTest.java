package com.ollamapocket.app;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class WorkflowGoalPlannerTest {
    private static int n;
    private static void ok(boolean b){if(!b)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected);}
    private static JSONObject row(String prompt,String fields)throws Exception{return new JSONObject().put("prompt",prompt).put("fields",fields);}
    private static String plan(JSONObject... rows)throws Exception{
        JSONArray steps=new JSONArray();for(JSONObject row:rows)steps.put(row);
        return new JSONObject().put("steps",steps).toString();
    }
    public static void main(String[] args)throws Exception{
        String goal="보고서 작성 | 매출 변화 😀";
        JSONObject a=row("Identify available facts","facts:array,missing:array");
        JSONObject b=row("Draft a concise report","report:string,limitations:array");
        String good=plan(a,b);
        String compiled=WorkflowGoalPlanner.compile(" "+goal+" ",good,false);
        List<WorkflowPolicy.Step> steps=WorkflowPolicy.parse(compiled);
        ok(steps.size()==2);
        for(WorkflowPolicy.Step step:steps){
            ok(step.gate==WorkflowPolicy.Gate.JSON_FIELDS&&step.prompt.startsWith("Goal: "+goal+" | Task: "));
        }
        ok(steps.get(0).required.equals("facts:array,missing:array"));
        ok(WorkflowPolicy.verified(steps.get(1),"{\"report\":\"draft\",\"limitations\":[]}"));
        ok(!WorkflowPolicy.verified(steps.get(1),"{\"report\":\"draft\"}"));
        ok(new WorkflowPlanEditor(compiled,2).encode().equals(compiled));
        ok(WorkflowPolicy.parse(WorkflowGoalPlanner.compile(goal,plan(a,b,a,b),false)).size()==4);
        for(String lang:new String[]{"ko","en","ja","zh-Hans","zh-Hant"})
            ok(WorkflowGoalPlanner.prompt(goal,lang).contains(goal)&&WorkflowGoalPlanner.prompt(goal,lang).contains("UI fallback "+lang));
        rejects(()->WorkflowGoalPlanner.prompt(goal,"fr"));
        ok(WorkflowGoalPlanner.goal("a".repeat(400)).length()==400);
        for(String bad:new String[]{null,""," ","\u00A0","\u200D","a".repeat(401),"x\ny","x\ry","x\u0000y","x\u202Ey","x\u2028y","\uD800"})
            rejects(()->WorkflowGoalPlanner.goal(bad));
        for(String bad:new String[]{
            "", "[]","{}",good+" trailing","markdown\n"+good,
            "{\"steps\":[],\"steps\":[]}",
            "{\"steps\":[{\"prompt\":\"x\",\"prompt\":\"y\",\"fields\":\"x:string\"},{\"prompt\":\"z\",\"fields\":\"x:string\"}]}",
            "{\"steps\":[{\"prompt\":\"x\",\"fields\":\"x:string\",\"tool\":\"shell\"},{\"prompt\":\"z\",\"fields\":\"x:string\"}]}",
            "{\"steps\":[{\"prompt\":4,\"fields\":\"x:string\"},{\"prompt\":\"z\",\"fields\":\"x:string\"}]}",
            "{\"steps\":[{\"prompt\":\"x\",\"fields\":[]},{\"prompt\":\"z\",\"fields\":\"x:string\"}]}",
            "{\"steps\":[null,null]}","{\"steps\":[{},{}]}","{\"steps\":[]}","x".repeat(4001)
        })rejects(()->WorkflowGoalPlanner.compile(goal,bad,false));
        for(String bad:new String[]{plan(a),plan(a,b,a,b,a),
            plan(row("x","x:unknown"),b),plan(row("x","x:string,x:integer"),b),
            plan(row("x","x:string|@exact:OK"),b),plan(row("x",""),b),
            plan(row("x","a:string,b:string,c:string,d:string,e:string,f:string,g:string"),b),
            plan(row("line\nbreak","x:string"),b),plan(row("x\u202Ey","x:string"),b),
            plan(row("a".repeat(601),"x:string"),b)
        })rejects(()->WorkflowGoalPlanner.compile(goal,bad,false));
        rejects(()->WorkflowGoalPlanner.compile(goal,good,true));
        rejects(()->WorkflowGoalPlanner.compile(null,good,false));
        String multilingual=WorkflowGoalPlanner.compile("中文 日本語 한글",plan(row("整理資料","draft:string"),row("검토 😀","review:array")),false);
        ok(multilingual.contains("中文 日本語 한글")&&multilingual.contains("검토 😀"));
        System.out.println("PASS: "+n+" goal-to-plan draft compiler assertions");
    }
}
