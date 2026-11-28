package com.ollamapocket.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ProjectWorkGateTest {
    private static int checks;
    private static void ok(boolean condition) {
        if (!condition) throw new AssertionError("check " + checks);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        ProjectWorkGate gate = new ProjectWorkGate();
        ok(!gate.busy());
        for (int i = 0; i < 100; i++) {
            List<Runnable> queue = new ArrayList<>();
            AtomicInteger runs = new AtomicInteger();
            AtomicInteger deliveries = new AtomicInteger();
            ProjectWorkGate.Lease first = gate.acquire();
            ok(first != null && gate.busy() && gate.acquire() == null);
            ok(first.execute(() -> { runs.incrementAndGet(); return "saved"; },
                queue::add, (value, error) -> {
                    ok("saved".equals(value) && error == null && !gate.busy());
                    deliveries.incrementAndGet();
                }));
            ok(queue.size() == 1 && gate.busy() && deliveries.get() == 0);
            ok(!first.execute(() -> { runs.incrementAndGet(); return "duplicate"; },
                queue::add, (v, e) -> { throw new AssertionError("duplicate delivery"); }));
            ok(runs.get() == 1 && gate.busy());
            queue.get(0).run();
            ok(deliveries.get() == 1 && !gate.busy());
            ProjectWorkGate.Lease second = gate.acquire();
            ok(second != null && gate.busy());
            queue.get(0).run();
            ok(gate.busy() && deliveries.get() == 1 && !first.release());
            ok(second.release() && !gate.busy());
            ok(!second.release());
        }

        AtomicReference<Runnable> pending = new AtomicReference<>();
        IOException expected = new IOException("fixture");
        ProjectWorkGate.Lease errorLease = gate.acquire();
        ok(errorLease.execute(() -> { throw expected; }, action -> { pending.set(action); return true; },
            (Object v, Exception e) -> ok(v == null && e == expected && !gate.busy())));
        ok(gate.busy());
        pending.get().run();
        ok(!gate.busy());

        for (boolean throwing : new boolean[]{false, true}) {
            ProjectWorkGate.Lease rejected = gate.acquire();
            ok(!rejected.execute(() -> "done", action -> {
                pending.set(action);
                if (throwing) throw new IllegalStateException("fixture dispatcher rejection");
                return false;
            }, (v, e) -> { throw new AssertionError("rejected completion delivered"); }));
            ok(!gate.busy());
            ProjectWorkGate.Lease replacement = gate.acquire();
            pending.get().run();
            ok(gate.busy() && !rejected.release());
            ok(replacement.release());
        }

        AssertionError fatal = new AssertionError("fixture fatal task");
        ProjectWorkGate.Lease fatalLease = gate.acquire();
        try {
            fatalLease.execute(() -> { throw fatal; }, action -> {
                throw new AssertionError("fatal error must not post");
            }, (Object v, Exception e) -> { throw new AssertionError("fatal error delivered"); });
            throw new AssertionError("fatal error swallowed");
        } catch (AssertionError e) { ok(e == fatal && !gate.busy()); }
        ProjectWorkGate.Lease startFailure = gate.acquire();
        ok(startFailure.release());
        ProjectWorkGate.Lease next = gate.acquire();
        ok(!startFailure.execute(() -> { throw new AssertionError("cancelled start executed"); },
            action -> true, (Object v, Exception e) -> {}));
        ok(gate.busy() && next.release());

        for (int round = 0; round < 20; round++) {
            int count = 16;
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger acquired = new AtomicInteger();
            AtomicReference<ProjectWorkGate.Lease> winner = new AtomicReference<>();
            Thread[] threads = new Thread[count];
            for (int i = 0; i < count; i++) {
                threads[i] = new Thread(() -> {
                    ready.countDown();
                    try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                    ProjectWorkGate.Lease lease = gate.acquire();
                    if (lease != null) { acquired.incrementAndGet(); winner.set(lease); }
                });
                threads[i].start();
            }
            ready.await();
            start.countDown();
            for (Thread thread : threads) thread.join();
            ok(acquired.get() == 1 && gate.busy());
            ok(winner.get().release() && !gate.busy());
        }
        ProjectWorkGate.Lease interrupted=gate.acquire();
        ok(interrupted.execute(()->{throw new InterruptedException("fixture");},action->{pending.set(action);return true;},
            (Object value,Exception error)->ok(value==null&&error instanceof InterruptedException&&!gate.busy())));
        ok(Thread.interrupted()&&gate.busy());
        pending.get().run();ok(!gate.busy());
        System.out.println("PASS: " + checks + " project worker ownership and dispatch assertions");
    }
}
