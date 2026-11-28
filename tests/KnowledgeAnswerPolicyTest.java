package com.ollamapocket.app;
import java.util.List;
public final class KnowledgeAnswerPolicyTest {
    private static int n;
    private static void ok(boolean b){if(!b)throw new AssertionError("knowledge answer "+n);n++;}
    private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException e){n++;return;}throw new AssertionError("invalid accepted "+n);}
    public static void main(String[] args)throws Exception{
        String query="한글😀";
        String text="a".repeat(119)+"😀"+query+"😀"+"z".repeat(119);
        KnowledgeLibrary.State one=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"one","One",text);
        KnowledgeAnswerPolicy.Plan plan=KnowledgeAnswerPolicy.plan(one,query,"질문?");
        ok(plan.matchingSources==1&&plan.includedSources==1&&plan.excerpts.size()==1);
        KnowledgeAnswerPolicy.Excerpt e=plan.excerpts.get(0);
        ok(e.marker.equals("K1")&&e.sourceId.equals("one")&&e.text.contains(query));
        ok(e.text.equals(text.substring(e.start-1,e.end)));
        ok(!Character.isLowSurrogate(e.text.charAt(0))&&!Character.isHighSurrogate(e.text.charAt(e.text.length()-1)));
        ok(plan.prompt.contains(e.text)&&plan.prompt.contains("untrusted data"));
        ok(DocumentBatchPolicy.fits(plan.prompt,KnowledgeAnswerPolicy.TOKENS));
        ok(plan.stillAvailable(one)&&plan.stillAvailable(KnowledgeLibrary.decode(KnowledgeLibrary.encode(one))));
        ok(!plan.stillAvailable(null)&&!plan.stillAvailable(KnowledgeLibrary.remove(one,"one")));
        KnowledgeLibrary.State different=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"one","Changed",text);
        ok(!plan.stillAvailable(different));
        KnowledgeAnswerPolicy.Plan absent=KnowledgeAnswerPolicy.plan(one,"NO","Question");
        ok(absent.excerpts.isEmpty()&&absent.matchingSources==0);
        reject(()->KnowledgeAnswerPolicy.answer(absent,"fake [K1]"));
        reject(()->KnowledgeAnswerPolicy.plan(one,"","q"));
        reject(()->KnowledgeAnswerPolicy.plan(one," ","q"));
        reject(()->KnowledgeAnswerPolicy.plan(one,"x".repeat(121),"q"));
        reject(()->KnowledgeAnswerPolicy.plan(one,query,"x".repeat(301)));
        reject(()->KnowledgeAnswerPolicy.plan(one,query,String.valueOf((char)0xd800)));
        reject(()->KnowledgeAnswerPolicy.answer(plan,""));
        reject(()->KnowledgeAnswerPolicy.answer(plan,"x".repeat(1801)));
        KnowledgeAnswerPolicy.Answer answer=KnowledgeAnswerPolicy.answer(plan,"claim [K1] [K9] [K01] [K1] [K2] [k1]");
        ok(answer.known.equals(List.of("K1"))&&answer.unknown.equals(List.of("K9","K01","K2","k1")));
        ok(answer.text.contains("[K01]"));
        ok(KnowledgeAnswerPolicy.answer(plan,"no citation").known.isEmpty());
        ok(KnowledgeAnswerPolicy.answer(plan,"no citation").unknown.isEmpty());
        KnowledgeLibrary.State many=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"a","A","key ".repeat(60));
        for(int i=0;i<4;i++)many=KnowledgeLibrary.add(many,"b"+i,"B","key in "+i);
        KnowledgeAnswerPolicy.Plan limited=KnowledgeAnswerPolicy.plan(many,"key","Question");
        ok(limited.matchingSources==5&&limited.includedSources==3&&limited.excerpts.size()==3);
        ok(limited.excerpts.get(0).sourceId.equals("a")&&limited.excerpts.get(1).sourceId.equals("b0")&&limited.excerpts.get(2).sourceId.equals("b1"));
        ok(KnowledgeAnswerPolicy.plan(many,"KEY","Question").excerpts.isEmpty());
        KnowledgeLibrary.State huge=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"h","H","漢".repeat(120)+"😀"+"漢".repeat(120));
        KnowledgeAnswerPolicy.Plan bounded=KnowledgeAnswerPolicy.plan(huge,"😀","漢".repeat(300));
        ok(bounded.matchingSources==1&&bounded.excerpts.size()==1&&DocumentBatchPolicy.fits(bounded.prompt,KnowledgeAnswerPolicy.TOKENS));
        ok(bounded.excerpts.get(0).text.contains("😀"));
        KnowledgeLibrary.Origin origin=new KnowledgeLibrary.Origin(KnowledgeLibrary.Kind.TEXT,"source.txt",false,1,1,3,0,0);
        KnowledgeLibrary.State imported=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"p","P","key",origin);
        KnowledgeAnswerPolicy.Plan p=KnowledgeAnswerPolicy.plan(imported,"key","Question");
        ok(p.excerpts.get(0).origin==origin&&p.excerpts.get(0).start==1&&p.excerpts.get(0).end==3);
        ok(p.prompt.contains("TEXT"));
        String repeated="first key "+"a".repeat(300)+" second key "+"b".repeat(300)+" third key";
        var repeatState=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"repeat","R",repeated);
        var repeat=KnowledgeAnswerPolicy.plan(repeatState,"key","Find key passages");
        ok(repeat.matchingSources==1&&repeat.includedSources==1&&repeat.excerpts.size()==3);
        ok(DocumentBatchPolicy.fits(repeat.prompt,KnowledgeAnswerPolicy.TOKENS));
        int priorEnd=0;
        for(int i=0;i<repeat.excerpts.size();i++){
            var x=repeat.excerpts.get(i);
            ok(x.marker.equals("K"+(i+1))&&x.sourceId.equals("repeat")&&x.start>priorEnd);
            ok(x.text.equals(repeated.substring(x.start-1,x.end))&&x.text.contains("key"));
            priorEnd=x.end;
        }
        var diverse=KnowledgeLibrary.add(repeatState,"other","Other","another key");
        var d=KnowledgeAnswerPolicy.plan(diverse,"key","Find key passages");
        ok(d.includedSources==2&&d.excerpts.size()==3&&d.excerpts.get(1).sourceId.equals("other"));
        ok(d.excerpts.get(2).sourceId.equals("repeat"));
        var adjacent=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"close","Close","key key key key");
        ok(KnowledgeAnswerPolicy.plan(adjacent,"key","Question").excerpts.size()==1);
        var tight=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"tight","Tight",("漢".repeat(250)+"😀").repeat(4));
        var tightPlan=KnowledgeAnswerPolicy.plan(tight,"😀","漢".repeat(300));
        ok(DocumentBatchPolicy.fits(tightPlan.prompt,KnowledgeAnswerPolicy.TOKENS));
        int end=0;
        for(var x:tightPlan.excerpts){ok(x.start>end&&!Character.isLowSurrogate(x.text.charAt(0))&&!Character.isHighSurrogate(x.text.charAt(x.text.length()-1)));end=x.end;}
        ok(KnowledgeAnswerPolicy.answer(repeat,"[K1] [K2] [K3] [K4]").known.equals(List.of("K1","K2","K3")));
        var grouped=KnowledgeAnswerPolicy.answer(repeat,"[K1, K2; K99] ［K3、k1］ [K01, K 2, K1] K7");
        ok(grouped.known.equals(List.of("K1","K2","K3")));
        ok(grouped.unknown.equals(List.of("K99","k1","K01","K 2")));
        ok(KnowledgeAnswerPolicy.answer(repeat,"K1 K7").known.isEmpty());
        System.out.println("PASS: "+n+" bounded knowledge answer evidence, exact identity and citation-ID assertions");
    }
}
