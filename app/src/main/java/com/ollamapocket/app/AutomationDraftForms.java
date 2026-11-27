package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Draft-only adapters. The existing strict parsers remain the authority for review and execution. */
final class AutomationDraftForms {
    private AutomationDraftForms(){}
    static String agent(String name,String model,String digest,String instruction,String temperature,String tokens)throws Exception{
        AgentProfile profile=new AgentProfile(name,model,digest,instruction,Integer.parseInt(temperature),Integer.parseInt(tokens));
        String raw=profile.encode();
        AgentProfile.decode(raw);
        return raw;
    }
    static final class Row {
        String id,prompt,expect,next,other;
        boolean condition,contains,previous;
        Row(String id,String prompt,String expect,String next,String other,boolean condition,boolean contains,boolean previous){
            this.id=id;this.prompt=prompt;this.expect=expect;this.next=next;this.other=other;
            this.condition=condition;this.contains=contains;this.previous=previous;
        }
    }
    static final class BranchDraft {
        String model,digest;
        boolean retain;
        final List<Row> rows=new ArrayList<>();
        private final String original,baseline;
        BranchDraft(String raw,String selectedModel)throws Exception{
            original=raw;
            if(raw.trim().isEmpty()){
                model=selectedModel;digest="";rows.add(blank("step1"));baseline=null;
            }else{
                BranchWorkflowRun.Definition parsed=BranchWorkflowRun.parse(raw);
                model=parsed.model;digest=parsed.digest;retain=parsed.retainOutputs;
                for(WorkflowBranchPlan.Node n:parsed.graph.nodes()){
                    BranchWorkflowRun.Task t=parsed.tasks.get(n.id);
                    rows.add(new Row(n.id,t.prompt,t.expect,n.onTrue,n.onFalse==null?WorkflowBranchPlan.BLOCK:n.onFalse,
                            t.condition,t.contains,t.usePrevious));
                }
                baseline=canonical();
            }
        }
        private static Row blank(String id){
            return new Row(id,"","",WorkflowBranchPlan.COMPLETE,WorkflowBranchPlan.BLOCK,false,false,false);
        }
        void insertAfter(int index){
            if(rows.size()>=WorkflowBranchPlan.MAX_NODES||index<0||index>=rows.size())
                throw new IllegalArgumentException("Node limit or index");
            int n=1;String id;
            while(true){
                id="step"+n++;boolean used=false;
                for(Row row:rows)if(id.equals(row.id)){used=true;break;}
                if(!used)break;
            }
            rows.add(index+1,blank(id));
        }
        void remove(int index){
            if(rows.size()<=1)throw new IllegalArgumentException("Keep entry node");
            rows.remove(index);
        }
        private static boolean validId(String id){
            return id!=null&&id.matches("[A-Za-z][A-Za-z0-9_-]{0,31}");
        }
        List<String> targets(int index){
            if(index<0||index>=rows.size())throw new IllegalArgumentException("Node index");
            List<String> values=new ArrayList<>();
            values.add(WorkflowBranchPlan.COMPLETE);values.add(WorkflowBranchPlan.BLOCK);
            for(int i=index+1;i<rows.size();i++){
                String id=rows.get(i).id;
                if(!validId(id))continue;
                int count=0;for(Row row:rows)if(id.equals(row.id))count++;
                if(count==1)values.add(id);
            }
            return values;
        }
        /** Advisory row navigation only; the original parser still decides admission. */
        int firstInvalidRow(){
            Set<String> ids=new HashSet<>();
            for(int i=0;i<rows.size();i++){
                Row r=rows.get(i);
                if(!validId(r.id)||!ids.add(r.id))return i;
                try{
                    ReviewedLocalPolicy.text(r.prompt,2000);
                    if(!r.condition)ReviewedLocalPolicy.text(r.expect,200);
                }catch(IllegalArgumentException invalid){return i;}
                if(r.condition&&r.contains||(!retain&&(r.contains||r.previous))||i==0&&r.previous)return i;
            }
            Set<String> reached=new HashSet<>();
            if(!rows.isEmpty())reached.add(rows.get(0).id);
            for(int i=0;i<rows.size();i++){
                Row r=rows.get(i);List<String> allowed=targets(i);
                if(!reached.contains(r.id)||!allowed.contains(r.next)||
                   r.condition&&(!allowed.contains(r.other)||r.next.equals(r.other)))return i;
                reached.add(r.next);if(r.condition)reached.add(r.other);
            }
            return -1;
        }
        private String canonical()throws Exception{
            JSONArray nodes=new JSONArray();
            for(Row r:rows){
                JSONObject n=new JSONObject().put("id",r.id).put("kind",r.condition?"condition":"task").put("prompt",r.prompt);
                if(r.condition){
                    if(r.contains)throw new IllegalArgumentException("Condition cannot use text gate");
                    n.put("true",r.next).put("false",r.other);
                }else{
                    n.put("expect",r.expect).put("next",r.next);
                    if(r.contains)n.put("gate","contains");
                }
                if(r.previous)n.put("usePrevious",true);
                if(!retain&&(r.previous||r.contains))throw new IllegalArgumentException("Retention required");
                nodes.put(n);
            }
            JSONObject result=new JSONObject().put("model",model).put("modelSha256",digest).put("nodes",nodes);
            if(retain)result.put("retainOutputs",true);
            return result.toString();
        }
        String encode()throws Exception{
            String candidate=canonical();
            // A no-op edit must preserve the exact reviewed definition and durable run identity.
            if(baseline!=null&&baseline.equals(candidate))return original;
            BranchWorkflowRun.parse(candidate);
            return candidate;
        }
    }
}
