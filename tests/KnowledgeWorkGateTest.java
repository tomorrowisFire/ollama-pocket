package com.ollamapocket.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate project/knowledge gates; model the exact queued UI delivery contract. */
public final class KnowledgeWorkGateTest {
    private static int checks;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        ProjectWorkGate project=new ProjectWorkGate(),knowledge=new ProjectWorkGate();
        AtomicInteger screen=new AtomicInteger(),writes=new AtomicInteger(),ui=new AtomicInteger();
        List<Runnable> queue=new ArrayList<>();
        for(int round=0;round<500;round++){
            ProjectWorkGate.Lease p=project.acquire(),k=knowledge.acquire();
            ok(p!=null&&k!=null&&project.busy()&&knowledge.busy());
            ok(project.acquire()==null&&knowledge.acquire()==null);
            int reviewed=screen.incrementAndGet(),before=ui.get();
            boolean failure=(round&1)==1;
            IOException fault=new IOException("fixture");
            ok(k.execute(()->{writes.incrementAndGet();if(failure)throw fault;return "saved";},queue::add,
                (value,error)->{ok(!knowledge.busy()&&project.busy());ok(failure?error==fault&&value==null:error==null&&"saved".equals(value));if(screen.get()==reviewed)ui.incrementAndGet();}));
            ok(knowledge.busy()&&project.busy()&&queue.size()==1);
            // Navigation does not release an in-flight operation or cancel a confirmed write.
            if(round%3==0)screen.incrementAndGet();
            Runnable completion=queue.remove(0);completion.run();
            ok(!knowledge.busy()&&project.busy()&&writes.get()==round+1);
            ok(ui.get()==before+(round%3==0?0:1));
            ProjectWorkGate.Lease replacement=knowledge.acquire();ok(replacement!=null);
            completion.run();ok(knowledge.busy()&&ui.get()==before+(round%3==0?0:1));
            ok(!k.release()&&replacement.release()&&p.release());
        }
        for(boolean throwsOnPost:new boolean[]{false,true}){
            ProjectWorkGate.Lease lease=knowledge.acquire();
            ok(!lease.execute(()->"done",action->{queue.add(action);if(throwsOnPost)throw new IllegalStateException("fixture");return false;},
                (value,error)->{throw new AssertionError("rejected UI delivery");}));
            ok(!knowledge.busy());
            ProjectWorkGate.Lease next=knowledge.acquire();queue.remove(0).run();
            ok(knowledge.busy()&&!lease.release()&&next.release());
        }
        ok(!project.busy()&&!knowledge.busy()&&queue.isEmpty());
        System.out.println("PASS: "+checks+" independent workspace ownership and stale delivery assertions");
    }
}
