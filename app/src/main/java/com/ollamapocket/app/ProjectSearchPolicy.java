package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only first literal match per field, preserving exact UTF-16 offsets. */
final class ProjectSearchPolicy {
    static final int MAX_FIELDS=504, MAX_HITS=40, MAX_FIELD=32000, MAX_TOTAL=2*1024*1024, CONTEXT=60;
    enum Kind { PROJECT_TITLE, INSTRUCTIONS, ARTIFACT_TITLE, CURRENT_TEXT }
    private ProjectSearchPolicy(){}
    static final class Source {
        final String projectId,artifactId,projectTitle,artifactTitle,text;
        final Kind kind;final int ordinal;
        Source(String projectId,String artifactId,String projectTitle,String artifactTitle,Kind kind,int ordinal,String text){
            if(projectId==null||projectTitle==null||kind==null||text==null||text.length()>MAX_FIELD||
                ((kind==Kind.ARTIFACT_TITLE||kind==Kind.CURRENT_TEXT)!=(artifactId!=null))||
                ((artifactId!=null)!=(artifactTitle!=null))||
                (kind==Kind.CURRENT_TEXT?ordinal<1||ordinal>6:ordinal!=0))throw new IllegalArgumentException("Invalid field");
            for(int i=0;i<text.length();){int cp=text.codePointAt(i);if(cp>=0xD800&&cp<=0xDFFF)throw new IllegalArgumentException("Invalid Unicode");i+=Character.charCount(cp);}
            this.projectId=projectId;this.artifactId=artifactId;this.projectTitle=projectTitle;this.artifactTitle=artifactTitle;
            this.kind=kind;this.ordinal=ordinal;this.text=text;
        }
    }
    static final class Hit {
        final Source source;final int start,end,contextStart,contextEnd;
        private Hit(Source source,int start,int end){
            this.source=source;this.start=start;this.end=end;
            String text=source.text;
            int a=Math.max(0,start-CONTEXT),b=Math.min(text.length(),end+CONTEXT);
            if(a>0&&Character.isLowSurrogate(text.charAt(a)))a--;
            if(b<text.length()&&Character.isLowSurrogate(text.charAt(b)))b++;
            contextStart=a;contextEnd=b;
        }
        String context(){return source.text.substring(contextStart,contextEnd);}
        boolean viewerCompatible(String query){
            try{WorkspaceTextPolicy.review(source.text).atMatch(query,start,end);return true;}
            catch(IllegalArgumentException e){return false;}
        }
    }
    static final class Result {
        final List<Hit> hits;final boolean truncated;
        private Result(List<Hit> hits,boolean truncated){this.hits=Collections.unmodifiableList(new ArrayList<>(hits));this.truncated=truncated;}
    }
    static Result search(List<Source> sources,String query){
        WorkspaceTextPolicy.validateQuery(query);
        if(sources==null||sources.size()>MAX_FIELDS)throw new IllegalArgumentException("Field limit");
        long size=0;
        for(Source source:sources){if(source==null||(size+=source.text.length())>MAX_TOTAL)throw new IllegalArgumentException("Source limit");}
        List<Hit> hits=new ArrayList<>();
        for(Source source:sources){
            int at=source.text.indexOf(query);
            if(at<0)continue;
            if(hits.size()==MAX_HITS)return new Result(hits,true);
            hits.add(new Hit(source,at,at+query.length()));
        }
        return new Result(hits,false);
    }
}
