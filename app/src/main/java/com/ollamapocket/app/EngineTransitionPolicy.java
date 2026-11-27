package com.ollamapocket.app;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** A handoff may proceed only while its own generation and admission remain current. */
final class EngineTransitionPolicy {
    private EngineTransitionPolicy(){}
    static BooleanSupplier abortSignal(long expected,LongSupplier generation,
                                       BooleanSupplier cancelled,BooleanSupplier safe){
        return () -> generation.getAsLong()!=expected||cancelled.getAsBoolean()||
            Thread.currentThread().isInterrupted()||!safe.getAsBoolean()||
            generation.getAsLong()!=expected||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted();
    }
}
