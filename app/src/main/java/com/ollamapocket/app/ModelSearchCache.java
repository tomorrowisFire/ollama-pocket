package com.ollamapocket.app;
import java.util.*;

/** Bounded, short-lived, memory-only cache: no queries or model data leave this process. */
public final class ModelSearchCache {
    private final int limit;private final long ttlMillis;
    private static final class Entry {
        final long stamped;final List<ModelVariantCatalog.Variant> values;
        Entry(long time,List<ModelVariantCatalog.Variant> list){
            stamped=time;values=Collections.unmodifiableList(new ArrayList<>(list));
        }
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,0.75f,true);
    public ModelSearchCache(int limit,long ttlMillis) {
        this.limit=Math.max(1,limit);this.ttlMillis=Math.max(1000,ttlMillis);
    }
    public static String key(String provider,String query) {
        return provider.trim().toLowerCase(Locale.ROOT)+"|"+
            query.trim().toLowerCase(Locale.ROOT);
    }
    public synchronized List<ModelVariantCatalog.Variant> get(String key,long now) {
        Entry entry=entries.get(key);
        if(entry==null)return null;
        if(now<entry.stamped||now-entry.stamped>ttlMillis){
            entries.remove(key);return null;
        }
        return entry.values;
    }
    public synchronized void put(String key,List<ModelVariantCatalog.Variant> values,long now) {
        entries.put(key,new Entry(now,values));
        while(entries.size()>limit){
            String oldest=entries.keySet().iterator().next();entries.remove(oldest);
        }
    }
    public synchronized void clear(){entries.clear();}
}
