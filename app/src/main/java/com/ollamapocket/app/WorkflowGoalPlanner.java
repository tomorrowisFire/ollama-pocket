package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Untrusted model proposal -> bounded draft only. No execution or checkpoint access. */
final class WorkflowGoalPlanner {
    static final int MAX_GOAL=400, MAX_INSTRUCTION=600, MAX_FIELDS=120;
    static final int MIN_STEPS=2, MAX_STEPS=4, MAX_TOKENS=512;
    static final long MAX_MILLIS=120_000L;
    private WorkflowGoalPlanner(){}
    static String line(String raw,int limit){
        if(raw==null||raw.length()>limit||raw.trim().isEmpty())throw new IllegalArgumentException("Text limit");
        boolean visible=false;
        for(int i=0;i<raw.length();){
            int cp=raw.codePointAt(i);i+=Character.charCount(cp);
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&cp!=0x200C&&cp!=0x200D)visible=true;
            if(Character.isISOControl(cp)||cp==0x2028||cp==0x2029||
                (Character.getType(cp)==Character.FORMAT&&cp!=0x200C&&cp!=0x200D)||
                (cp>=0xD800&&cp<=0xDFFF))throw new IllegalArgumentException("Single visible line required");
        }
        if(!visible)throw new IllegalArgumentException("Visible text required");
        return raw.trim();
    }
    static String goal(String raw){return line(raw,MAX_GOAL);}
    static String prompt(String rawGoal,String language){
        String g=goal(rawGoal);
        if(!Arrays.asList("ko","en","ja","zh-Hans","zh-Hant").contains(language))
            throw new IllegalArgumentException("Language");
        return "Propose a short plan for a local text-only AI workflow. Return ONLY strict JSON: "+
            "{\"steps\":[{\"prompt\":\"task instruction\",\"fields\":\"result:string,limitations:array\"}]}. "+
            "Use 2 to 4 sequential steps. The next step sees only the previous output and original goal. "+
            "Each prompt is one line, concise, in the user's goal language (UI fallback "+language+"). "+
            "Each fields string lists 1 to 6 ASCII field names and types: string,integer,number,boolean,array,object. "+
            "Use useful content fields, not just a success flag. No code execution, tools, network, file actions or claims of testing. "+
            "Draft or review text only. Do not invent facts. The user must review the plan before any execution.\n"+
            "USER GOAL:\n"+g;
    }
    static String compile(String rawGoal,String output,boolean truncated){
        String goal=goal(rawGoal);
        if(truncated||output==null||output.length()>WorkflowPolicy.MAX_OUTPUT||
            !WorkflowJsonFields.matches("steps:array",output))throw new IllegalArgumentException("Invalid proposal");
        try{
            JSONArray array=new JSONObject(output).getJSONArray("steps");
            if(array.length()<MIN_STEPS||array.length()>MAX_STEPS)throw new IllegalArgumentException("Step count");
            List<String> lines=new ArrayList<>();
            for(int i=0;i<array.length();i++){
                JSONObject row=array.getJSONObject(i);
                if(row.length()!=2||!(row.opt("prompt") instanceof String)||!(row.opt("fields") instanceof String))
                    throw new IllegalArgumentException("Step shape");
                String instruction=line(row.getString("prompt"),MAX_INSTRUCTION);
                String spec=line(row.getString("fields"),MAX_FIELDS);
                Map<String,String> fields=WorkflowJsonFields.fields(spec);
                if(fields.size()>6)throw new IllegalArgumentException("Field count");
                String full="Goal: "+goal+" | Task: "+instruction;
                lines.add(WorkflowPlanEditor.line(full,WorkflowPolicy.Gate.JSON_FIELDS,spec));
            }
            String plan=String.join("\n",lines);
            if(WorkflowPolicy.parse(plan).size()!=array.length())throw new IllegalArgumentException("Plan round trip");
            return plan;
        }catch(Exception e){throw new IllegalArgumentException("Invalid proposed plan",e);}
    }
}
