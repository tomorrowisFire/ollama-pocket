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
    ProjectLibrary.State create(ProjectLibrary.State st,String pid,String id,String title,long now){
        return ProjectLibrary.createArtifact(st,pid,id,title,json,Math.max(1,now));
    }
    ProjectLibrary.State append(ProjectLibrary.State st,String pid,String aid,long now){
        ProjectLibrary.Artifact a=st.project(pid).artifact(aid);
        return ProjectLibrary.editArtifact(st,pid,aid,json,Math.max(a.current().saved,now));
    }
}
