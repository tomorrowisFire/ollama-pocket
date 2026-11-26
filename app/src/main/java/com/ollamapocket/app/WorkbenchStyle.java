package com.ollamapocket.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared, theme-local styling of opted-in workspace body widgets. Never changes listeners,
 * text, filters, view identity, persistence or platform confirmation controls. */
final class WorkbenchStyle {
    private WorkbenchStyle(){}
    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    private static WorkbenchVisualPolicy.Palette palette(Context c){
        int ink=0xff25272c;
        TypedArray attrs=c.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try{ColorStateList value=attrs.getColorStateList(0);if(value!=null)ink=value.getDefaultColor();}
        finally{attrs.recycle();}
        return WorkbenchVisualPolicy.palette(WorkbenchVisualPolicy.darkFromPrimaryText(ink));
    }
    private static float scale(Activity a){
        return WorkbenchVisualPolicy.textScale(a.getPreferences(Context.MODE_PRIVATE).getString("font_size","normal"));
    }
    static WorkbenchVisualPolicy.Role role(int label){
        if(label==R.string.project_delete||label==R.string.project_link_remove||label==R.string.auto_clear||
            label==R.string.auto_cancel_run||label==R.string.auto_history_clear||label==R.string.auto_editor_remove||
            label==R.string.auto_delete_item||label==R.string.cloud_key_delete)
            return WorkbenchVisualPolicy.Role.DESTRUCTIVE;
        if(label==R.string.project_save||label==R.string.project_report_save||label==R.string.knowledge_review||
            label==R.string.knowledge_generate||label==R.string.knowledge_search||label==R.string.conference_start||
            label==R.string.auto_start||label==R.string.auto_resume||label==R.string.auto_goal_generate||
            label==R.string.workspace_voice_start||label==R.string.workspace_read_start)
            return WorkbenchVisualPolicy.Role.PRIMARY;
        return WorkbenchVisualPolicy.Role.SECONDARY;
    }
    static LinearLayout.LayoutParams row(Context c){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(c,WorkbenchVisualPolicy.GAP_DP);return lp;
    }
    private static GradientDrawable shape(Context c,int fill,int stroke,int width){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(c,WorkbenchVisualPolicy.RADIUS_DP));
        if(width>0)d.setStroke(dp(c,width),stroke);return d;
    }
    private static ColorStateList colors(int enabled,int disabled){
        return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{disabled,enabled});
    }
    static void button(Activity a,Button b,WorkbenchVisualPolicy.Role role){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable state=new StateListDrawable();
        state.addState(new int[]{-android.R.attr.state_enabled},shape(a,p.disabled,p.border,1));
        state.addState(new int[]{android.R.attr.state_focused},shape(a,p.fill(role,true),p.focusInk(role),2));
        state.addState(new int[]{},shape(a,p.fill(role,true),role==WorkbenchVisualPolicy.Role.SECONDARY?p.border:p.fill(role,true),1));
        b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(role==WorkbenchVisualPolicy.Role.PRIMARY?0x25808080:0x33808080),
            state,shape(a,Color.WHITE,Color.TRANSPARENT,0)));
        b.setStateListAnimator(null);b.setElevation(0);
        b.setAllCaps(false);b.setSingleLine(false);b.setEllipsize(null);
        b.setMinHeight(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));b.setMinimumHeight(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));
        b.setMinWidth(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));
        b.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));
        b.setGravity(role==WorkbenchVisualPolicy.Role.PRIMARY?Gravity.CENTER:Gravity.START|Gravity.CENTER_VERTICAL);
        b.setTextSize(14*scale(a));
        b.setTypeface(Typeface.DEFAULT,role==WorkbenchVisualPolicy.Role.PRIMARY?Typeface.BOLD:Typeface.NORMAL);
        b.setTextColor(colors(p.ink(role,true),p.disabledText));
    }
    static void button(Activity a,Button b,int label){button(a,b,role(label));}
    static void field(Activity a,EditText e){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable state=new StateListDrawable();
        state.addState(new int[]{-android.R.attr.state_enabled},shape(a,p.disabled,p.border,1));
        state.addState(new int[]{android.R.attr.state_focused},shape(a,p.field,p.focus,2));
        state.addState(new int[]{},shape(a,p.field,p.border,1));
        e.setBackgroundTintList(null);e.setBackground(state);
        e.setMinHeight(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));e.setMinimumHeight(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));
        e.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));
        e.setTextSize(15*scale(a));e.setTextColor(colors(p.text,p.disabledText));e.setHintTextColor(colors(p.muted,p.disabledText));
    }
    static void label(Activity a,TextView t,int sp,boolean muted,boolean heading){
        WorkbenchVisualPolicy.Palette p=palette(a);
        t.setTextSize(sp*scale(a));t.setTextColor(muted?p.muted:p.text);
        t.setLineSpacing(dp(a,2),1f);
        if(heading){t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);if(android.os.Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);}
    }
    static void card(Activity a,LinearLayout layout,boolean selected){
        WorkbenchVisualPolicy.Palette p=palette(a);
        layout.setBackground(shape(a,p.surface,selected?p.focus:p.border,selected?2:1));
        layout.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));
    }
    /** Persistent caption linked to its existing, independently guarded control. */
    static TextView fieldLabel(Activity a,LinearLayout parent,int label,View input){
        TextView title=new TextView(a);title.setText(label);label(a,title,13,false,true);
        if(input.getId()==View.NO_ID)input.setId(View.generateViewId());
        title.setLabelFor(input.getId());title.setSaveEnabled(false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,4);
        parent.addView(title,lp);return title;
    }
}
