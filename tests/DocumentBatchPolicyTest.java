package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class DocumentBatchPolicyTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed);}
    private static DocumentBatchPolicy.Source text(String name,String data,boolean partial){
        return new DocumentBatchPolicy.Source(name,data,DocumentBatchPolicy.Kind.TEXT,partial,0,0,0);
    }
    private static DocumentBatchPolicy.Plan plan(String... values){
        List<DocumentBatchPolicy.Source> files=new ArrayList<>();
        for(int i=0;i<values.length;i++)files.add(text("doc"+i+".txt",values[i],true));
        return new DocumentBatchPolicy.Plan("Compare the documents.",files);
    }
    public static void main(String[] args)throws Exception{
        ok(DocumentBatchPolicy.prefixLimit(1)==33576);
        ok(DocumentBatchPolicy.prefixLimit(2)==16788);
        ok(DocumentBatchPolicy.prefixLimit(3)==11192);
        rejects(()->DocumentBatchPolicy.prefixLimit(0));
        rejects(()->DocumentBatchPolicy.prefixLimit(4));
        String source="a".repeat(1399)+"😀"+"b".repeat(1397)+"한국어";
        DocumentBatchPolicy.Plan split=plan(source);
        StringBuilder reconstructed=new StringBuilder();int next=0;
        for(DocumentBatchPolicy.Unit u:split.units){
            ok(u.start==next&&u.end-u.start==u.text.length()&&u.text.length()<=1400);
            ok(!Character.isLowSurrogate(u.text.charAt(0))&&!Character.isHighSurrogate(u.text.charAt(u.text.length()-1)));
            reconstructed.append(u.text);next=u.end;
        }
        ok(reconstructed.toString().equals(source)&&next==source.length());
        DocumentBatchPolicy.Plan max=plan("A".repeat(5596),"B".repeat(5596),"C".repeat(5596));
        ok(max.units.size()==12&&max.total()==16);
        ok(max.units.get(4).id().equals("D2-S1")&&max.units.get(8).id().equals("D3-S1"));
        ok(max.units.get(11).end==5596);
        ok(DocumentBatchPolicy.unitPrompt(max,max.units.get(0)).contains("PARTIAL"));
        ok(DocumentBatchPolicy.unitPrompt(max,max.units.get(0)).contains("UNTRUSTED DATA"));
        rejects(()->plan("x".repeat(33577)));
        rejects(()->plan("x".repeat(16789),"y"));
        rejects(()->plan("x","y","z","w"));
        rejects(()->plan(" "));
        rejects(()->plan("\u00a0\u3000\u202f"));
        rejects(()->new DocumentBatchPolicy.Plan("\u2003\u00a0",List.of(text("a","x",false))));
        rejects(()->DocumentBatchPolicy.checked("\u3000\u00a0",420));
        rejects(()->new DocumentBatchPolicy.Plan("",List.of(text("a","x",false))));
        rejects(()->new DocumentBatchPolicy.Plan("a".repeat(301),List.of(text("a","x",false))));
        DocumentBatchPolicy.Source pdf=new DocumentBatchPolicy.Source("pdf","page text",DocumentBatchPolicy.Kind.PDF,true,40,12,10);
        ok(pdf.scope().contains("12 of 40")&&pdf.scope().contains("10 readable"));
        rejects(()->new DocumentBatchPolicy.Source("a","x",DocumentBatchPolicy.Kind.PDF,true,2,3,1));
        AtomicInteger calls=new AtomicInteger(),progress=new AtomicInteger();
        DocumentBatchPolicy.Result complete=DocumentBatchPolicy.analyze(max,(prompt,chars,tokens)->{
            int call=calls.incrementAndGet();
            if(call<=12){ok(prompt.contains("BEGIN SOURCE"));ok(chars==420&&tokens==256);}
            else if(call<=15)ok(prompt.contains("generated notes"));
            else{ok(prompt.contains("Group 3:"));ok(chars==1800&&tokens==512);}
            return "note "+call;
        },()->false,progress::set);
        ok(complete.status==DocumentBatchPolicy.Status.COMPLETED&&complete.notes.size()==12&&complete.groupsDone==3);
        ok(calls.get()==16&&progress.get()==16&&complete.synthesis.equals("note 16"));
        calls.set(0);progress.set(0);
        DocumentBatchPolicy.Plan single=plan("small extracted document");
        DocumentBatchPolicy.Result direct=DocumentBatchPolicy.analyze(single,(prompt,chars,tokens)->{
            int call=calls.incrementAndGet();
            if(call==1){ok(prompt.contains("BEGIN SOURCE"));return "fact";}
            ok(call==2&&prompt.contains("final analysis")&&prompt.contains("[D1-S1]: fact"));
            return "Summary [D1-S1]";
        },()->false,progress::set);
        ok(direct.status==DocumentBatchPolicy.Status.COMPLETED&&direct.notes.size()==1&&direct.groupsDone==0);
        ok(calls.get()==2&&progress.get()==2&&direct.synthesis.equals("Summary [D1-S1]"));
        ok(DocumentBatchPolicy.citations(direct.synthesis,direct.notes).valid.equals(List.of("D1-S1")));
        // A long multilingual goal and note may require the original singleton reduction.
        DocumentBatchPolicy.Plan singleCjk=new DocumentBatchPolicy.Plan("한".repeat(300),List.of(text("short","x",false)));
        String longNote="한".repeat(420);
        ok(!DocumentBatchPolicy.fits(DocumentBatchPolicy.finalPrompt(singleCjk,List.of("[D1-S1]: "+longNote)),DocumentBatchPolicy.FINAL_TOKENS));
        ok(DocumentBatchPolicy.fits(DocumentBatchPolicy.groupPrompt(singleCjk,List.of(new DocumentBatchPolicy.Note(singleCjk.units.get(0),longNote)),0,1),DocumentBatchPolicy.NOTE_TOKENS));
        calls.set(0);
        DocumentBatchPolicy.Result reducedSingle=DocumentBatchPolicy.analyze(singleCjk,(prompt,chars,tokens)->{
            int call=calls.incrementAndGet();return call==1?longNote:call==2?"brief [D1-S1]":"final [D1-S1]";
        },()->false,x->{});
        ok(reducedSingle.status==DocumentBatchPolicy.Status.COMPLETED&&reducedSingle.groupsDone==1&&calls.get()==3);
        calls.set(0);progress.set(0);
        DocumentBatchPolicy.Result failed=DocumentBatchPolicy.analyze(max,(prompt,chars,tokens)->{
            if(calls.incrementAndGet()==4)throw new java.io.IOException("fixture");
            return "note";
        },()->false,progress::set);
        ok(failed.status==DocumentBatchPolicy.Status.FAILED&&failed.notes.size()==3&&failed.groupsDone==0&&failed.synthesis.isEmpty());
        ok(calls.get()==4&&progress.get()==3);
        calls.set(0);
        DocumentBatchPolicy.Result oversized=DocumentBatchPolicy.analyze(max,(prompt,chars,tokens)->{
            calls.incrementAndGet();return "z".repeat(chars+1);
        },()->false,x->{});
        ok(oversized.status==DocumentBatchPolicy.Status.FAILED&&oversized.notes.isEmpty()&&calls.get()==1);
        AtomicBoolean stop=new AtomicBoolean();calls.set(0);
        DocumentBatchPolicy.Result stopped=DocumentBatchPolicy.analyze(max,(prompt,chars,tokens)->{
            if(calls.incrementAndGet()==2)stop.set(true);return "note";
        },stop::get,x->{});
        ok(stopped.status==DocumentBatchPolicy.Status.STOPPED&&stopped.notes.size()==1&&stopped.synthesis.isEmpty());
        calls.set(0);
        DocumentBatchPolicy.Result groupFailure=DocumentBatchPolicy.analyze(max,(prompt,chars,tokens)->{
            if(calls.incrementAndGet()==14)return "";return "note";
        },()->false,x->{});
        ok(groupFailure.status==DocumentBatchPolicy.Status.FAILED&&groupFailure.notes.size()==12&&groupFailure.groupsDone==1&&groupFailure.synthesis.isEmpty());
        calls.set(0);
        DocumentBatchPolicy.Result finalFailure=DocumentBatchPolicy.analyze(max,(prompt,chars,tokens)->{
            if(calls.incrementAndGet()==16)throw new java.io.IOException("fixture");return "note";
        },()->false,x->{});
        ok(finalFailure.status==DocumentBatchPolicy.Status.FAILED&&finalFailure.notes.size()==12&&finalFailure.groupsDone==3&&finalFailure.synthesis.isEmpty());
        DocumentBatchPolicy.Plan expanded=plan("A".repeat(11192),"B".repeat(11192),"C".repeat(11192));
        ok(expanded.units.size()==24&&expanded.groupCount()==8&&expanded.total()==33);
        ok(expanded.includedEnd(0)==11192&&expanded.includedEnd(2)==11192);
        DocumentBatchPolicy.Plan multilingual=plan("한글日本語中文😀".repeat(3000));
        int covered=0;
        for(DocumentBatchPolicy.Unit unit:multilingual.units){
            ok(unit.start==covered&&DocumentBatchPolicy.fits(DocumentBatchPolicy.unitPrompt(multilingual,unit),256));
            ok(!Character.isLowSurrogate(unit.text.charAt(0))&&!Character.isHighSurrogate(unit.text.charAt(unit.text.length()-1)));
            covered=unit.end;
        }
        ok(covered==multilingual.includedEnd(0)&&covered<multilingual.sources.get(0).text.length());
        ok(DocumentBatchPolicy.estimateTokens("abc")==1&&DocumentBatchPolicy.estimateTokens("한")==2);
        ok(!DocumentBatchPolicy.fits("한".repeat(1000),512));
        calls.set(0);progress.set(0);
        DocumentBatchPolicy.Result extended=DocumentBatchPolicy.analyze(expanded,(prompt,chars,tokens)->{
            int call=calls.incrementAndGet();
            if(call<=24)ok(prompt.contains("BEGIN SOURCE"));
            else if(call<=30)ok(prompt.contains("generated notes"));
            else if(call<=32)ok(prompt.contains("unverified summaries"));
            else ok(prompt.contains("Group 2:")&&!prompt.contains("Group 3:"));
            return call==33?"Summary [D1-S1] [D3-S8] [D9-S1] [D1-S1]":"note";
        },()->false,progress::set);
        ok(extended.status==DocumentBatchPolicy.Status.COMPLETED&&extended.groupsDone==8&&calls.get()==33&&progress.get()==33);
        DocumentBatchPolicy.Citations cited=DocumentBatchPolicy.citations(extended.synthesis,extended.notes);
        ok(cited.valid.equals(List.of("D1-S1","D3-S8"))&&cited.unknown.equals(List.of("D9-S1")));
        ok(DocumentBatchPolicy.citations("no brackets D1-S1",extended.notes).valid.isEmpty());
        ok(DocumentBatchPolicy.citations("[D1-S1]",List.of()).unknown.equals(List.of("D1-S1")));
        DocumentBatchPolicy.Citations mixed=DocumentBatchPolicy.citations(
            "[D1-S1, D2-S8; D90000-S1] ［D3-S8、d1-s1］ [D01-S1, D1 – S2]",extended.notes);
        ok(mixed.valid.equals(List.of("D1-S1","D2-S8","D3-S8")));
        ok(mixed.unknown.equals(List.of("D90000-S1","d1-s1","D01-S1","D1 – S2")));
        DocumentBatchPolicy.Note whitespace=new DocumentBatchPolicy.Note(
            new DocumentBatchPolicy.Unit(0,0,0,2,"  "),"[Whitespace-only extracted range]");
        ok(DocumentBatchPolicy.citations("[D1-S1]",List.of(whitespace)).unknown.equals(List.of("D1-S1")));
        ok(extended.completedEnd(0)==11192&&extended.completedEnd(2)==11192);
        ok(failed.completedEnd(0)==4200&&failed.completedEnd(1)==0&&failed.completedEnd(2)==0);
        ok(stopped.completedEnd(0)==1400&&stopped.completedEnd(1)==0);
        String scopePrompt=DocumentBatchPolicy.finalPrompt(multilingual,List.of("note"));
        ok(scopePrompt.contains("Scope (extracted text only")&&scopePrompt.contains(
            "D1 "+multilingual.includedEnd(0)+"/"+multilingual.sources.get(0).text.length()+" partial"));
        // Long CJK notes require smaller reduction groups; retain all original notes.
        DocumentBatchPolicy.Plan adaptive=plan("a".repeat(5596));
        ok(adaptive.units.size()==4&&adaptive.maxTotal()==8&&expanded.maxTotal()==48);
        calls.set(0);progress.set(0);
        DocumentBatchPolicy.Result reduced=DocumentBatchPolicy.analyze(adaptive,(prompt,chars,tokens)->{
            int call=calls.incrementAndGet();ok(DocumentBatchPolicy.fits(prompt,tokens));
            if(call<=4)return "한".repeat(350);
            if(call<=6){ok(prompt.contains("generated notes"));return "요약 [D1-S"+(call==5?1:3)+"]";}
            return "final [D1-S1] [D1-S3]";
        },()->false,progress::set);
        ok(reduced.status==DocumentBatchPolicy.Status.COMPLETED&&reduced.notes.size()==4&&reduced.groupsDone==2);
        ok(calls.get()==7&&progress.get()==7&&!reduced.contextLimited);
        calls.set(0);
        DocumentBatchPolicy.Result noFit=DocumentBatchPolicy.analyze(adaptive,(prompt,chars,tokens)->{
            calls.incrementAndGet();return "漢".repeat(420);
        },()->false,x->{});
        ok(noFit.status==DocumentBatchPolicy.Status.FAILED&&noFit.contextLimited&&noFit.notes.size()==4&&noFit.synthesis.isEmpty());
        ok(calls.get()==4);
        AttachmentText.Prefix prefix=AttachmentText.readPrefix("a.txt",new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),AttachmentText.Encoding.UNICODE,1400);
        ok(prefix.partial&&prefix.text.length()==1399&&prefix.text.equals(source.substring(0,1399)));
        AttachmentText.Prefix whole=AttachmentText.readPrefix("a.txt",new ByteArrayInputStream("한글 문서".getBytes(StandardCharsets.UTF_8)),AttachmentText.Encoding.UNICODE,100);
        ok(!whole.partial&&whole.text.equals("한글 문서"));
        AttachmentText.Prefix prior=new AttachmentText.Prefix("abc",true).bounded(10);
        ok(prior.partial&&prior.text.equals("abc"));
        System.out.println("PASS: "+n+" bounded document batch, coverage and failure-policy assertions");
    }
}
