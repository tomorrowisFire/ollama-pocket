package com.ollamapocket.app;

import java.util.*;
import java.util.concurrent.*;

public final class ModelSearchBatchTest {
    private static int checks;
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("search batch check "+checks);}
    private static void catalogReadBounds()throws Exception{
        byte[] utf8="한국어 中文 日本語 😀".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ok(ModelVariantCatalog.readCatalogText(new java.io.ByteArrayInputStream(utf8),utf8.length,()->0L).equals("한국어 中文 日本語 😀"));
        for(byte[] invalid:new byte[][]{new byte[]{(byte)0xff},new byte[utf8.length+1]}){
            try{ModelVariantCatalog.readCatalogText(new java.io.ByteArrayInputStream(invalid),utf8.length,()->0L);throw new AssertionError("Bad catalog accepted");}
            catch(java.io.IOException expected){checks++;}
        }
        java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
        java.io.InputStream slow=new java.io.InputStream(){
            @Override public int read(){throw new AssertionError("Single-byte read");}
            @Override public int read(byte[] bytes){
                reads.incrementAndGet();clock.set(ModelVariantCatalog.CATALOG_READ_BUDGET_NS);
                bytes[0]='x';return 1;
            }
        };
        try{ModelVariantCatalog.readCatalogText(slow,10,clock::get);throw new AssertionError("Expired catalog accepted");}
        catch(java.net.SocketTimeoutException expected){ok(reads.get()==1);}
        Thread.currentThread().interrupt();
        try{
            try{ModelVariantCatalog.readCatalogText(slow,10,()->0L);throw new AssertionError("Cancelled catalog read");}
            catch(java.io.InterruptedIOException expected){ok(reads.get()==1);}
        }finally{Thread.interrupted();}
        java.io.InputStream stalled=new java.io.InputStream(){
            @Override public int read(){return 0;}
            @Override public int read(byte[] bytes){return 0;}
        };
        try{ModelVariantCatalog.readCatalogText(stalled,10,()->0L);throw new AssertionError("Zero-progress read");}
        catch(java.io.IOException expected){checks++;}
    }
    public static void main(String[] args)throws Exception{
        catalogReadBounds();
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
        var unknown=new ModelVariantCatalog.Variant("Ollama","a:latest","",0);
        var otherFile=new ModelVariantCatalog.Variant("Hugging Face","org/b","b2.gguf",20);
        var otherCase=new ModelVariantCatalog.Variant("Hugging Face","org/B","b.gguf",30);
        var duplicates=ModelVariantCatalog.collect(List.of(CompletableFuture.completedFuture(List.of(unknown,b)),
                CompletableFuture.completedFuture(List.of(a,b,otherFile,otherCase,unknown))));
        ok(duplicates.complete()&&duplicates.values.equals(List.of(a,b,otherFile,otherCase)));
        var malformed=ModelVariantCatalog.collect(List.of(CompletableFuture.completedFuture(Arrays.asList(a,null,b))));
        ok(!malformed.complete()&&malformed.failedLookups==1&&malformed.values.equals(List.of(a,b)));
        List<ModelVariantCatalog.Variant> many=new ArrayList<>();
        for(int i=0;i<=ModelVariantCatalog.MAX_BATCH_VARIANTS;i++)
            many.add(new ModelVariantCatalog.Variant("Ollama","model"+i+":latest","",10));
        var capped=ModelVariantCatalog.collect(List.of(CompletableFuture.completedFuture(many)));
        ok(!capped.complete()&&capped.failedLookups==1&&capped.values.size()==ModelVariantCatalog.MAX_BATCH_VARIANTS);
        ok(capped.values.get(0)==many.get(0)&&capped.values.get(capped.values.size()-1)==many.get(4095));
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
        var negative=new ModelSearchCache(2,600000L);
        negative.put("none",List.of(),1000L);
        negative.put("found",List.of(a),1000L);
        ok(negative.get("none",31000L).isEmpty());
        ok(negative.get("none",31001L)==null);
        ok(negative.get("found",31001L).equals(List.of(a)));
        negative.put("none",List.of(),40000L);
        negative.put("next",List.of(b),70001L);
        ok(negative.get("none",70001L)==null);
        ok(negative.get("found",70001L).equals(List.of(a)));
        ok(negative.get("next",70001L).equals(List.of(b)));
        var shortNegative=new ModelSearchCache(1,1000L);
        shortNegative.put("none",List.of(),1000L);
        ok(shortNegative.get("none",2000L).isEmpty());
        ok(shortNegative.get("none",2001L)==null);
        negative.put("none",List.of(),80000L);
        ok(negative.get("none",79999L)==null);
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
