package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/** Explicit locale preference without changing process-wide locale or model identifiers. */
public final class OnHandsLocale {
    private static final String PREF = "onhands_language";
    private static final String[] TAGS = {"system","ko","en","zh-Hans","zh-Hant","ja"};
    private static final String[] LABELS = {"","한국어","English","简体中文","繁體中文","日本語"};
    private OnHandsLocale() {}
    private static String choice(Context c) {
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("tag","system");
    }
    static String normalize(Locale locale) {
        String l=locale.getLanguage();
        if ("zh".equals(l)) {
            String script=locale.getScript(), region=locale.getCountry();
            if ("Hant".equals(script) || (script.isEmpty() && ("TW".equals(region)||"HK".equals(region)||"MO".equals(region)))) return "zh-Hant";
            return "zh-Hans";
        }
        return ("ko".equals(l)||"ja".equals(l)||"en".equals(l)) ? l : "en";
    }
    static Context wrap(Context base) {
        String selected=choice(base);
        if ("system".equals(selected)) selected=normalize(base.getResources().getConfiguration().getLocales().get(0));
        else if (!java.util.Arrays.asList(TAGS).contains(selected)) selected="en";
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocales(new LocaleList(Locale.forLanguageTag(selected)));
        return base.createConfigurationContext(config);
    }
    static Locale speechLocale(Context c) {
        switch(normalize(c.getResources().getConfiguration().getLocales().get(0))) {
            case "ko": return Locale.KOREA;
            case "ja": return Locale.JAPAN;
            case "zh-Hans": return Locale.SIMPLIFIED_CHINESE;
            case "zh-Hant": return Locale.TRADITIONAL_CHINESE;
            default: return Locale.US;
        }
    }
    static void choose(Activity a) {
        String[] labels=LABELS.clone(); labels[0]=a.getString(R.string.system_language);
        int current=Math.max(0,java.util.Arrays.asList(TAGS).indexOf(choice(a)));
        AlertDialog picker=new AlertDialog.Builder(a).setTitle(R.string.language)
            .setSingleChoiceItems(WorkbenchStyle.choiceRows(a,labels,current),current,(dialog,index)->{
                dialog.dismiss();
                if(index==current)return;
                a.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("tag",TAGS[index]).apply();
                a.recreate();
            }).setNegativeButton(R.string.cancel,null).show();
        WorkbenchWindow.fitCompact(a,picker);
    }
}
