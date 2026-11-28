package com.ollamapocket.app;
import java.util.*;

/** Bounded, short-lived, memory-only cache: no queries or model data leave this process. */
public final class ModelSearchCache {
    static final int MAX_VARIANTS=8192, MAX_ENTRY_VARIANTS=4096, MAX_KEY_CHARS=512;
    static final long MAX_TEXT_CHARS=2_000_000L, MAX_ENTRY_CHARS=500_000L;
    private final int limit;private final long ttlMillis;
    private int variants;private long textChars;
    private Object writeEpoch=new Object();
    private static final class Entry {
        final long stamped,chars;final List<ModelVariantCatalog.Variant> values;
        Entry(long time,List<ModelVariantCatalog.Variant> list,long chars){
            stamped=time;this.chars=chars;values=Collections.unmodifiableList(new ArrayList<>(list));
        }
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,0.75f,true);
    public ModelSearchCache(int limit,long ttlMillis) {
        this.limit=Math.max(1,Math.min(64,limit));this.ttlMillis=Math.max(1000,ttlMillis);
    }
    public static String key(String provider,String query) {
        return provider.trim().toLowerCase(Locale.ROOT)+"|"+
            query.trim().toLowerCase(Locale.ROOT);
    }
    private void remove(String key){
        Entry old=entries.remove(key);
        if(old!=null){variants-=old.values.size();textChars-=old.chars;}
    }
    private void pruneExpired(long now){
        Iterator<Map.Entry<String,Entry>> it=entries.entrySet().iterator();
        while(it.hasNext()){
            Entry e=it.next().getValue();
            if(now<e.stamped||now-e.stamped>ttlMillis){
                variants-=e.values.size();textChars-=e.chars;it.remove();
            }
        }
    }
    public synchronized List<ModelVariantCatalog.Variant> get(String key,long now) {
        if(now>=0)pruneExpired(now);
        Entry entry=entries.get(key);
        if(entry==null)return null;
        if(now<0||now<entry.stamped||now-entry.stamped>ttlMillis){
            remove(key);return null;
        }
        return entry.values;
    }
    public synchronized void put(String key,List<ModelVariantCatalog.Variant> values,long now) {
        if(key==null||key.isEmpty()||key.length()>MAX_KEY_CHARS||now<0)return;
        pruneExpired(now);
        // A replacement which cannot be cached invalidates the older complete result.
        remove(key);
        if(values==null||values.size()>MAX_ENTRY_VARIANTS)return;
        long chars=key.length();
        for(ModelVariantCatalog.Variant value:values){
            if(value==null||value.source==null||value.model==null||value.file==null||value.quant==null)return;
            chars+=(long)value.source.length()+value.model.length()+value.file.length()+value.quant.length();
            if(chars>MAX_ENTRY_CHARS)return;
        }
        entries.put(key,new Entry(now,values,chars));variants+=values.size();textChars+=chars;
        while(entries.size()>limit||variants>MAX_VARIANTS||textChars>MAX_TEXT_CHARS)
            remove(entries.keySet().iterator().next());
    }
    synchronized Object writeEpoch(){return writeEpoch;}
    synchronized void putIfCurrent(Object expected,String key,List<ModelVariantCatalog.Variant> values,long now){
        if(expected==writeEpoch)put(key,values,now);
    }
    public synchronized void clear(){entries.clear();variants=0;textChars=0;writeEpoch=new Object();}
}
