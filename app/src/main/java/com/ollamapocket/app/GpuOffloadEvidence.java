package com.ollamapocket.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative evidence of completed tensor offload, not a performance benchmark. */
final class GpuOffloadEvidence {
    static final int MAX_LOG_LINE=512, MAX_LAYERS=65535;
    private static final Pattern COMPLETE=Pattern.compile(
        "^[ \\t]*(?:load_tensors|llm_load_tensors):[ \\t]+offloaded[ \\t]+([1-9][0-9]{0,4})/([1-9][0-9]{0,4})[ \\t]+layers?[ \\t]+to[ \\t]+GPU[ \\t]*$",
        Pattern.CASE_INSENSITIVE);
    private GpuOffloadEvidence(){}
    static final int MAX_DIAGNOSTIC_LINE=2048;
    private static final String TRUNCATED=" [truncated]";
    private static String truncated(String value){
        int end=Math.min(value.length(),MAX_DIAGNOSTIC_LINE-TRUNCATED.length());
        if(end>0&&end<value.length()&&Character.isHighSurrogate(value.charAt(end-1))&&Character.isLowSurrogate(value.charAt(end)))end--;
        return value.substring(0,end)+TRUNCATED;
    }
    static String boundedDiagnostic(String value){
        return value==null?"":value.length()>MAX_DIAGNOSTIC_LINE?truncated(value):value;
    }
    /** Read a complete physical line but retain at most 2048 characters, including the truncation marker. */
    static String readDiagnosticLine(java.io.BufferedReader reader)throws java.io.IOException{
        StringBuilder text=new StringBuilder(128);
        boolean overflow=false;
        int ch;
        while((ch=reader.read())!=-1){
            if(ch=='\n')break;
            if(ch=='\r'){reader.mark(1);if(reader.read()!='\n')reader.reset();break;}
            if(text.length()<MAX_DIAGNOSTIC_LINE)text.append((char)ch);else overflow=true;
        }
        if(ch==-1&&text.length()==0&&!overflow)return null;
        return overflow?truncated(text.toString()):text.toString();
    }

    static boolean completed(String line){
        if(line==null||line.length()>MAX_LOG_LINE)return false;
        Matcher match=COMPLETE.matcher(line);
        if(!match.matches())return false;
        int offloaded=Integer.parseInt(match.group(1));
        int total=Integer.parseInt(match.group(2));
        return offloaded<=total&&total<=MAX_LAYERS;
    }

    static boolean ready(boolean sameProcessGeneration,boolean alive,boolean completed,
                         boolean healthy,boolean cancelled,boolean interrupted,boolean safe){
        return sameProcessGeneration&&alive&&completed&&healthy&&!cancelled&&!interrupted&&safe;
    }
}
