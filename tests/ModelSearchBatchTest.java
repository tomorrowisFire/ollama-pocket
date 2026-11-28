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
        var bounded=new ModelSearchCache(20,1000);
        var oversized=Collections.nCopies(ModelSearchCache.MAX_ENTRY_VARIANTS+1,a);
        bounded.put("old",List.of(a),1000);
        bounded.put("old",oversized,1001);
        ok(bounded.get("old",1001)==null);
        var large=Collections.nCopies(ModelSearchCache.MAX_ENTRY_VARIANTS,a);
        bounded.put("first",large,1000);bounded.put("second",large,1000);
        ok(bounded.get("first",1000).size()==large.size()); // refresh LRU
        bounded.put("third",large,1000);
        ok(bounded.get("second",1000)==null);
        ok(bounded.get("first",1000)!=null&&bounded.get("third",1000)!=null);
        var longName=new ModelVariantCatalog.Variant("Ollama","x".repeat(4000),"",10);
        bounded.put("huge-text",Collections.nCopies(150,longName),1000);
        ok(bounded.get("huge-text",1000)==null);
        bounded.put("null",Arrays.asList(a,null),1000);ok(bounded.get("null",1000)==null);
        bounded.put("empty",List.of(),1000);ok(bounded.get("empty",1000).isEmpty());
        ok(bounded.get("empty",2001)==null);
        bounded.clear();ok(bounded.get("first",1000)==null);
        bounded.put("after-clear",List.of(b),1000);ok(bounded.get("after-clear",1000).equals(List.of(b)));
        var expiring=new ModelSearchCache(2,1000);
        expiring.put("expired",List.of(a),1000);expiring.put("recent",List.of(b),1900);
        ok(expiring.get("expired",1900)!=null); // expired entry is most recently used
        expiring.put("new",List.of(a),2001);
        ok(expiring.get("expired",2001)==null&&expiring.get("recent",2001)!=null&&expiring.get("new",2001)!=null);
        var memory=new ModelSearchCache(2,1000);
        Object staleEpoch=memory.writeEpoch();
        memory.putIfCurrent(staleEpoch,"old",List.of(a),1000);
        ok(memory.get("old",1000)!=null);
        memory.clear();
        memory.putIfCurrent(staleEpoch,"old",List.of(a),1001);
        ok(memory.get("old",1001)==null);
        Object currentEpoch=memory.writeEpoch();
        memory.putIfCurrent(currentEpoch,"new",List.of(b),1001);
        ok(memory.get("new",1001).equals(List.of(b)));
        memory.putIfCurrent(staleEpoch,"new",List.of(a),1002);
        ok(memory.get("new",1002).equals(List.of(b)));
        System.out.println("PASS: "+checks+" model search completeness, cancellation and bounded cache checks");
    }
}
