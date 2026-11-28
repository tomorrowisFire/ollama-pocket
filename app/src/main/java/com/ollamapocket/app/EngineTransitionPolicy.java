package com.ollamapocket.app;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** A handoff may proceed only while its own generation and admission remain current. */
final class EngineTransitionPolicy {
    private EngineTransitionPolicy(){}
    static final long RELEASE_BUDGET_MS=15_000L;
    static final int RELEASE_REQUEST_CAP_MS=3_000;
    /** A monotonic aggregate budget, checked between I/O operations. */
    static int releaseTimeoutMs(long start,long now){
        if(start<0||now<start||now-start>=RELEASE_BUDGET_MS)return 0;
        return (int)Math.min(RELEASE_REQUEST_CAP_MS,RELEASE_BUDGET_MS-(now-start));
    }
    static BooleanSupplier abortSignal(long expected,LongSupplier generation,
                                       BooleanSupplier cancelled,BooleanSupplier safe){
        return () -> generation.getAsLong()!=expected||cancelled.getAsBoolean()||
            Thread.currentThread().isInterrupted()||!safe.getAsBoolean()||
            generation.getAsLong()!=expected||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted();
    }
}
