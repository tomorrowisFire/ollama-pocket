package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import static com.ollamapocket.app.DocumentCitationCoverage.Status.*;

public final class DocumentCitationCoverageTest {
    private static int checks;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    private static void rejects(Runnable action){
        try{action.run();throw new AssertionError("Invalid evidence accepted");}
        catch(IllegalArgumentException expected){checks++;}
    }
    public static void main(String[] args){
        for(int sources=1;sources<=3;sources++){
            int slots=sources*3;
            int combinations=1;for(int i=0;i<slots;i++)combinations*=3;
            // Each slot is absent, completed but uncited, or completed and cited.
            for(int code=0;code<combinations;code++){
                int current=code;
                List<String> evidence=new ArrayList<>(),refs=new ArrayList<>();
                int[] count=new int[sources],cited=new int[sources];
                for(int i=0;i<slots;i++){
                    int state=current%3;current/=3;
                    int source=i/3;String id="D"+(source+1)+"-S"+(i%3+1);
                    if(state>0){evidence.add(id);count[source]++;}
                    if(state==2){refs.add(id);cited[source]++;}
                }
                List<DocumentCitationCoverage.Source> result=DocumentCitationCoverage.summarize(sources,evidence,refs,true);
                List<DocumentCitationCoverage.Source> pending=DocumentCitationCoverage.summarize(sources,evidence,List.of(),false);
                ok(result.size()==sources&&pending.size()==sources);
                for(int i=0;i<sources;i++){
                    DocumentCitationCoverage.Source r=result.get(i),p=pending.get(i);
                    ok(r.completedEvidence==count[i]&&r.referencedEvidence==cited[i]);
                    ok(r.status==(count[i]==0?NO_EVIDENCE:cited[i]==0?NO_REFERENCES:REFERENCED));
                    ok(p.completedEvidence==count[i]&&p.referencedEvidence==-1&&p.status==NOT_ASSESSED);
                }
            }
        }
        List<String> full=new ArrayList<>();for(int i=1;i<=24;i++)full.add("D1-S"+i);
        ok(DocumentCitationCoverage.summarize(1,full,full,true).get(0).referencedEvidence==24);
        for(String invalid:new String[]{"D0-S1","D4-S1","D1-S0","D1-S25","D1-S01","d1-s1","D1-S1 ","Ｄ1-S1"})
            rejects(()->DocumentCitationCoverage.summarize(3,List.of(invalid),List.of(),true));
        rejects(()->DocumentCitationCoverage.summarize(1,List.of("D2-S1"),List.of(),true));
        rejects(()->DocumentCitationCoverage.summarize(1,List.of("D1-S1","D1-S1"),List.of(),true));
        rejects(()->DocumentCitationCoverage.summarize(1,List.of("D1-S1"),List.of("D1-S1","D1-S1"),true));
        rejects(()->DocumentCitationCoverage.summarize(1,List.of("D1-S1"),List.of("D1-S2"),true));
        rejects(()->DocumentCitationCoverage.summarize(1,List.of("D1-S1"),List.of("D1-S1"),false));
        rejects(()->DocumentCitationCoverage.summarize(0,List.of(),List.of(),true));
        rejects(()->DocumentCitationCoverage.summarize(4,List.of(),List.of(),true));
        rejects(()->DocumentCitationCoverage.summarize(1,null,List.of(),true));
        rejects(()->DocumentCitationCoverage.summarize(1,List.of(),null,true));
        try{DocumentCitationCoverage.summarize(1,List.of(),List.of(),true).clear();throw new AssertionError("Mutable report");}
        catch(UnsupportedOperationException expected){checks++;}
        System.out.println("PASS: "+checks+" document citation coverage assertions");
    }
}
