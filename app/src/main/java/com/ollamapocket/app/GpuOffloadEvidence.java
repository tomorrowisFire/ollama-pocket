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
