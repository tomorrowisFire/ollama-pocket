package com.ollamapocket.app;

import java.util.List;
import org.json.JSONObject;

public final class DocumentBatchReportTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed);}
    public static void main(String[] args)throws Exception{
        String source="ORIGINAL-PRIVATE-MARKER"+"a".repeat(1400);
        DocumentBatchPolicy.Source text=new DocumentBatchPolicy.Source("quote\".txt",source,DocumentBatchPolicy.Kind.TEXT,true,0,0,0);
        DocumentBatchPolicy.Source pdf=new DocumentBatchPolicy.Source("日本語.pdf","PDF-EXTRACTED",DocumentBatchPolicy.Kind.PDF,true,40,12,10);
        DocumentBatchPolicy.Plan plan=new DocumentBatchPolicy.Plan("Compare \"values\" 中文😀",List.of(text,pdf));
        ok(plan.units.size()==3);
        // The analysis policy trims outer whitespace/control characters. The export
        // must preserve the recorded note, including internal control characters.
        String generated="note \"quoted\"\n한글 😀 \u0000 inside";
        String rawGenerated="  "+generated+" \u0000";
        ok(DocumentBatchPolicy.checked(rawGenerated,DocumentBatchPolicy.NOTE_CHARS).equals(generated));
        DocumentBatchPolicy.Result full=DocumentBatchPolicy.analyze(plan,(prompt,chars,tokens)->
            prompt.contains("BEGIN SOURCE")?rawGenerated:prompt.contains("final analysis")?"Summary [D1-S1, D2-S1] [D9-S1]":"summary",
            ()->false,x->{});
        ok(full.status==DocumentBatchPolicy.Status.COMPLETED);
        DocumentBatchReport report=new DocumentBatchReport(plan,full,"model:test",null,5,1234);
        ok(report.completed.size()==3);
        ok(report.lookup("D1-S1")!=null&&report.lookup("D2-S1")!=null);
        ok(report.lookup("D3-S1")==null&&report.lookup("D01-S1")==null&&report.lookup("d1-s1")==null&&report.lookup(null)==null);
        DocumentBatchPolicy.Note first=report.lookup("D1-S1");
        ok(report.extracted(first).equals(source.substring(0,1400)));
        ok(report.hasExtractedEvidence(first)&&first.text.equals(generated));
        JSONObject j=new JSONObject(report.json());
        ok(j.getInt("schema")==1&&j.getString("status").equals("COMPLETED")&&j.isNull("stop_reason"));
        ok(j.getString("model").equals("model:test")&&j.getString("goal").equals(plan.goal));
        ok(j.getInt("planned_excerpts")==3&&j.getInt("completed_excerpts")==3&&j.getInt("completed_reduction_groups")==1);
        ok(j.getLong("analysis_elapsed_ms")==1234&&j.getInt("local_chat_request_attempts")==5);
        ok(j.getBoolean("elapsed_excludes_final_cleanup")&&!j.getBoolean("factual_verification"));
        JSONObject d=j.getJSONArray("sources").getJSONObject(0),p=j.getJSONArray("sources").getJSONObject(1);
        ok(d.getString("name").equals("quote\".txt")&&d.getBoolean("extraction_partial"));
        ok(d.getInt("extracted_utf16")==source.length()&&d.getInt("planned_utf16")==source.length()&&d.getInt("completed_utf16")==source.length());
        ok(p.getInt("pdf_pages")==40&&p.getInt("pdf_examined_pages")==12&&p.getInt("pdf_readable_pages")==10);
        JSONObject note=j.getJSONArray("notes").getJSONObject(0);
        ok(note.getString("note").equals(generated)&&note.getString("source_id").equals("D1"));
        ok(note.getInt("start_utf16_inclusive_1_based")==1&&note.getInt("end_utf16_inclusive_1_based")==1400);
        ok(j.getJSONArray("known_citation_ids").length()==2&&j.getJSONArray("unknown_citation_ids").getString(0).equals("D9-S1"));
        ok(j.getString("synthesis").equals(full.synthesis));
        ok(!report.json().contains("ORIGINAL-PRIVATE-MARKER")&&!note.has("source_text")&&!d.has("source_text"));
        DocumentBatchPolicy.Result stopped=new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.STOPPED,List.of(first),0,"");
        DocumentBatchReport partial=new DocumentBatchReport(plan,stopped,"model:test","USER",2,100);
        JSONObject pj=new JSONObject(partial.json());
        ok(pj.getString("status").equals("STOPPED")&&pj.getString("stop_reason").equals("USER")&&pj.isNull("synthesis"));
        ok(partial.lookup("D1-S2")==null&&partial.lookup("D2-S1")==null&&partial.lookup("D1-S1")==first);
        ok(pj.getJSONArray("sources").getJSONObject(0).getInt("completed_utf16")==1400&&pj.getJSONArray("sources").getJSONObject(1).getInt("completed_utf16")==0);
        ok(pj.getJSONArray("known_citation_ids").length()==0&&pj.getJSONArray("unknown_citation_ids").length()==0);
        DocumentBatchPolicy.Result failed=new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.FAILED,full.notes,1,"",true);
        rejects(()->new DocumentBatchReport(plan,failed,"m",null,3,200));
        JSONObject fj=new JSONObject(new DocumentBatchReport(plan,failed,"m",null,5,200).json());
        ok(fj.getString("status").equals("FAILED")&&fj.getBoolean("context_limited")&&fj.isNull("synthesis"));
        rejects(()->new DocumentBatchReport(plan,stopped,"m",null,1,0));
        rejects(()->new DocumentBatchReport(plan,full,"m","USER",5,0));
        rejects(()->new DocumentBatchReport(plan,full,"m",null,49,0));
        rejects(()->new DocumentBatchReport(plan,full,"m",null,4,0));
        rejects(()->new DocumentBatchReport(plan,full,"m",null,6,0));
        rejects(()->new DocumentBatchReport(plan,stopped,"m","USER",0,0));
        ok(new DocumentBatchReport(plan,stopped,"m","USER",1,0).requests==1);
        rejects(()->new DocumentBatchReport(plan,full,"m",null,5,-1));
        rejects(()->new DocumentBatchReport(plan,full,"",null,5,0));
        rejects(()->new DocumentBatchReport(plan,new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.COMPLETED,List.of(first),0,"fake"),"m",null,1,0));
        rejects(()->new DocumentBatchReport(plan,new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.STOPPED,List.of(full.notes.get(1)),0,""),"m","USER",1,0));
        rejects(()->new DocumentBatchReport(plan,new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.FAILED,List.of(first),0,"fake"),"m",null,1,0));
        DocumentBatchPolicy.Plan other=new DocumentBatchPolicy.Plan(plan.goal,plan.sources);
        rejects(()->new DocumentBatchReport(other,full,"m",null,5,0));
        rejects(()->partial.extracted(full.notes.get(1)));
        rejects(()->DocumentBatchReport.boundedJson("x".repeat(DocumentBatchReport.MAX_JSON_CHARS+1)));
        ok(DocumentBatchReport.boundedJson("x".repeat(DocumentBatchReport.MAX_JSON_CHARS)).length()==DocumentBatchReport.MAX_JSON_CHARS);
        DocumentBatchPolicy.Plan ws=new DocumentBatchPolicy.Plan("extract",List.of(
            new DocumentBatchPolicy.Source("ws.txt"," ".repeat(1400)+"end",DocumentBatchPolicy.Kind.TEXT,false,0,0,0)));
        ok(ws.units.size()==2);
        DocumentBatchPolicy.Result wr=DocumentBatchPolicy.analyze(ws,(prompt,chars,tokens)->"ok [D1-S1] [D1-S2]",()->false,x->{});
        DocumentBatchReport w=new DocumentBatchReport(ws,wr,"m",null,3,10);
        ok(w.lookup("D1-S1")!=null&&!w.hasExtractedEvidence(w.lookup("D1-S1"))&&w.extracted(w.lookup("D1-S1")).equals(" ".repeat(1400)));
        ok(w.requests==3); // whitespace-only record does not consume a map request
        rejects(()->new DocumentBatchReport(ws,wr,"m",null,2,10));
        JSONObject wj=new JSONObject(w.json());
        ok(wj.getJSONArray("notes").getJSONObject(0).getString("record_kind").equals("WHITESPACE_ONLY")&&wj.getJSONArray("notes").getJSONObject(0).isNull("note"));
        ok(w.citations.unknown.equals(List.of("D1-S1"))&&w.citations.valid.equals(List.of("D1-S2")));
        DocumentBatchPolicy.Plan singleton=new DocumentBatchPolicy.Plan("extract",List.of(
            new DocumentBatchPolicy.Source("short.txt","short evidence",DocumentBatchPolicy.Kind.TEXT,false,0,0,0)));
        DocumentBatchPolicy.Result direct=DocumentBatchPolicy.analyze(singleton,(prompt,chars,tokens)->"note [D1-S1]",()->false,x->{});
        ok(direct.status==DocumentBatchPolicy.Status.COMPLETED&&direct.groupsDone==0);
        JSONObject sj=new JSONObject(new DocumentBatchReport(singleton,direct,"m",null,2,10).json());
        ok(sj.getInt("local_chat_request_attempts")==2&&sj.getInt("completed_reduction_groups")==0);
        rejects(()->new DocumentBatchReport(singleton,direct,"m",null,3,10));
        System.out.println("PASS: "+n+" document report snapshot, citation lookup and JSON assertions");
    }
}
