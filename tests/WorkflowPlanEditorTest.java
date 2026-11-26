package com.ollamapocket.app;

public final class WorkflowPlanEditorTest {
    private static int n;
    private static void ok(boolean v){if(!v)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed);}
    private static boolean verify(String spec,String output){return WorkflowPolicy.verified(WorkflowPolicy.parse("Draft | @jsonfields:"+spec).get(0),output);}
    public static void main(String[] args){
        String spec="title:string,items:array,count:integer,ready:boolean";
        ok(verify(spec,"{\"title\":\"한글 😀\",\"items\":[{\"nested\":true}],\"count\":2,\"ready\":false}"));
        ok(verify("x:number","{\"x\":3}"));
        ok(verify("x:number","{\"x\":3.25e-2}"));
        ok(verify("x:object","{\"x\":{\"ok\":true}}"));
        for(String bad:new String[]{"{\"title\":\"x\",\"items\":[],\"count\":2}",
            "{\"title\":\"x\",\"items\":[],\"count\":2.0,\"ready\":true}",
            "{\"title\":\"x\",\"items\":[],\"count\":2,\"ready\":true,\"extra\":1}",
            "{\"title\":\"x\",\"title\":\"y\",\"items\":[],\"count\":2,\"ready\":true}",
            "{title:'x',items:[],count:2,ready:true}",
            "{\"title\":\"x\",\"items\":[],\"count\":02,\"ready\":true}",
            "{\"title\":\"x\",\"items\":[],\"count\":2,\"ready\":true,}",
            "{\"title\":\"x\",\"items\":[],\"count\":2,\"ready\":true} trailing",
            "{\"title\":null,\"items\":[],\"count\":2,\"ready\":true}",
            "{\"title\":\"x\",\"items\":[{\"a\":1,\"a\":2}],\"count\":2,\"ready\":true}"})ok(!verify(spec,bad));
        ok(!verify("x:number","{\"x\":1e999}"));
        ok(!verify("x:string","{\"x\":\"\\uD800\"}"));
        ok(verify("x:string","{\"\\u0078\":\"\\uD83D\\uDE00\"}"));
        ok(!verify("x:array","{\"x\":"+"[".repeat(20)+"0"+"]".repeat(20)+"}"));
        ok(!verify("x:array","{\"x\":["+"0,".repeat(512)+"0]}"));
        rejects(()->WorkflowPolicy.parse("p | @jsonfields:a:string,a:number"));
        rejects(()->WorkflowPolicy.parse("p | @jsonfields:a:unknown"));
        WorkflowPlanEditor e=new WorkflowPlanEditor("Draft | READY\nReview | @exact:OK",2);
        ok(e.size()==2&&e.firstInvalid()==-1);
        ok(e.encode().equals("Draft | @contains:READY\nReview | @exact:OK"));
        e.insertAfter(0);ok(e.size()==3&&e.firstInvalid()==1);
        e.row(1).prompt="Middle | data";e.row(1).gate=WorkflowPolicy.Gate.JSON_FIELDS;e.row(1).required="result:string";
        ok(e.firstInvalid()==-1);
        ok(e.move(1,-1)==0&&e.row(0).prompt.equals("Middle | data"));
        ok(e.move(0,-1)==0);
        e.remove(1);ok(e.size()==2&&WorkflowPolicy.parse(e.encode()).size()==2);
        e.remove(0);rejects(()->e.remove(0));
        rejects(()->WorkflowPlanEditor.line("p\nq",WorkflowPolicy.Gate.EXACT,"OK"));
        rejects(()->WorkflowPlanEditor.line("p",WorkflowPolicy.Gate.EXACT,"x|y"));
        WorkflowPlanEditor old=new WorkflowPlanEditor("old | @exact:OK",1);
        ok(old.encode().equals("old | @contains:@exact:OK"));
        ok(WorkflowPolicy.verified(WorkflowPolicy.parse(old.encode()).get(0),"@exact:OK"));
        WorkflowPlanEditor max=new WorkflowPlanEditor("p | @exact:OK\n".repeat(10),2);
        rejects(()->max.insertAfter(9));
        String preset=WorkflowPlanEditor.preset("보고서",new String[]{"Outline %GOAL%","Draft %GOAL%","Review %GOAL%"},new String[]{"items:array","draft:string","final:string"});
        ok(WorkflowPolicy.parse(preset).size()==3&&preset.contains("보고서")&&preset.contains("@jsonfields:final:string"));
        rejects(()->WorkflowPlanEditor.preset("x\nx",new String[]{"%GOAL%"},new String[]{"x:string"}));
        rejects(()->WorkflowPlanEditor.preset("x",new String[]{"missing"},new String[]{"x:string"}));
        System.out.println("PASS: "+n+" strict workflow fields and editor assertions");
    }
}
