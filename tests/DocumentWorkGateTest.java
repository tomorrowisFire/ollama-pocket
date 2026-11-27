package com.ollamapocket.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Offline model of operation-scoped document progress and queued completion. */
public final class DocumentWorkGateTest {
    private static int checks;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        ProjectWorkGate documents=new ProjectWorkGate(),projects=new ProjectWorkGate(),knowledge=new ProjectWorkGate();
        AtomicReference<Object> screen=new AtomicReference<>();
        AtomicInteger progress=new AtomicInteger(),delivered=new AtomicInteger();
        List<Runnable> queue=new ArrayList<>();
        for(int i=0;i<600;i++){
            Object dialog=new Object();screen.set(dialog);
            ProjectWorkGate.Lease p=projects.acquire(),k=knowledge.acquire(),first=documents.acquire();
            ok(p!=null&&k!=null&&first!=null&&first.isCurrent()&&documents.acquire()==null);
            int before=progress.get(),count=delivered.get();
            Runnable oldProgress=()->{if(first.isCurrent()&&screen.get()==dialog)progress.incrementAndGet();};
            boolean failure=(i%2)==0;IOException fault=new IOException("fixture");
            ok(first.execute(()->{if(failure)throw fault;return "report";},queue::add,(value,error)->{
                ok(!first.isCurrent()&&!documents.busy());
                ok(failure?value==null&&error==fault:"report".equals(value)&&error==null);
                if(screen.get()==dialog)delivered.incrementAndGet();
            }));
            ok(queue.size()==1&&first.isCurrent()&&documents.busy());
            oldProgress.run();ok(progress.get()==before+1);
            boolean closed=i%3==0;if(closed)screen.set(new Object());
            oldProgress.run();ok(progress.get()==before+(closed?1:2));
            Runnable completion=queue.remove(0);completion.run();
            ok(!first.isCurrent()&&!documents.busy()&&delivered.get()==count+(closed?0:1));
            ProjectWorkGate.Lease next=documents.acquire();ok(next!=null&&next.isCurrent());
            screen.set(dialog);int updated=progress.get();oldProgress.run();completion.run();
            ok(progress.get()==updated&&delivered.get()==count+(closed?0:1));
            ok(!first.release()&&next.isCurrent()&&p.isCurrent()&&k.isCurrent());
            ok(next.release()&&p.release()&&k.release());
        }
        for(boolean throwing:new boolean[]{false,true}){
            ProjectWorkGate.Lease lease=documents.acquire();
            ok(!lease.execute(()->"report",action->{queue.add(action);if(throwing)throw new IllegalStateException("rejected");return false;},
                (v,e)->{throw new AssertionError("rejected delivery");}));
            ok(!lease.isCurrent()&&!documents.busy());
            ProjectWorkGate.Lease next=documents.acquire();queue.remove(0).run();
            ok(next.isCurrent()&&!lease.release()&&next.release());
        }
        AssertionError fatal=new AssertionError("fixture");ProjectWorkGate.Lease lease=documents.acquire();
        try{
            lease.execute(()->{throw fatal;},action->{throw new AssertionError("unexpected post");},(Object v,Exception e)->{});
            throw new AssertionError("fatal swallowed");
        }catch(AssertionError e){ok(e==fatal&&!lease.isCurrent()&&!documents.busy());}
        ProjectWorkGate.Lease unstarted=documents.acquire();ok(unstarted.isCurrent()&&unstarted.release()&&!unstarted.isCurrent());
        ok(!documents.busy()&&!projects.busy()&&!knowledge.busy()&&queue.isEmpty());
        System.out.println("PASS: "+checks+" document operation ownership and stale progress assertions");
    }
}
