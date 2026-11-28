package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Draft-only editor adapter. No model call, storage or checkpoint writes. */
final class WorkflowPlanEditor {
    static final class Row {
        String prompt,required;
        WorkflowPolicy.Gate gate;
        Row(String prompt,WorkflowPolicy.Gate gate,String required){this.prompt=prompt;this.gate=gate;this.required=required;}
    }
    private final List<Row> rows=new ArrayList<>();
    private final List<WorkflowPolicy.Step> initial;
    WorkflowPlanEditor(String source,int format){
        if(source==null)throw new IllegalArgumentException("Missing draft");
        initial=source.trim().isEmpty()?Collections.emptyList():WorkflowPolicy.parse(source,format);
        for(WorkflowPolicy.Step step:initial)rows.add(new Row(step.prompt,step.gate,step.required));
        if(rows.isEmpty())rows.add(blank());
    }
    /** A no-op visual edit must not reformat a reviewed plan or invalidate its resume identity. */
    boolean unchanged(){
        if(rows.size()!=initial.size())return false;
        for(int i=0;i<rows.size();i++){
            Row r=rows.get(i);WorkflowPolicy.Step s=initial.get(i);
            if(r.gate!=s.gate||!s.prompt.equals(r.prompt)||!s.required.equals(r.required))return false;
        }
        return true;
    }
    private static Row blank(){return new Row("",WorkflowPolicy.Gate.MIN_CHARS,"80");}
    int size(){return rows.size();}
    Row row(int index){return rows.get(index);}
    void insertAfter(int index){if(rows.size()>=WorkflowPolicy.MAX_STEPS)throw new IllegalArgumentException("Step limit");rows.add(index+1,blank());}
    void remove(int index){if(rows.size()<=1)throw new IllegalArgumentException("Keep one step");rows.remove(index);}
    int move(int index,int delta){
        if(delta!=1&&delta!=-1)throw new IllegalArgumentException("Invalid direction");
        int next=index+delta;
        if(next<0||next>=rows.size())return index;
        Collections.swap(rows,index,next);return next;
    }
    static String line(String prompt,WorkflowPolicy.Gate gate,String required){
        if(prompt==null||required==null||gate==null||prompt.indexOf('\r')>=0||prompt.indexOf('\n')>=0||
            required.indexOf('\r')>=0||required.indexOf('\n')>=0||required.indexOf('|')>=0)throw new IllegalArgumentException("One line per step");
        String directive;
        switch(gate){
            case EXACT:directive="exact";break;
            case MIN_CHARS:directive="minchars";break;
            case JSON_KEYS:directive="jsonkeys";break;
            case JSON_FIELDS:directive="jsonfields";break;
            case JSON_VALUES:directive="jsonvalues";break;
            default:directive="contains";
        }
        String value=prompt.trim()+" | @"+directive+":"+required.trim();
        List<WorkflowPolicy.Step> parsed=WorkflowPolicy.parse(value);
        if(parsed.size()!=1||parsed.get(0).gate!=gate||!parsed.get(0).prompt.equals(prompt.trim())||
            !parsed.get(0).required.equals(required.trim()))throw new IllegalArgumentException("Invalid step");
        return value;
    }
    static WorkflowPolicy.Sample sample(Row row,String answer,boolean truncated){
        if(row==null)return WorkflowPolicy.Sample.INVALID_CONDITION;
        try{
            WorkflowPolicy.Step step=WorkflowPolicy.parse(line(row.prompt,row.gate,row.required)).get(0);
            return WorkflowPolicy.sample(step,answer,truncated);
        }catch(IllegalArgumentException e){return WorkflowPolicy.Sample.INVALID_CONDITION;}
    }
    int firstInvalid(){
        for(int i=0;i<rows.size();i++)try{Row r=rows.get(i);line(r.prompt,r.gate,r.required);}catch(IllegalArgumentException e){return i;}
        return -1;
    }
    String encode(){
        List<String> out=new ArrayList<>();
        for(Row r:rows)out.add(line(r.prompt,r.gate,r.required));
        String plan=String.join("\n",out);WorkflowPolicy.parse(plan);return plan;
    }
    static String preset(String goal,String[] prompts,String[] fields){
        if(goal==null||goal.trim().isEmpty()||goal.length()>1000||goal.indexOf('\n')>=0||goal.indexOf('\r')>=0||
            prompts==null||fields==null||prompts.length!=fields.length||prompts.length==0||prompts.length>WorkflowPolicy.MAX_STEPS)
            throw new IllegalArgumentException("Preset goal");
        List<String> lines=new ArrayList<>();
        for(int i=0;i<prompts.length;i++){
            if(prompts[i]==null||!prompts[i].contains("%GOAL%"))throw new IllegalArgumentException("Preset prompt");
            lines.add(line(prompts[i].replace("%GOAL%",goal.trim()),WorkflowPolicy.Gate.JSON_FIELDS,fields[i]));
        }
        String plan=String.join("\n",lines);WorkflowPolicy.parse(plan);return plan;
    }
}
