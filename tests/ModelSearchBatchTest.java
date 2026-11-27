package com.ollamapocket.app;

import java.util.*;
import java.util.concurrent.*;

public final class ModelSearchBatchTest {
    private static int checks;
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("search batch check "+checks);}
    public static void main(String[] args)throws Exception{
        var a=new ModelVariantCatalog.Variant("Ollama","a:latest","",10);
        var b=new ModelVariantCatalog.Variant("Hugging Face","org/b","b.gguf",20);
        var failed=new CompletableFuture<List<ModelVariantCatalog.Variant>>();
        failed.completeExceptionally(new java.io.IOException("HTTP 429"));
        var cancelled=new CompletableFuture<List<ModelVariantCatalog.Variant>>();
        cancelled.cancel(false);
        var empty=CompletableFuture.completedFuture(List.<ModelVariantCatalog.Variant>of());
        var partial=ModelVariantCatalog.collect(List.of(CompletableFuture.completedFuture(List.of(a)),failed,cancelled,
                CompletableFuture.completedFuture(List.of(b)),empty));
        ok(partial.values.equals(List.of(a,b))&&partial.failedLookups==2&&!partial.complete());
        ok(ModelVariantCatalog.collect(List.of(empty)).complete());
        ok(ModelVariantCatalog.collect(List.of(failed,cancelled)).failedLookups==2);
        ok(ModelVariantCatalog.collect(List.of()).values.isEmpty());
        ok(ModelVariantCatalog.collect(List.of(CompletableFuture.completedFuture(null))).failedLookups==1);
        try{partial.values.add(a);throw new AssertionError("Mutable batch");}catch(UnsupportedOperationException expected){checks++;}
        Thread.currentThread().interrupt();
        try{ModelVariantCatalog.collect(List.of(empty));throw new AssertionError("Cancelled query accepted");}
        catch(InterruptedException expected){checks++;}
        finally{Thread.interrupted();}
        var cache=new ModelSearchCache(2,1000);
        if(partial.complete())cache.put("query",partial.values,1000);
        ok(cache.get("query",1000)==null);
        var complete=ModelVariantCatalog.collect(List.of(CompletableFuture.completedFuture(List.of(b))));
        if(complete.complete())cache.put("query",complete.values,1000);
        ok(cache.get("query",1000).equals(List.of(b)));
        System.out.println("PASS: "+checks+" model search completeness, cancellation and cache admission checks");
    }
}
