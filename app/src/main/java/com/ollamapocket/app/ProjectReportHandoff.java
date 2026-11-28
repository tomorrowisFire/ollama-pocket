package com.ollamapocket.app;

/** Exact, immutable transfer from a typed terminal snapshot, not verification. */
final class ProjectReportHandoff {
    final String json,status;
    private ProjectReportHandoff(String json,String status){
        checkSize(json);this.json=json;this.status=status;
    }
    static final class TooLarge extends IllegalArgumentException {}
    static void checkSize(String text){
        if(text==null||text.isEmpty())throw new IllegalArgumentException("Empty report");
        if(text.length()>ProjectLibrary.MAX_TEXT)throw new TooLarge();
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IllegalArgumentException("Invalid Unicode");
            }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode");
        }
    }
    static ProjectReportHandoff of(ConferenceReport report)throws Exception{
        return new ProjectReportHandoff(report.json(),report.result.status.name());
    }
    static ProjectReportHandoff of(DocumentBatchReport report)throws Exception{
        return new ProjectReportHandoff(report.json(),report.result.status.name());
    }
    /** Explicit alternative to the exact full handoff, never an automatic truncation.
     * Preserve the original source/coverage/citation ledger and synthesis. Omit whole
     * generated notes as needed, with an exact report identity and visible omissions. */
    static ProjectReportHandoff compactOf(DocumentBatchReport report)throws Exception{
        String full=report.json();
        org.json.JSONObject root=new org.json.JSONObject(full);
        org.json.JSONArray rows=root.getJSONArray("notes");
        java.util.List<String> generated=new java.util.ArrayList<>();
        int total=0;
        for(int i=0;i<rows.length();i++){
            org.json.JSONObject row=rows.getJSONObject(i);
            String value=row.isNull("note")?null:row.getString("note");
            generated.add(value);
            if(value!=null)total++;
            row.put("note",org.json.JSONObject.NULL);
            row.put("generated_note_included",false);
        }
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256")
            .digest(full.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder(64);
        for(byte b:hash){int n=b&255;hex.append(Character.forDigit(n>>>4,16)).append(Character.forDigit(n&15,16));}
        root.put("kind","ONHAND_COMPACT_DOCUMENT_REPORT")
            .put("complete_report",false).put("full_report_sha256_utf8",hex.toString())
            .put("raw_source_excerpts_included",false).put("generated_notes_total",total)
            .put("generated_notes_included",0).put("generated_notes_omitted",total);
        // The metadata alone must fit. Preserve every row and never crop a string or the JSON.
        // Reserve room for final count changes, even at the 80-unit limit.
        int length=root.toString().length(),included=0;
        if(length>ProjectLibrary.MAX_TEXT)throw new TooLarge();
        for(int i=0;i<rows.length();i++){
            String value=generated.get(i);
            if(value==null)continue;
            org.json.JSONObject row=rows.getJSONObject(i);
            int before=row.toString().length();
            row.put("note",value).put("generated_note_included",true);
            int delta=row.toString().length()-before;
            if((long)length+delta+16<=ProjectLibrary.MAX_TEXT){
                included++;length+=delta;
            }else row.put("note",org.json.JSONObject.NULL).put("generated_note_included",false);
        }
        root.put("generated_notes_included",included).put("generated_notes_omitted",total-included);
        return new ProjectReportHandoff(root.toString(),report.result.status.name());
    }
    ProjectLibrary.State createProject(ProjectLibrary.State st,String pid,String projectTitle,String aid,String artifactTitle,long now){
        return ProjectLibrary.createProjectWithArtifact(st,pid,projectTitle,aid,artifactTitle,json,Math.max(1,now));
    }
    ProjectLibrary.State create(ProjectLibrary.State st,String pid,String id,String title,long now){
        return ProjectLibrary.createArtifact(st,pid,id,title,json,Math.max(1,now));
    }
    ProjectLibrary.State append(ProjectLibrary.State st,String pid,String aid,long now){
        ProjectLibrary.Artifact a=st.project(pid).artifact(aid);
        return ProjectLibrary.editArtifact(st,pid,aid,json,Math.max(a.current().saved,now));
    }
}
