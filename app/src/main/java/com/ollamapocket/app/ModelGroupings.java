package com.ollamapocket.app;
import java.util.Locale;

/** Stable grouping keys share size/version while preserving distinct sources. */
public final class ModelGroupings {
    private ModelGroupings(){}
    public static String key(ModelVariantCatalog.Variant variant) {
        return (variant.hf()?"hf:":"ollama:")+
            variant.group().toLowerCase(Locale.ROOT);
    }
    public static String localKey(String tag){
        return "ollama:"+ModelVariantCatalog.stripQuant(tag).toLowerCase(Locale.ROOT);
    }
}
