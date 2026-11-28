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
    static void fit(Activity owner,Dialog dialog){fit(owner,dialog,false);}
    static void fitCompact(Activity owner,Dialog dialog){fit(owner,dialog,true);}
    private static void fit(Activity owner,Dialog dialog,boolean compact){
        if(owner.isFinishing()||owner.isDestroyed()||!dialog.isShowing()||dialog.getWindow()==null)return;
        WorkbenchStyle.dialogActions(owner,dialog);
        Window window=dialog.getWindow();
        View decor=window.getDecorView();
        Object previous=decor.getTag(R.id.onhand_workbench_fit);
        if(previous instanceof FitBinding)((FitBinding)previous).close();
        FitBinding binding=new FitBinding(owner,dialog,window,compact);
        decor.setTag(R.id.onhand_workbench_fit,binding);
        binding.start();
    }
    /** Exactly one sizing owner per dialog decor; detach/refit releases both listener and pending callback. */
    private static final class FitBinding implements View.OnLayoutChangeListener,View.OnAttachStateChangeListener {
        final Activity owner;
        final Dialog dialog;
        final Window window;
        final View ownerRoot,decor;
        final boolean compact;
        final Runnable resize=this::resizeNow;
        boolean closed;
        FitBinding(Activity owner,Dialog dialog,Window window,boolean compact){
            this.owner=owner;this.dialog=dialog;this.window=window;this.compact=compact;
            ownerRoot=owner.getWindow().getDecorView();decor=window.getDecorView();
        }
        void start(){
            ownerRoot.addOnLayoutChangeListener(this);
            decor.addOnAttachStateChangeListener(this);
            resize.run();decor.post(resize);
        }
        void close(){
            if(closed)return;
            closed=true;
            ownerRoot.removeOnLayoutChangeListener(this);
            decor.removeCallbacks(resize);
            decor.removeOnAttachStateChangeListener(this);
            if(decor.getTag(R.id.onhand_workbench_fit)==this)decor.setTag(R.id.onhand_workbench_fit,null);
        }
        @Override public void onViewAttachedToWindow(View v){if(!closed){decor.removeCallbacks(resize);decor.post(resize);}}
        @Override public void onViewDetachedFromWindow(View v){close();}
        @Override public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
            if(r-l!=or-ol)resize.run();
        }
        private void resizeNow(){
            if(closed||decor.getTag(R.id.onhand_workbench_fit)!=this)return;
            if(!dialog.isShowing()||owner.isFinishing()||owner.isDestroyed()||dialog.getWindow()!=window){close();return;}
            float density=owner.getResources().getDisplayMetrics().density;
            if(!Float.isFinite(density)||density<=0)return;
            int usable=ownerRoot.getWidth();
            if(Build.VERSION.SDK_INT>=30){
                WindowMetrics metrics=owner.getWindowManager().getCurrentWindowMetrics();
                Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                usable=WorkbenchDialogPresentation.usableWidth(usable,metrics.getBounds().width(),insets.left,insets.right);
            }else{
                WindowInsets insets=ownerRoot.getRootWindowInsets();
                if(insets!=null&&usable>0)usable=WorkbenchDialogPresentation.usableWidth(usable,usable,insets.getStableInsetLeft(),insets.getStableInsetRight());
            }
            if(usable<=0)return;
            int usableDp=(int)(usable/density);
            float appScale=WorkbenchVisualPolicy.textScale(owner.getPreferences(Activity.MODE_PRIVATE).getString("font_size","normal"));
            float scale=WorkbenchDialogPresentation.combinedScale(owner.getResources().getConfiguration().fontScale,appScale);
            int width=compact?WorkbenchLayoutPolicy.compactDialogWidthDp(usableDp,scale):WorkbenchLayoutPolicy.dialogWidthDp(usableDp,scale);
            int target=Math.min(usable,Math.round(width*density));
            if(target>0&&window.getAttributes().width!=target)window.setLayout(target,window.getAttributes().height);
        }
    }
}
