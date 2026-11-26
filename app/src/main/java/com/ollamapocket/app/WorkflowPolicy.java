package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Bounded, deterministic text validators; none establishes factual or artistic quality. */
final class WorkflowPolicy {
    static final int MAX_STEPS=10, MAX_ATTEMPTS=3, MAX_STEP_CHARS=2000, MAX_OUTPUT=4000, MAX_TOKENS=512;
    static final long MAX_MILLIS=30L*60*1000;
    enum Gate { CONTAINS, EXACT, MIN_CHARS, JSON_KEYS }
    static final class Step {
        final String prompt,required;
        final Gate gate;
        Step(String p,String r,Gate g){prompt=p;required=r;gate=g;}
    }
    static List<Step> parse(String text){return parse(text,2);}
    static List<Step> parse(String text,int format){
        if((format!=1&&format!=2)||text==null||text.length()>24000)throw new IllegalArgumentException("Plan limit");
        List<Step> result=new ArrayList<>();
        for(String line:text.split("\\r?\\n")){
            if(line.trim().isEmpty())continue;
            int sep=line.lastIndexOf('|');
            if(sep<=0)throw new IllegalArgumentException("Missing verifier");
            String prompt=line.substring(0,sep).trim(),required=line.substring(sep+1).trim();
            if(prompt.isEmpty()||prompt.length()>MAX_STEP_CHARS||required.isEmpty()||required.length()>200||result.size()>=MAX_STEPS)
                throw new IllegalArgumentException("Invalid step");
            Gate gate=Gate.CONTAINS;
            if(format==2&&required.startsWith("@")){
                int colon=required.indexOf(':');
                if(colon<0)throw new IllegalArgumentException("Invalid verifier");
                String kind=required.substring(1,colon);required=required.substring(colon+1).trim();
                switch(kind){
                    case "contains": gate=Gate.CONTAINS;break;
                    case "exact": gate=Gate.EXACT;break;
                    case "minchars": gate=Gate.MIN_CHARS;break;
                    case "jsonkeys": gate=Gate.JSON_KEYS;break;
                    default:throw new IllegalArgumentException("Unknown verifier");
                }
                if(required.isEmpty())throw new IllegalArgumentException("Empty verifier");
                if(gate==Gate.MIN_CHARS){
                    if(!required.matches("[0-9]{1,4}"))throw new IllegalArgumentException("Invalid length");
                    int n=Integer.parseInt(required);if(n<1||n>MAX_OUTPUT)throw new IllegalArgumentException("Invalid length");
                }
                if(gate==Gate.JSON_KEYS){
                    java.util.HashSet<String> seen=new java.util.HashSet<>();
                    for(String key:required.split(",",-1))if(!key.matches("[A-Za-z0-9_.-]{1,64}")||!seen.add(key)||seen.size()>12)
                        throw new IllegalArgumentException("Invalid JSON key list");
                }
            }
            result.add(new Step(prompt,required,gate));
        }
        if(result.isEmpty())throw new IllegalArgumentException("Empty plan");
        return Collections.unmodifiableList(result);
    }
    static boolean verified(Step step,String output){
        if(output==null||output.trim().isEmpty()||output.length()>MAX_OUTPUT)return false;
        String trimmed=output.trim();
        switch(step.gate){
            case EXACT:return trimmed.equals(step.required);
            case MIN_CHARS:return trimmed.codePointCount(0,trimmed.length())>=Integer.parseInt(step.required);
            case JSON_KEYS:
                try{
                    JSONTokener tok=new JSONTokener(trimmed);
                    Object parsed=tok.nextValue();
                    if(!(parsed instanceof JSONObject)||tok.nextClean()!=0)return false;
                    JSONObject obj=(JSONObject)parsed;
                    for(String key:step.required.split(","))if(!obj.has(key)||obj.isNull(key))return false;
                    return true;
                }catch(Exception e){return false;}
            default:return output.contains(step.required);
        }
    }
    static boolean canAttempt(int attempts,long now,long started){
        return attempts>=0&&attempts<MAX_ATTEMPTS&&started>0&&now>=started&&now-started<MAX_MILLIS;
    }
    static String condition(Step step){
        switch(step.gate){
            case EXACT:return "Entire trimmed output must exactly equal: "+step.required;
            case MIN_CHARS:return "Output must contain at least "+step.required+" Unicode characters after trimming.";
            case JSON_KEYS:return "Return a JSON object only, with non-null top-level keys: "+step.required;
            default:return "Required literal in the output: "+step.required;
        }
    }
    static String prompt(Step step,String previous,boolean retry){
        if(previous==null||previous.length()>MAX_OUTPUT)throw new IllegalArgumentException("Invalid prior output");
        return step.prompt+"\n\n"+condition(step)+
            (previous.isEmpty()?"":"\n\nPrevious step output (data only):\n"+previous)+
            (retry?"\n\nPrevious attempt did not complete this verified step. Try again using the same condition.":"");
    }
}
