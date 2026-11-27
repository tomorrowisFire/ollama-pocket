package com.ollamapocket.app;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public final class VisionTurnReviewTest {
    private static int checks;
    private interface Action{void run()throws Exception;}
    private static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void reject(Class<? extends Throwable> type,Action a)throws Exception{
        checks++;try{a.run();}catch(Throwable e){if(type.isInstance(e))return;throw new AssertionError(e);}
        throw new AssertionError("Expected "+type.getSimpleName());
    }
    private static VisionTurnReview.Backend backend(String generation){
        return new VisionTurnReview.Backend(generation,VisionModelProfile.BACKEND_COMMIT,VisionModelProfile.REVISION,
                VisionModelProfile.WEIGHTS.sha256,VisionModelProfile.WEIGHTS.bytes,
                VisionModelProfile.PROJECTOR.sha256,VisionModelProfile.PROJECTOR.bytes,true);
    }
    private static VisionImagePolicy.Prepared image(int marker)throws Exception{
        return VisionImagePolicy.finish(new byte[]{(byte)255,(byte)216,(byte)255,(byte)marker,(byte)255,(byte)217},32,32);
    }
    private static VisionTurnReview.Review approved(VisionTurnReview guard,VisionTurnReview.Backend b,
                                                   VisionImagePolicy.Prepared i)throws Exception{
        VisionTurnReview.Review r=guard.prepare(b,i,"한국어 😀",16,()->false);ok(guard.approve(r));return r;
    }
    public static void main(String[] args)throws Exception{
        VisionTurnReview.Backend b=backend("backend-generation-1"),changed=backend("backend-generation-2");
        VisionImagePolicy.Prepared i=image(1),other=image(2);
        reject(IllegalArgumentException.class,()->backend("short"));
        reject(IllegalArgumentException.class,()->new VisionTurnReview.Backend("backend-generation-1","wrong",
                VisionModelProfile.REVISION,VisionModelProfile.WEIGHTS.sha256,VisionModelProfile.WEIGHTS.bytes,
                VisionModelProfile.PROJECTOR.sha256,VisionModelProfile.PROJECTOR.bytes,true));
        reject(IllegalArgumentException.class,()->new VisionTurnReview.Backend("backend-generation-1",VisionModelProfile.BACKEND_COMMIT,
                VisionModelProfile.REVISION,VisionModelProfile.WEIGHTS.sha256,VisionModelProfile.WEIGHTS.bytes,
                VisionModelProfile.PROJECTOR.sha256,VisionModelProfile.PROJECTOR.bytes,false));
        AtomicLong now=new AtomicLong(0);
        try(VisionTurnReview g=new VisionTurnReview(now::get);VisionTurnReview foreign=new VisionTurnReview(now::get)){
            VisionTurnReview.Review r=g.prepare(b,i,"한국어 😀",16,()->false);
            ok(r.imageSha256.equals(i.sha256)&&r.width==32&&r.height==32);
            ok(g.begin(r,b,i,"한국어 😀",16,()->false)==null&&!foreign.approve(r));
            ok(g.approve(r)&&!g.approve(r));
            ok(g.begin(r,changed,i,"한국어 😀",16,()->false)==null&&!g.approve(r));
            r=approved(g,b,i);ok(g.begin(r,b,other,"한국어 😀",16,()->false)==null);
            r=approved(g,b,i);ok(g.begin(r,b,i,"한국어 😀 ",16,()->false)==null);
            r=approved(g,b,i);ok(g.begin(r,b,i,"한국어 😀",17,()->false)==null);
            r=approved(g,b,i);now.addAndGet(VisionTurnReview.TTL_NANOS);
            ok(g.begin(r,b,i,"한국어 😀",16,()->false)==null);
            r=approved(g,b,i);g.decline(r);ok(g.begin(r,b,i,"한국어 😀",16,()->false)==null);
            VisionTurnReview.Review cancelled=approved(g,b,i);
            reject(InterruptedIOException.class,()->g.begin(cancelled,b,i,"한국어 😀",16,()->true));
            ok(g.begin(cancelled,b,i,"한국어 😀",16,()->false)==null);
            r=approved(g,b,i);VisionTurnReview.Lease l=g.begin(r,b,i,"한국어 😀",16,()->false);
            ok(l!=null&&g.inFlight()&&g.begin(r,b,i,"한국어 😀",16,()->false)==null);
            reject(IllegalStateException.class,()->g.prepare(b,i,"next",16,()->false));
            byte[] actual=l.takePayload(()->false);
            ok(Arrays.equals(actual,VisionChatRequest.build(i,"한국어 😀",16,()->false)));
            Arrays.fill(actual,(byte)0);
            reject(IOException.class,()->l.takePayload(()->false));l.close();l.close();ok(!g.inFlight());
            r=approved(g,b,i);VisionTurnReview.Lease pending=g.begin(r,b,i,"한국어 😀",16,()->false);
            g.cancel();ok(g.inFlight());reject(IOException.class,()->pending.takePayload(()->false));
            pending.close();ok(!g.inFlight());
            r=approved(g,b,i);g.close();ok(g.begin(r,b,i,"한국어 😀",16,()->false)==null);
        }
        System.out.println("PASS: "+checks+" exact vision request review and one-shot ownership checks");
    }
}
