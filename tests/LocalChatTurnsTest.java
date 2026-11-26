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
        System.out.println("LocalChatTurnsTest PASS: "+n+" assertions");
    }
}
