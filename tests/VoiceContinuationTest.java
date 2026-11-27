package com.ollamapocket.app;

public final class VoiceContinuationTest {
    private static int checks;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args){
        VoiceContinuation c=new VoiceContinuation(),other=new VoiceContinuation();
        int[] calls={0,0};Runnable first=()->calls[0]++,second=()->calls[1]++;
        ok(!c.pending());ok(c.schedule()==null);ok(c.consume(null)==null);
        c.replace(first);ok(c.pending());VoiceContinuation.Ticket a=c.schedule();
        ok(a!=null&&!c.pending());ok(c.schedule()==null);
        other.replace(second);VoiceContinuation.Ticket foreign=other.schedule();
        ok(c.consume(foreign)==null);ok(other.consume(a)==null);
        Runnable claimed=c.consume(a);ok(claimed==first);claimed.run();ok(calls[0]==1);
        ok(c.consume(a)==null&&!c.pending());ok(other.consume(foreign)==second);
        c.replace(first);a=c.schedule();c.replace(second);
        ok(c.consume(a)==null&&c.pending());VoiceContinuation.Ticket b=c.schedule();
        ok(a!=b);ok(c.consume(a)==null);claimed=c.consume(b);ok(claimed==second);claimed.run();ok(calls[1]==1);
        c.replace(first);a=c.schedule();c.replace(null);
        ok(c.consume(a)==null&&!c.pending());ok(c.schedule()==null);
        c.replace(first);c.clear();ok(!c.pending()&&c.schedule()==null);
        c.replace(first);a=c.schedule();c.clear();c.replace(first);b=c.schedule();
        ok(c.consume(a)==null);ok(c.consume(b)==first);ok(c.consume(b)==null);
        // TTS initialization: latest pending request alone owns continuation.
        c.replace(first);c.replace(second);ok(c.pending());a=c.schedule();ok(c.consume(a)==second);
        c.replace(first);c.replace(null);ok(!c.pending()&&c.schedule()==null);
        // Barge-in and completion may both attempt to schedule; only one owns the tail.
        for(int i=0;i<32;i++){
            c.replace(first);a=c.schedule();ok(a!=null);ok(c.schedule()==null);
            ok(c.consume(a)==first);ok(c.consume(a)==null);ok(!c.pending());
        }
        ok(calls[0]==1&&calls[1]==1);
        System.out.println("PASS: "+checks+" voice continuation ownership assertions");
    }
}
