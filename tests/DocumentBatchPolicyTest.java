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
        ok(DocumentBatchPolicy.prefixLimit(1)==16788);
        ok(DocumentBatchPolicy.prefixLimit(2)==8394);
        ok(DocumentBatchPolicy.prefixLimit(3)==5596);
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
        rejects(()->plan("x".repeat(16789)));
        rejects(()->plan("x".repeat(8395),"y"));
        rejects(()->plan("x","y","z","w"));
        rejects(()->plan(" "));
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
        AttachmentText.Prefix prefix=AttachmentText.readPrefix("a.txt",new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),AttachmentText.Encoding.UNICODE,1400);
        ok(prefix.partial&&prefix.text.length()==1399&&prefix.text.equals(source.substring(0,1399)));
        AttachmentText.Prefix whole=AttachmentText.readPrefix("a.txt",new ByteArrayInputStream("한글 문서".getBytes(StandardCharsets.UTF_8)),AttachmentText.Encoding.UNICODE,100);
        ok(!whole.partial&&whole.text.equals("한글 문서"));
        AttachmentText.Prefix prior=new AttachmentText.Prefix("abc",true).bounded(10);
        ok(prior.partial&&prior.text.equals("abc"));
        System.out.println("PASS: "+n+" bounded document batch, coverage and failure-policy assertions");
    }
}
