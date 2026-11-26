package com.ollamapocket.app;

import android.content.ClipData;
import android.os.PersistableBundle;

/** Hides sensitive content in supported Android clipboard previews.
    Clipboard is still accessible according to Android and keyboard/clipboard settings. */
final class SensitiveClipboard {
    private SensitiveClipboard(){}
    static ClipData text(CharSequence label,CharSequence value){
        ClipData clip=ClipData.newPlainText(label,value);
        PersistableBundle extras=new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE",true);
        clip.getDescription().setExtras(extras);
        return clip;
    }
}
