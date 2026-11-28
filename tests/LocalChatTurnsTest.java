package com.ollamapocket.app;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LocalChatTurnsTest {
    private static int n;
    private static void ok(boolean v,String label){if(!v)throw new AssertionError(label);n++;}
    private static LocalChatTurns.Message u(String s){return new LocalChatTurns.Message("user",s);}
    private static LocalChatTurns.Message a(String s){return new LocalChatTurns.Message("assistant",s);}
    private static void invalid(Runnable r,String label){try{r.run();}catch(IllegalArgumentException e){n++;return;}throw new AssertionError(label);}
    private static List<LocalChatTurns.Message> reference(List<LocalChatTurns.Message> source,String prompt){
        List<LocalChatTurns.Message[]> pairs=new ArrayList<>();
        for(int i=0;i+1<source.size();i++){
            LocalChatTurns.Message q=source.get(i),a=source.get(i+1);
            if(q!=null&&a!=null&&q.user()&&a.assistant()){pairs.add(new LocalChatTurns.Message[]{q,a});i++;}
        }
        List<LocalChatTurns.Message> out=new ArrayList<>();int chars=prompt.length();
        for(int i=pairs.size()-1;i>=0&&out.size()/2<LocalChatTurns.MAX_CONTEXT_PAIRS;i--){
            LocalChatTurns.Message[] pair=pairs.get(i);
            long next=(long)chars+pair[0].content.length()+pair[1].content.length();
            if(next>LocalChatTurns.MAX_CONTEXT_CHARS)break;
            out.add(0,pair[1]);out.add(0,pair[0]);chars=(int)next;
        }
        return out;
    }
    public static void main(String[] args){
        List<LocalChatTurns.Message> h=new ArrayList<>(Arrays.asList(a("stray"),u("first"),a("answer"),u("failed"),u("latest"),a("last"),u("orphan")));
        List<LocalChatTurns.Message> req=LocalChatTurns.request(h,"new");
        ok(req.size()==5,"two complete pairs plus new prompt");
        ok("first".equals(req.get(0).content)&&"latest".equals(req.get(2).content),"orphan omitted");
        ok("new".equals(req.get(4).content)&&h.size()==7,"source preserved");
        LocalChatTurns.Branch b=LocalChatTurns.branch(h,5);
        ok(b.prefixLength==4&&"latest".equals(b.prompt),"regenerate paired question");
        b=LocalChatTurns.branch(h,1);ok(b.prefixLength==1&&"first".equals(b.prompt),"edit earlier question");
        invalid(()->LocalChatTurns.branch(h,0),"stray assistant");
        invalid(()->LocalChatTurns.branch(h,99),"invalid index");
        invalid(()->LocalChatTurns.request(h," "),"blank question");
        invalid(()->LocalChatTurns.request(h,"x".repeat(16001)),"question limit");
        ok(LocalChatTurns.validReply("answer")&&!LocalChatTurns.validReply(" ")&&!LocalChatTurns.validReply("x".repeat(160001)),"reply validation");
        ok(LocalChatTurns.storageFits(256,512000)&&!LocalChatTurns.storageFits(257,512000)&&!LocalChatTurns.storageFits(256,512001),"storage bound");
        List<LocalChatTurns.Message> many=new ArrayList<>();
        for(int i=0;i<30;i++){many.add(u("q"+i));many.add(a("a"+i));}
        req=LocalChatTurns.request(many,"next");
        ok(req.size()==31&&"q15".equals(req.get(0).content),"context only trims request");
        ok(many.size()==60,"stored source unchanged");
        req=LocalChatTurns.request(Arrays.asList(u("a"),a("x".repeat(47000))),"q".repeat(1000));
        ok(req.size()==1,"whole-pair character limit");
        req=LocalChatTurns.request(Arrays.asList(u("old"),null,a("invalid"),u("newer"),a("valid")),"new");
        ok(req.size()==3&&"newer".equals(req.get(0).content),"invalid entries do not pair");
        ok(LocalChatTurns.gpuTerminal("stop")&&LocalChatTurns.gpuTerminal("length")&&!LocalChatTurns.gpuTerminal("tool_calls")&&!LocalChatTurns.gpuTerminal(""),"terminal status");
        java.util.Random random=new java.util.Random(77);
        for(int trial=0;trial<800;trial++){
            List<LocalChatTurns.Message> source=trial%2==0?new ArrayList<>():new java.util.LinkedList<>();
            for(int i=0,count=random.nextInt(260);i<count;i++){
                int type=random.nextInt(7);
                source.add(type==0?null:type==1?u(" "):type==2?a(" "):type==3?u("long".repeat(15000)):
                    type==4?a("answer "+i):type==5?u("question "+i):new LocalChatTurns.Message("system","legacy"));
            }
            List<LocalChatTurns.Message> expected=reference(source,"latest");
            List<LocalChatTurns.Message> actual=LocalChatTurns.request(source,"latest");
            ok(actual.size()==expected.size()+1,"suffix size");
            for(int i=0;i<expected.size();i++)ok(actual.get(i)==expected.get(i),"exact legacy context equivalence");
            ok(actual.get(actual.size()-1).content.equals("latest"),"preserve prompt");
        }
        for(String bad:new String[]{"\u3000","\u00a0\u200d","bad\u0000text","\ud800","\udc00"}){
            ok(!LocalChatTurns.validPrompt(bad)&&!LocalChatTurns.validReply(bad),"malformed or invisible text");
        }
        ok(LocalChatTurns.validPrompt("한글 👩‍💻\n\t日本語")&&LocalChatTurns.validReply("中文"),"Unicode preserved");
        ok(LocalChatTurns.validFragment("\n\t")&&!LocalChatTurns.validFragment("bad\u0000"),"fragment validation");
        List<LocalChatTurns.Message> legacy=Arrays.asList(u("ok"),a("answer"),u("broken\ud800"),a("old invalid"),u("new"),a("fine"));
        req=LocalChatTurns.request(legacy,"next");
        ok(req.size()==5&&req.get(2).content.equals("new")&&legacy.size()==6,"skip corrupt legacy context without modifying history");
        System.out.println("LocalChatTurnsTest PASS: "+n+" assertions");
    }
}
