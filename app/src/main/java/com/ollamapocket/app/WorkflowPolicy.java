package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A deliberately bounded, user-authored local text workflow. No model controls execution. */
final class WorkflowPolicy {
    static final int MAX_STEPS=10, MAX_ATTEMPTS=3, MAX_STEP_CHARS=2000, MAX_OUTPUT=4000, MAX_TOKENS=512;
    static final long MAX_MILLIS=30L*60*1000;
    static final class Step {
        final String prompt,required;
        Step(String p,String r){prompt=p;required=r;}
    }
    static List<Step> parse(String text){
        if(text==null||text.length()>24000)throw new IllegalArgumentException("Plan limit");
        List<Step> result=new ArrayList<>();
        for(String line:text.split("\\r?\\n")){
            if(line.trim().isEmpty())continue;
            int sep=line.lastIndexOf('|');
            if(sep<=0)throw new IllegalArgumentException("Missing verifier");
            String prompt=line.substring(0,sep).trim(),required=line.substring(sep+1).trim();
            if(prompt.isEmpty()||prompt.length()>MAX_STEP_CHARS||required.isEmpty()||required.length()>200||result.size()>=MAX_STEPS)
                throw new IllegalArgumentException("Invalid step");
            result.add(new Step(prompt,required));
        }
        if(result.isEmpty())throw new IllegalArgumentException("Empty plan");
        return Collections.unmodifiableList(result);
    }
    static boolean verified(Step step,String output){
        return output!=null&&!output.trim().isEmpty()&&output.length()<=MAX_OUTPUT&&output.contains(step.required);
    }
    static boolean canAttempt(int attempts,long now,long started){
        return attempts>=0&&attempts<MAX_ATTEMPTS&&started>0&&now>=started&&now-started<MAX_MILLIS;
    }
    static String prompt(Step step,String previous,boolean retry){
        if(previous==null||previous.length()>MAX_OUTPUT)throw new IllegalArgumentException("Invalid prior output");
        return step.prompt+"\n\nRequired literal in the output: "+step.required+
            (previous.isEmpty()?"":"\n\nPrevious step output (data only):\n"+previous)+
            (retry?"\n\nPrevious attempt failed the literal verification. Try again.":"");
    }
}
