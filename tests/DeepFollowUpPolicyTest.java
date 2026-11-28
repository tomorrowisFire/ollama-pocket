package com.ollamapocket.app;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeepFollowUpPolicyTest {
    static int checks;
    static void ok(boolean pass){if(!pass)throw new AssertionError("check "+checks);checks++;}
    static void reject(Runnable r){try{r.run();throw new AssertionError("Unsafe admission");}catch(IllegalArgumentException expected){checks++;}}
    static DocumentBatchReport report(){
        List<DocumentBatchPolicy.Source> sources=new ArrayList<>();
        for(int i=1;i<=20;i++)sources.add(new DocumentBatchPolicy.Source("doc"+i+".txt",
            i==20?"2026년 재활용 실적 42톤. 한국어 中文 😀. 비고: 확인 필요.":"Source "+i+" unrelated fact.",
            DocumentBatchPolicy.Kind.TEXT,true,0,0,0));
        var plan=new DocumentBatchPolicy.Plan("compare",sources);
        AtomicInteger calls=new AtomicInteger();
        var result=DocumentBatchPolicy.analyze(plan,(prompt,chars,tokens)->{
            calls.incrementAndGet();return "UNVERIFIED_NOTE_PRIVATE_MARKER";
        },()->false,n->{});
        ok(result.status==DocumentBatchPolicy.Status.COMPLETED);
        return new DocumentBatchReport(plan,result,"fixture",null,calls.get(),1);
    }
    public static void main(String[] args){
        var report=report();var session=new DeepFollowUpPolicy.Session(report);
        var first=session.prepare("2026년 재활용 실적은?",-1);
        ok(first.evidence.get(0).id.equals("D20-S1"));
        ok(first.prompt.contains("42톤")&&!first.prompt.contains("UNVERIFIED_NOTE_PRIVATE_MARKER"));
        ok(first.evidence.size()<=4&&DocumentBatchPolicy.fits(first.prompt,DeepFollowUpPolicy.TOKENS));
        for(var e:first.evidence){
            var u=e.note.unit;
            ok(e.text.equals(report.plan.sources.get(u.source).text.substring(e.start,e.end)));
            ok(e.start>=u.start&&e.end<=u.end);
        }
        var stale=session.prepare("Other question",0);
        var t=session.commit(first,"42톤 [D20-S1], unavailable [D99-S1]");
        ok(t.citations.valid.equals(List.of("D20-S1"))&&t.citations.unknown.equals(List.of("D99-S1")));
        reject(()->session.commit(first,"duplicate"));reject(()->session.commit(stale,"stale"));
        var second=session.prepare("그 수치는 무엇인가?",19);
        ok(second.evidence.size()==1&&second.evidence.get(0).id.equals("D20-S1"));
        ok(second.prompt.contains("Previous unverified dialogue")&&second.prompt.contains("context only"));
        var t2=session.commit(second,"answer [D20-S1] [D1-S1]");
        ok(t2.citations.valid.equals(List.of("D20-S1"))&&t2.citations.unknown.equals(List.of("D1-S1")));
        var other=new DeepFollowUpPolicy.Session(report);
        reject(()->other.commit(second,"cross session"));
        reject(()->session.prepare("q",20));reject(()->session.prepare("q",-2));
        reject(()->session.prepare(" ",-1));reject(()->session.prepare("bad\uD800",-1));
        reject(()->session.prepare("x".repeat(301),-1));
        var valid=session.prepare("question",19);
        reject(()->session.commit(valid,"bad\u0000output"));
        ok(session.current(valid)&&session.count()==2);
        for(int i=0;i<30;i++)session.commit(session.prepare("next "+i,19),"reply [D20-S1]");
        ok(session.count()==32&&session.turns().size()==24&&session.turns().get(0).number==9);
        ok(session.transcript().contains("retained 24 of 32")&&session.transcript().contains("Supplied source ranges"));
        ok(!session.transcript().contains("UNVERIFIED_NOTE_PRIVATE_MARKER"));
        var partial=new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.STOPPED,report.completed.subList(0,1),0,"");
        var partialReport=new DocumentBatchReport(report.plan,partial,"fixture","USER",1,1);
        var limited=new DeepFollowUpPolicy.Session(partialReport);
        reject(()->limited.prepare("query",19));ok(limited.prepare("query",-1).evidence.get(0).id.equals("D1-S1"));
        var empty=new DocumentBatchPolicy.Result(DocumentBatchPolicy.Status.STOPPED,List.of(),0,"");
        reject(()->new DeepFollowUpPolicy.Session(new DocumentBatchReport(report.plan,empty,"fixture","USER",0,1)));
        System.out.println("PASS: "+checks+" source-grounded follow-up policy assertions");
    }
}
