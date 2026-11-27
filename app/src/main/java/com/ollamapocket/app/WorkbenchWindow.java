package com.ollamapocket.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowMetrics;

/** Size only explicitly opted-in workbench dialogs. Keep normal dialog height/IME behavior. */
final class WorkbenchWindow {
    private WorkbenchWindow(){}
    static void fit(Activity owner,Dialog dialog){
        if(owner.isFinishing()||owner.isDestroyed()||!dialog.isShowing()||dialog.getWindow()==null)return;
        WorkbenchStyle.dialogActions(owner,dialog);
        final Window window=dialog.getWindow();
        final View ownerRoot=owner.getWindow().getDecorView(),decor=window.getDecorView();
        final Runnable resize=()->{
            if(!dialog.isShowing()||owner.isFinishing()||owner.isDestroyed()||dialog.getWindow()!=window)return;
            float density=owner.getResources().getDisplayMetrics().density;
            if(!Float.isFinite(density)||density<=0)return;
            int usable=ownerRoot.getWidth();
            if(Build.VERSION.SDK_INT>=30){
                WindowMetrics metrics=owner.getWindowManager().getCurrentWindowMetrics();
                Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                int width=Math.max(0,metrics.getBounds().width()-insets.left-insets.right);
                if(width>0)usable=usable>0?Math.min(usable,width):width;
            }else{
                WindowInsets insets=ownerRoot.getRootWindowInsets();
                if(insets!=null&&usable>0)usable=Math.max(0,usable-insets.getStableInsetLeft()-insets.getStableInsetRight());
            }
            if(usable<=0)return;
            int width=WorkbenchLayoutPolicy.dialogWidthDp((int)(usable/density),owner.getResources().getConfiguration().fontScale);
            int target=Math.min(usable,Math.round(width*density));
            if(target>0&&window.getAttributes().width!=target)window.setLayout(target,window.getAttributes().height);
        };
        View.OnLayoutChangeListener changed=(v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)resize.run();};
        ownerRoot.addOnLayoutChangeListener(changed);
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){
                ownerRoot.removeOnLayoutChangeListener(changed);decor.removeCallbacks(resize);decor.removeOnAttachStateChangeListener(this);
            }
        });
        resize.run();decor.post(resize);
    }
}
