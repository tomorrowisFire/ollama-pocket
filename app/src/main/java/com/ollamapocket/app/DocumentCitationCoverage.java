package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Counts distinct referenced excerpt IDs, not the truth or relevance of any claim. */
final class DocumentCitationCoverage {
    private static final Pattern ID=Pattern.compile("D([1-9]|1[0-9]|20)-S(?:[1-9]|1[0-9]|2[0-4])");
    enum Status { NOT_ASSESSED, NO_EVIDENCE, NO_REFERENCES, REFERENCED }
    static final class Source {
        final int completedEvidence,referencedEvidence;
        final Status status;
        private Source(int completed,int referenced,boolean assessed){
            completedEvidence=completed;referencedEvidence=assessed?referenced:-1;
            status=!assessed?Status.NOT_ASSESSED:completed==0?Status.NO_EVIDENCE:
                referenced==0?Status.NO_REFERENCES:Status.REFERENCED;
        }
    }
    private DocumentCitationCoverage(){}
    private static int source(String id,int sourceCount){
        if(id==null||!ID.matcher(id).matches())throw new IllegalArgumentException("Evidence ID");
        int index=Integer.parseInt(id.substring(1,id.indexOf('-')))-1;
        if(index>=sourceCount)throw new IllegalArgumentException("Evidence source");
        return index;
    }
    static List<Source> summarize(int sourceCount,List<String> evidence,List<String> references,boolean assessed){
        if(sourceCount<1||sourceCount>DocumentBatchPolicy.MAX_SOURCES||evidence==null||references==null||evidence.size()>DocumentBatchPolicy.MAX_UNITS||references.size()>DocumentBatchPolicy.MAX_UNITS||
            (!assessed&&!references.isEmpty()))throw new IllegalArgumentException("Evidence scope");
        int[] completed=new int[sourceCount],cited=new int[sourceCount];
        Set<String> known=new HashSet<>(),seen=new HashSet<>();
        for(String id:evidence){
            int index=source(id,sourceCount);
            if(!known.add(id))throw new IllegalArgumentException("Duplicate evidence");
            completed[index]++;
        }
        for(String id:references){
            int index=source(id,sourceCount);
            if(!known.contains(id)||!seen.add(id))throw new IllegalArgumentException("Unreviewed or duplicate reference");
            cited[index]++;
        }
        List<Source> result=new ArrayList<>(sourceCount);
        for(int i=0;i<sourceCount;i++)result.add(new Source(completed[i],cited[i],assessed));
        return Collections.unmodifiableList(result);
    }
}
