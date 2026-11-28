package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/** In-memory, read-only snapshot of the actual completed prefix of one analysis plan. */
final class DocumentBatchReport {
    static final int MAX_JSON_CHARS=120000;
    final DocumentBatchPolicy.Plan plan;
    final DocumentBatchPolicy.Result result;
    final String model,stopReason;
    final int requests;
    final long elapsedMillis;
    final List<DocumentBatchPolicy.Note> completed;
    final DocumentBatchPolicy.Citations citations;

    DocumentBatchReport(DocumentBatchPolicy.Plan plan,DocumentBatchPolicy.Result result,String model,
                        String stopReason,int requests,long elapsedMillis){
        if(plan==null||result==null||result.status==null||!AttachmentText.hasContent(model)||model.length()>256||
            requests<0||requests>plan.maxTotal()||elapsedMillis<0)
            throw new IllegalArgumentException("Report metadata");
        if(result.notes.size()>plan.units.size()||result.groupsDone<0||
            result.groupsDone>Math.max(1,plan.units.size()-1)||
            (result.groupsDone>0&&result.notes.size()!=plan.units.size())||
            result.synthesis==null)throw new IllegalArgumentException("Report scope");
        boolean success=result.status==DocumentBatchPolicy.Status.COMPLETED;
        if(success){
            if(result.notes.size()!=plan.units.size()||result.contextLimited||!AttachmentText.hasContent(result.synthesis)||
                result.synthesis.length()>DocumentBatchPolicy.FINAL_CHARS)
                throw new IllegalArgumentException("Incomplete synthesis");
        }else if(!result.synthesis.isEmpty()||(result.contextLimited&&result.status!=DocumentBatchPolicy.Status.FAILED))
            throw new IllegalArgumentException("Invalid partial result");
        if(result.status==DocumentBatchPolicy.Status.STOPPED){
            if(!Arrays.asList("USER","DEADLINE","THERMAL","MEMORY","SERVER","COOP","UNKNOWN").contains(stopReason))
                throw new IllegalArgumentException("Stop reason");
        }else if(stopReason!=null)throw new IllegalArgumentException("Unexpected stop reason");
        List<DocumentBatchPolicy.Note> notes=new ArrayList<>();
        int recordedRequests=result.groupsDone+(success?1:0);
        for(int i=0;i<result.notes.size();i++){
            DocumentBatchPolicy.Note note=result.notes.get(i);
            // Only the actual unit instance from this immutable plan is accepted.
            if(note==null||note.unit!=plan.units.get(i)||!AttachmentText.hasContent(note.text)||note.text.length()>DocumentBatchPolicy.NOTE_CHARS)
                throw new IllegalArgumentException("Invalid completed prefix");
            notes.add(note);
            if(AttachmentText.hasContent(note.unit.text))recordedRequests++;
        }
        // A committed note/group/final synthesis must have a corresponding dispatched
        // request (except whitespace-only ledger entries). An interrupted request may
        // be counted without a completed record. A successful run has no failed retry.
        if(requests<recordedRequests||(success&&requests!=recordedRequests))
            throw new IllegalArgumentException("Inconsistent request count");
        this.plan=plan;this.result=result;this.model=model;this.stopReason=stopReason;
        this.requests=requests;this.elapsedMillis=elapsedMillis;
        completed=Collections.unmodifiableList(notes);
        citations=DocumentBatchPolicy.citations(success?result.synthesis:"",completed);
    }

    DocumentBatchPolicy.Note lookup(String id){
        if(id==null||!id.matches("D[1-3]-S[1-9][0-9]?"))return null;
        for(DocumentBatchPolicy.Note n:completed)if(n.unit.id().equals(id))return n;
        return null;
    }
    boolean hasExtractedEvidence(DocumentBatchPolicy.Note note){
        return note!=null&&lookup(note.unit.id())==note&&AttachmentText.hasContent(note.unit.text);
    }
    String extracted(DocumentBatchPolicy.Note note){
        if(note==null||lookup(note.unit.id())!=note)throw new IllegalArgumentException("Unknown completed note");
        DocumentBatchPolicy.Unit u=note.unit;
        // The source and the generated note are distinct, even for whitespace-only records.
        return plan.sources.get(u.source).text.substring(u.start,u.end);
    }
    static String boundedJson(String json){
        if(json==null||json.length()>MAX_JSON_CHARS)throw new IllegalArgumentException("Report limit");
        return json;
    }
    String json()throws Exception{
        JSONArray sources=new JSONArray(),notes=new JSONArray();
        for(int i=0;i<plan.sources.size();i++){
            DocumentBatchPolicy.Source s=plan.sources.get(i);
            JSONObject source=new JSONObject().put("id","D"+(i+1)).put("name",s.name)
                .put("kind",s.kind.name()).put("extraction_partial",s.partial)
                .put("extracted_utf16",s.text.length()).put("planned_utf16",plan.includedEnd(i))
                .put("completed_utf16",result.completedEnd(i));
            if(s.kind==DocumentBatchPolicy.Kind.PDF)source.put("pdf_pages",s.pages)
                .put("pdf_examined_pages",s.examined).put("pdf_readable_pages",s.readable);
            sources.put(source);
        }
        for(DocumentBatchPolicy.Note note:completed){
            DocumentBatchPolicy.Unit u=note.unit;
            notes.put(new JSONObject().put("id",u.id()).put("source_id","D"+(u.source+1))
                .put("start_utf16_inclusive_1_based",u.start+1).put("end_utf16_inclusive_1_based",u.end)
                .put("record_kind",hasExtractedEvidence(note)?"GENERATED_NOTE":"WHITESPACE_ONLY")
                .put("note",hasExtractedEvidence(note)?(Object)note.text:JSONObject.NULL));
        }
        boolean success=result.status==DocumentBatchPolicy.Status.COMPLETED;
        return boundedJson(new JSONObject().put("schema",1).put("status",result.status.name())
            .put("stop_reason",stopReason==null?JSONObject.NULL:stopReason)
            .put("context_limited",result.contextLimited).put("model",model).put("goal",plan.goal)
            .put("scope","EXTRACTED_TEXT_ONLY").put("factual_verification",false)
            .put("planned_excerpts",plan.units.size()).put("completed_excerpts",completed.size())
            .put("completed_reduction_groups",result.groupsDone)
            .put("local_chat_request_attempts",requests).put("analysis_elapsed_ms",elapsedMillis)
            .put("elapsed_excludes_final_cleanup",true)
            .put("sources",sources).put("notes",notes)
            .put("synthesis",success?(Object)result.synthesis:JSONObject.NULL)
            .put("citation_check_scope","COMPLETED_NONWHITESPACE_ID_EXISTENCE_ONLY")
            .put("known_citation_ids",new JSONArray(citations.valid))
            .put("unknown_citation_ids",new JSONArray(citations.unknown)).toString(2));
    }
}
