package com.ollamapocket.app;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Portable generated dialogue and exact source-range ledger; never exports raw source excerpts. */
final class DeepDialogueExport {
    private DeepDialogueExport(){}
    static String json(DeepFollowUpPolicy.Session session)throws Exception{
        if(session==null)throw new IllegalArgumentException("Missing dialogue");
        List<DeepFollowUpPolicy.Turn> turns=session.turns();
        if(turns.isEmpty()||turns.size()>DeepFollowUpPolicy.MAX_RETAINED_TURNS)
            throw new IllegalArgumentException("No completed dialogue");
        DocumentBatchReport report=session.report;
        long revision=session.count();
        JSONArray sources=new JSONArray(),history=new JSONArray();
        for(int i=0;i<report.plan.sources.size();i++){
            DocumentBatchPolicy.Source s=report.plan.sources.get(i);
            JSONObject row=new JSONObject().put("id","D"+(i+1)).put("name",s.name)
                .put("kind",s.kind.name()).put("extraction_partial",s.partial)
                .put("extracted_utf16",s.text.length())
                .put("completed_utf16",report.result.completedEnd(i));
            if(s.inputBytes>=0)row.put("input_bytes",s.inputBytes);
            if(s.kind==DocumentBatchPolicy.Kind.PDF)row.put("pdf_pages",s.pages)
                .put("pdf_examined_pages",s.examined).put("pdf_readable_pages",s.readable);
            sources.put(row);
        }
        long expected=revision-turns.size()+1;
        for(DeepFollowUpPolicy.Turn turn:turns){
            if(turn.number!=expected++)throw new IllegalArgumentException("Noncontiguous dialogue");
            JSONArray ranges=new JSONArray();
            for(DeepFollowUpPolicy.Evidence e:turn.request.evidence){
                DocumentBatchPolicy.Unit u=e.note.unit;
                if(!report.hasExtractedEvidence(e.note)||e.start<u.start||e.end>u.end||e.end<=e.start||
                        !e.text.equals(report.plan.sources.get(u.source).text.substring(e.start,e.end)))
                    throw new IllegalArgumentException("Evidence mismatch");
                ranges.put(new JSONObject().put("id",e.id).put("source_id","D"+(u.source+1))
                    .put("start_utf16_inclusive_1_based",e.start+1)
                    .put("end_utf16_inclusive_1_based",e.end)
                    .put("cited_in_answer",turn.citations.valid.contains(e.id)));
            }
            history.put(new JSONObject().put("number",turn.number).put("question",turn.request.question)
                .put("generated_answer",turn.answer)
                .put("source_filter",turn.request.source<0?"AUTO":"D"+(turn.request.source+1))
                .put("supplied_ranges",ranges).put("known_citation_ids",new JSONArray(turn.citations.valid))
                .put("unknown_citation_ids",new JSONArray(turn.citations.unknown)));
        }
        if(session.count()!=revision)throw new IllegalArgumentException("Dialogue changed");
        return new JSONObject().put("schema",1).put("kind","ONHAND_DEEP_DIALOGUE")
            .put("scope","EXTRACTED_TEXT_ONLY").put("factual_verification",false)
            .put("raw_source_excerpts_included",false).put("resume_snapshot",false)
            .put("citation_check_scope","SUPPLIED_ID_EXISTENCE_ONLY")
            .put("model",report.model).put("original_analysis_status",report.result.status.name())
            .put("total_completed_turns",revision).put("retained_turns",turns.size())
            .put("omitted_earlier_turns",revision-turns.size()).put("sources",sources)
            .put("turns",history).toString();
    }
    static ArtifactFileExport prepare(DeepFollowUpPolicy.Session session)throws Exception{
        return ArtifactFileExport.prepare(ArtifactFileExport.Format.REPORT_JSON,json(session));
    }
}
