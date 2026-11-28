package com.ollamapocket.app;
import org.json.JSONObject;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeepDialogueExportTest {
    private static int checks;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        var source=new DocumentBatchPolicy.Source("자료😀.txt","RAW_ONLY_SECRET_9999 facts here",DocumentBatchPolicy.Kind.TEXT,true,0,0,0,40);
        var plan=new DocumentBatchPolicy.Plan("lookup",List.of(source));
        AtomicInteger calls=new AtomicInteger();
        var result=DocumentBatchPolicy.analyze(plan,(p,c,t)->{calls.incrementAndGet();return "generated note";},()->false,n->{});
        var session=new DeepFollowUpPolicy.Session(new DocumentBatchReport(plan,result,"fixture",null,calls.get(),7));
        try{DeepDialogueExport.json(session);throw new AssertionError("empty");}catch(IllegalArgumentException expected){checks++;}
        for(int i=0;i<26;i++)session.commit(session.prepare("facts "+i,0),"generated \"answer\" 😀 [D1-S1] [D9-S1]");
        String json=DeepDialogueExport.json(session);
        var obj=new JSONObject(json);
        ok(obj.getString("kind").equals("ONHAND_DEEP_DIALOGUE")&&!obj.getBoolean("factual_verification"));
        ok(!obj.getBoolean("raw_source_excerpts_included")&&!obj.getBoolean("resume_snapshot"));
        ok(obj.getLong("total_completed_turns")==26&&obj.getInt("retained_turns")==24&&obj.getLong("omitted_earlier_turns")==2);
        var turns=obj.getJSONArray("turns");ok(turns.length()==24&&turns.getJSONObject(0).getLong("number")==3);
        var first=turns.getJSONObject(0);ok(first.getString("question").equals("facts 2")&&first.getString("source_filter").equals("D1"));
        ok(first.getString("generated_answer").contains("\"answer\" 😀"));
        ok(first.getJSONArray("known_citation_ids").getString(0).equals("D1-S1"));
        ok(first.getJSONArray("unknown_citation_ids").getString(0).equals("D9-S1"));
        var range=first.getJSONArray("supplied_ranges").getJSONObject(0);
        ok(range.getBoolean("cited_in_answer")&&range.getString("source_id").equals("D1"));
        ok(range.getInt("start_utf16_inclusive_1_based")>=1&&range.getInt("end_utf16_inclusive_1_based")<=source.text.length());
        var sourceRow=obj.getJSONArray("sources").getJSONObject(0);
        ok(sourceRow.getBoolean("extraction_partial"));
        ok(sourceRow.getInt("planned_utf16")==plan.includedEnd(0));
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
            .digest(session.report.json().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expected=java.util.HexFormat.of().formatHex(digest);
        ok(obj.getString("original_report_sha256_utf8").equals(expected));
        ok(obj.getString("original_report_digest_scope").equals("EXPORTED_REPORT_JSON_NOT_ORIGINAL_FILE"));
        ok(DeepDialogueExport.reportDigest(session.report).equals(expected));
        var another=new DocumentBatchReport(plan,result,"fixture",null,calls.get(),8);
        ok(!DeepDialogueExport.reportDigest(another).equals(expected));
        ok(!json.contains("RAW_ONLY_SECRET_9999")&&!json.contains("generated note")&&!json.contains("exact extracted UTF-16"));
        var payload=DeepDialogueExport.prepare(session);var out=new ByteArrayOutputStream();payload.writeTo(out);
        ok(payload.format==ArtifactFileExport.Format.REPORT_JSON&&out.toString("UTF-8").equals(json));
        System.out.println("PASS: "+checks+" portable dialogue export assertions");
    }
}
