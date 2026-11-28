package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Bounded, deterministic text validators; none establishes factual or artistic quality. */
final class WorkflowPolicy {
    static final int MAX_STEPS=10, MAX_ATTEMPTS=3, MAX_STEP_CHARS=2000, MAX_OUTPUT=4000, MAX_TOKENS=512;
    static final int MAX_TOTAL_ATTEMPTS=MAX_STEPS*MAX_ATTEMPTS;
    static final long MAX_MILLIS=30L*60*1000;
    enum Gate { CONTAINS, EXACT, MIN_CHARS, JSON_KEYS, JSON_FIELDS, JSON_VALUES }
    enum Decision { ADVANCE, RETRY, BLOCK }
    enum Sample { MATCH, MISMATCH, EMPTY, OVERSIZED, TRUNCATED, INVALID_CONDITION }
    enum BudgetState { READY, COMPLETE, CANCELLED, CLOCK, EXPIRED, ATTEMPTS, INVALID }
    static final class Budget {
        final int stepAttempts,totalAttempts;
        final long millis;
        final BudgetState state;
        Budget(int stepAttempts,int totalAttempts,long millis,BudgetState state){
            this.stepAttempts=stepAttempts;this.totalAttempts=totalAttempts;this.millis=millis;this.state=state;
        }
        boolean allowed(){return state==BudgetState.READY;}
    }
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
                    case "jsonfields": gate=Gate.JSON_FIELDS;break;
                    case "jsonvalues": gate=Gate.JSON_VALUES;break;
                    default:throw new IllegalArgumentException("Unknown verifier");
                }
                if(required.isEmpty())throw new IllegalArgumentException("Empty verifier");
                if(gate==Gate.MIN_CHARS){
                    if(!required.matches("[0-9]{1,4}"))throw new IllegalArgumentException("Invalid length");
                    int n=Integer.parseInt(required);if(n<1||n>MAX_OUTPUT)throw new IllegalArgumentException("Invalid length");
                }
                if(gate==Gate.JSON_FIELDS)WorkflowJsonFields.fields(required);
                if(gate==Gate.JSON_VALUES)WorkflowJsonFields.validateValues(required);
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
            case JSON_FIELDS:return WorkflowJsonFields.matches(step.required,output);
            case JSON_VALUES:return WorkflowJsonFields.matchesValues(step.required,output);
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
    private static boolean validCondition(Step step){
        if(step==null||step.gate==null||step.required==null||step.required.trim().isEmpty()||step.required.length()>200)return false;
        try{
            switch(step.gate){
                case MIN_CHARS:
                    if(!step.required.matches("[0-9]{1,4}"))return false;
                    int n=Integer.parseInt(step.required);return n>=1&&n<=MAX_OUTPUT;
                case JSON_FIELDS:WorkflowJsonFields.fields(step.required);return true;
                case JSON_VALUES:WorkflowJsonFields.validateValues(step.required);return true;
                case JSON_KEYS:
                    java.util.Set<String> keys=new java.util.HashSet<>();
                    for(String key:step.required.split(",",-1))
                        if(!key.matches("[A-Za-z0-9_.-]{1,64}")||!keys.add(key)||keys.size()>12)return false;
                    return true;
                default:return true;
            }
        }catch(IllegalArgumentException e){return false;}
    }
    /** One predicate for the actual runner and the read-only sample lab. */
    static Sample sample(Step step,String output,boolean truncated){
        if(!validCondition(step))return Sample.INVALID_CONDITION;
        if(truncated)return Sample.TRUNCATED;
        if(output==null)return Sample.EMPTY;
        if(output.length()>MAX_OUTPUT)return Sample.OVERSIZED;
        if(output.trim().isEmpty())return Sample.EMPTY;
        try{return verified(step,output)?Sample.MATCH:Sample.MISMATCH;}
        catch(IllegalArgumentException e){return Sample.INVALID_CONDITION;}
    }
    /** Shared runner decision: never accept truncated, invalid or unbudgeted attempts. */
    static Decision decide(Step step,String output,boolean truncated,int attempts){
        if(attempts<1||attempts>MAX_ATTEMPTS)return Decision.BLOCK;
        Sample result=sample(step,output,truncated);
        if(result==Sample.MATCH)return Decision.ADVANCE;
        if(result==Sample.INVALID_CONDITION)return Decision.BLOCK;
        return attempts<MAX_ATTEMPTS?Decision.RETRY:Decision.BLOCK;
    }
    /** Read-only allowance. Legacy counters can be incomplete; never invent missing history. */
    static Budget budget(String status,int steps,int completed,int attempts,int total,long started,long updated,long now){
        if(!java.util.Arrays.asList("QUEUED","RUNNING","VERIFYING","RETRY_WAIT","PAUSED","BLOCKED","COMPLETED","CANCELLED").contains(status)||
            steps<1||steps>MAX_STEPS||completed<0||completed>steps||attempts<0||attempts>MAX_ATTEMPTS||
            total<0||total>MAX_TOTAL_ATTEMPTS||started<=0||updated<0||(updated!=0&&updated<started)||
            ("COMPLETED".equals(status)!=(completed==steps)))
            return new Budget(0,0,0,BudgetState.INVALID);
        if("COMPLETED".equals(status))return new Budget(0,0,0,BudgetState.COMPLETE);
        if("CANCELLED".equals(status))return new Budget(0,0,0,BudgetState.CANCELLED);
        if(now<started||now<updated)return new Budget(0,0,0,BudgetState.CLOCK);
        long elapsed=now-started;
        if(elapsed>=MAX_MILLIS)return new Budget(0,0,0,BudgetState.EXPIRED);
        int remainingTotal=MAX_TOTAL_ATTEMPTS-total;
        int remainingStep=Math.min(MAX_ATTEMPTS-attempts,remainingTotal);
        return new Budget(remainingStep,remainingTotal,MAX_MILLIS-elapsed,
            remainingStep>0?BudgetState.READY:BudgetState.ATTEMPTS);
    }
    /** Compare the reviewed snapshot to a freshly decoded durable checkpoint and exact draft/model. */
    static boolean reviewedResume(String reviewed,String current,String plan,String draft,String model,String selected){
        return reviewed!=null&&!reviewed.isEmpty()&&reviewed.equals(current)&&
            plan!=null&&plan.equals(draft)&&model!=null&&!model.isEmpty()&&model.equals(selected);
    }
    static boolean canAttempt(int attempts,long now,long started){
        return attempts>=0&&attempts<MAX_ATTEMPTS&&started>0&&now>=started&&now-started<MAX_MILLIS;
    }
    static String condition(Step step){
        switch(step.gate){
            case EXACT:return "Entire trimmed output must exactly equal: "+step.required;
            case MIN_CHARS:return "Output must contain at least "+step.required+" Unicode characters after trimming.";
            case JSON_KEYS:return "Return a JSON object only, with non-null top-level keys: "+step.required;
            case JSON_FIELDS:return "Return exactly one strict JSON object, no markdown, no duplicate or extra fields, using these top-level field names and types: "+step.required+". Integer means an integer literal. Do not invent unknown facts.";
            case JSON_VALUES:return "Return exactly one strict JSON object matching these required top-level scalar values and types: "+step.required+". No extra fields, duplicate keys, nulls, arrays, objects or markdown. Integer literals and fractional/exponent numbers are distinct types. If the task cannot meet the condition, do not invent facts.";
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
