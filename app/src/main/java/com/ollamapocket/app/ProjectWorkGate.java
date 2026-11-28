package com.ollamapocket.app;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/** Exclusive operation ownership until terminal delivery or failed dispatch. */
final class ProjectWorkGate {
    private final AtomicReference<Lease> owner = new AtomicReference<>();

    boolean busy() { return owner.get() != null; }

    Lease acquire() {
        Lease lease = new Lease(this);
        return owner.compareAndSet(null, lease) ? lease : null;
    }

    interface Task<T> { T run() throws Exception; }
    interface Poster { boolean post(Runnable completion); }

    static final class Lease {
        private final ProjectWorkGate gate;
        private final AtomicBoolean started = new AtomicBoolean();

        private Lease(ProjectWorkGate gate) { this.gate = gate; }

        /** Check the originating operation before delivering nonterminal progress. */
        boolean isCurrent() { return gate.owner.get() == this; }

        boolean release() { return gate.owner.compareAndSet(this, null); }

        /** A poster must enqueue asynchronously, not call the completion inline. */
        <T> boolean execute(Task<T> task, Poster poster, BiConsumer<T, Exception> completion) {
            if (!started.compareAndSet(false, true) || gate.owner.get() != this) return false;
            boolean posted = false;
            try {
                T result = null;
                Exception problem = null;
                try { result = task.run(); } catch (Exception e) {
                    if(e instanceof InterruptedException)Thread.currentThread().interrupt();
                    problem = e;
                }
                final T value = result;
                final Exception error = problem;
                try {
                    posted = poster.post(() -> {
                        if (release()) completion.accept(value, error);
                    });
                } catch (RuntimeException rejected) {
                    // The UI dispatcher cannot accept the result. No off-thread UI access.
                    return false;
                }
                return posted;
            } finally {
                // Includes fatal task errors, without swallowing or converting them to success.
                if (!posted) release();
            }
        }
    }
}
