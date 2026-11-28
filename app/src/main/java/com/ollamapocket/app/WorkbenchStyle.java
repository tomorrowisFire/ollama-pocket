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
    static WorkbenchVisualPolicy.Palette palette(Context c){
        if(c instanceof MainActivity)return WorkbenchVisualPolicy.palette(((MainActivity)c).brandDark());
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
        if(label==R.string.send||label==R.string.batch_start||label==R.string.project_save||label==R.string.project_report_save||label==R.string.knowledge_review||
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
    /** Shared choice rows; native checked state and item callbacks remain authoritative. */
    static android.widget.CheckedTextView choiceRow(Context c,String label,boolean selected){
        WorkbenchVisualPolicy.Palette p=palette(c);
        android.widget.CheckedTextView row=new android.widget.CheckedTextView(c);
        StateListDrawable surface=new StateListDrawable();
        surface.addState(new int[]{android.R.attr.state_checked,android.R.attr.state_focused},shape(c,p.choiceFill(true),p.focus,2));
        surface.addState(new int[]{android.R.attr.state_checked},shape(c,p.choiceFill(true),p.choiceBorder(true),1));
        surface.addState(new int[]{android.R.attr.state_focused},shape(c,p.choiceFill(false),p.focus,2));
        surface.addState(new int[]{},shape(c,p.choiceFill(false),p.choiceBorder(false),0));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(p.dialogRipple()),surface,shape(c,Color.WHITE,Color.TRANSPARENT,0)));
        StateListDrawable mark=new StateListDrawable();
        mark.addState(new int[]{android.R.attr.state_checked},OnHandGlyphs.drawable(c,OnHandGlyphs.Kind.CHECK,p.choiceMark(true),20));
        GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.TRANSPARENT);circle.setStroke(dp(c,1),p.choiceMark(false));circle.setSize(dp(c,20),dp(c,20));
        mark.addState(new int[]{},circle);
        row.setCheckMarkTintList(null);row.setCheckMarkDrawable(mark);
        row.setText(label);row.setSingleLine(false);row.setEllipsize(null);
        row.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
            new int[]{p.choiceInk(true),p.choiceInk(false)}));
        row.setTextSize(14*(c instanceof Activity?scale((Activity)c):1f));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(c,14),dp(c,11),dp(c,14),dp(c,11));
        row.setMinHeight(dp(c,52));row.setMinimumHeight(dp(c,52));row.setChecked(selected);
        return row;
    }
    static android.widget.ArrayAdapter<String> choiceRows(Context c,String[] labels,int checked){
        return new android.widget.ArrayAdapter<String>(c,android.R.layout.simple_list_item_single_choice,labels){
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
                return choiceRow(c,getItem(position),position==checked);
            }
        };
    }
    static android.widget.ArrayAdapter<String> spinnerChoices(android.widget.Spinner spinner,String[] labels){
        Context c=spinner.getContext();
        return new android.widget.ArrayAdapter<String>(c,android.R.layout.simple_spinner_item,labels){
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
                TextView row=new TextView(c);row.setText(getItem(position));row.setTextColor(palette(c).text);
                row.setTextSize(14*(c instanceof Activity?scale((Activity)c):1f));
                row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(c,40));return row;
            }
            @Override public View getDropDownView(int position,View recycled,android.view.ViewGroup parent){
                return choiceRow(c,getItem(position),position==spinner.getSelectedItemPosition());
            }
        };
    }
    /** List/grid mode is a choice, not a primary action. Keep a quiet fill and explicit check label. */
    static void choiceButton(Activity a,Button b,boolean selected){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable state=new StateListDrawable();
        state.addState(new int[]{-android.R.attr.state_enabled},shape(a,p.disabled,p.border,0));
        state.addState(new int[]{android.R.attr.state_focused},shape(a,p.choiceFill(selected),p.focus,2));
        state.addState(new int[]{},shape(a,p.choiceFill(selected),p.choiceBorder(selected),1));
        int start=b.getPaddingStart(),top=b.getPaddingTop(),end=b.getPaddingEnd(),bottom=b.getPaddingBottom();
        b.setTag(R.id.onhand_primary_paint,null);b.setBackgroundTintList(null);b.setForeground(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(p.dialogRipple()),state,shape(a,Color.WHITE,Color.TRANSPARENT,0)));
        b.setPaddingRelative(start,top,end,bottom);
        b.setTextColor(colors(p.choiceInk(selected),p.disabledText));b.setSelected(selected);
    }
    static void button(Activity a,Button b,WorkbenchVisualPolicy.Role role){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable state=new StateListDrawable();
        state.addState(new int[]{-android.R.attr.state_enabled},shape(a,p.disabled,p.border,1));
        state.addState(new int[]{android.R.attr.state_focused},shape(a,p.fill(role,true),p.focusInk(role),2));
        int outline=role==WorkbenchVisualPolicy.Role.PRIMARY?p.primaryOutline():
            role==WorkbenchVisualPolicy.Role.SECONDARY?p.border:p.fill(role,true);
        state.addState(new int[]{},shape(a,p.fill(role,true),outline,1));
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
    static void button(Activity a,Button b,int label){
        button(a,b,role(label));
        OnHandGlyphs.Kind glyph=OnHandGlyphs.forResource(label);
        if(glyph!=null)OnHandGlyphs.label(b,glyph,false);
    }
    /** Single-surface chat composer; paint only, never replace input or action callbacks. */
    private static GradientDrawable composerShape(Context c,int fill,int stroke,int width,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));
        if(width>0)d.setStroke(dp(c,width),stroke);return d;
    }
    /** Preserve the role across automatic theme changes without overwriting custom backgrounds. */
    private static final class PrimaryPaint {
        final int radius;
        final android.graphics.drawable.Drawable background;
        PrimaryPaint(int radius,android.graphics.drawable.Drawable background){this.radius=radius;this.background=background;}
    }
    static void primaryButtonPaint(Activity a,Button b,int radius){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},composerShape(a,p.disabled,p.border,1,radius));
        states.addState(new int[]{android.R.attr.state_focused},composerShape(a,p.primaryFill,p.primaryText,2,radius));
        states.addState(new int[]{},composerShape(a,p.primaryFill,p.primaryOutline(),1,radius));
        int start=b.getPaddingStart(),top=b.getPaddingTop(),end=b.getPaddingEnd(),bottom=b.getPaddingBottom();
        b.setForeground(null);b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(p.dialogRipple()),states,
            composerShape(a,Color.WHITE,Color.TRANSPARENT,0,radius)));
        b.setPaddingRelative(start,top,end,bottom);
        b.setTextColor(colors(p.primaryText,p.disabledText));
        b.setTag(R.id.onhand_primary_paint,new PrimaryPaint(radius,b.getBackground()));
        OnHandGlyphs.refresh(b);
    }
    static void refreshPrimaryButtonPaint(Activity a,View v){
        if(!(v instanceof Button))return;
        Object raw=v.getTag(R.id.onhand_primary_paint);
        if(!(raw instanceof PrimaryPaint))return;
        PrimaryPaint previous=(PrimaryPaint)raw;
        if(v.getBackground()==previous.background)primaryButtonPaint(a,(Button)v,previous.radius);
        else v.setTag(R.id.onhand_primary_paint,null);
    }
    static void composerSurface(Activity a,LinearLayout layout,boolean focused){
        WorkbenchVisualPolicy.Palette p=palette(a);
        layout.setBackground(composerShape(a,p.surface,focused?p.focus:p.border,focused?2:1,26));
        layout.setPadding(dp(a,8),dp(a,6),dp(a,8),dp(a,6));
    }
    static void composerInput(Activity a,EditText input){
        WorkbenchVisualPolicy.Palette p=palette(a);
        input.setBackgroundTintList(null);input.setBackgroundColor(Color.TRANSPARENT);
        input.setPaddingRelative(dp(a,12),dp(a,9),dp(a,12),dp(a,9));
        input.setTextColor(colors(p.text,p.disabledText));
        input.setHintTextColor(colors(p.muted,p.disabledText));
    }
    static void composerAction(Activity a,Button button,boolean primary){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},composerShape(a,p.composerActionFill(primary,false),p.border,0,24));
        states.addState(new int[]{android.R.attr.state_focused},composerShape(a,p.composerActionFill(primary,true),p.composerActionFocus(primary),2,24));
        states.addState(new int[]{},composerShape(a,p.composerActionFill(primary,true),primary?p.primaryOutline():p.border,primary?1:0,24));
        button.setForeground(null);button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080),states,composerShape(a,Color.WHITE,Color.TRANSPARENT,0,24)));
        button.setTextColor(colors(p.composerActionInk(primary,true),p.composerActionInk(primary,false)));
        button.setMinWidth(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));button.setMinimumWidth(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));
        button.setMinHeight(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));button.setMinimumHeight(dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP));
        OnHandGlyphs.refresh(button);
    }
    /** Quiet outlined quick action. Primary color is reserved for explicit primary actions. */
    static void quickAction(Activity a,Button b){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},composerShape(a,p.disabled,p.border,1,24));
        states.addState(new int[]{android.R.attr.state_focused},composerShape(a,p.surface,p.focus,2,24));
        states.addState(new int[]{},composerShape(a,p.surface,p.border,1,24));
        b.setForeground(null);b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(p.dialogRipple()),states,composerShape(a,Color.WHITE,Color.TRANSPARENT,0,24)));
        b.setTextColor(colors(p.text,p.disabledText));
        b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setPaddingRelative(dp(a,12),dp(a,8),dp(a,12),dp(a,8));
        b.setSingleLine(false);b.setEllipsize(null);
        b.setMinimumHeight(Math.max(b.getMinimumHeight(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
        b.setMinimumWidth(Math.max(b.getMinimumWidth(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
        OnHandGlyphs.refresh(b);
    }
    /** Flat navigation/disclosure row: no state, text, or callback ownership. */
    static void navigationRow(Activity a,Button b){
        WorkbenchVisualPolicy.Palette p=palette(a);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},shape(a,p.canvas,p.border,0));
        states.addState(new int[]{android.R.attr.state_focused},shape(a,p.canvas,p.focus,2));
        states.addState(new int[]{},shape(a,p.canvas,p.border,0));
        b.setForeground(null);b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080),states,shape(a,Color.WHITE,Color.TRANSPARENT,0)));
        b.setTextColor(colors(p.text,p.disabledText));
        b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        b.setSingleLine(false);b.setEllipsize(null);
        b.setMinHeight(Math.max(b.getMinHeight(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
        b.setMinimumHeight(Math.max(b.getMinimumHeight(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
        b.setMinWidth(Math.max(b.getMinWidth(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
        b.setMinimumWidth(Math.max(b.getMinimumWidth(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
        b.setPaddingRelative(dp(a,12),dp(a,8),dp(a,12),dp(a,8));
        OnHandGlyphs.refresh(b);
    }
    /** Separate a setting's name from its current value without changing its click target. */
    static void settingValue(Button button,String label,String value){
        String text=SelectionPresentation.settingText(label,value);
        android.text.SpannableString content=new android.text.SpannableString(text);
        content.setSpan(new android.text.style.RelativeSizeSpan(0.86f),0,label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        content.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),label.length()+1,text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setSingleLine(false);button.setMaxLines(Integer.MAX_VALUE);button.setEllipsize(null);
        button.setText(content);button.setContentDescription(SelectionPresentation.settingDescription(label,value));
        button.setMinimumHeight(Math.max(button.getMinimumHeight(),dp(button.getContext(),60)));
    }
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
        layout.setPadding(dp(a,16),dp(a,14),dp(a,16),dp(a,14));
    }
    /** Restyle only the platform action buttons; do not replace listeners, security flags or dismissal. */
    static void dialogActions(Activity a,android.app.Dialog dialog){
        if(!(dialog instanceof android.app.AlertDialog))return;
        android.app.AlertDialog alert=(android.app.AlertDialog)dialog;
        WorkbenchVisualPolicy.Palette p=palette(a);
        for(int id:new int[]{android.content.DialogInterface.BUTTON_POSITIVE,android.content.DialogInterface.BUTTON_NEGATIVE,android.content.DialogInterface.BUTTON_NEUTRAL}){
            Button b=alert.getButton(id);if(b==null)continue;
            boolean positive=id==android.content.DialogInterface.BUTTON_POSITIVE;
            // Keep the platform's measured button padding, text, listener and dismissal.
            // A positive slot can also be destructive: do not promote it to a filled CTA.
            StateListDrawable state=new StateListDrawable();
            state.addState(new int[]{-android.R.attr.state_enabled},shape(a,p.dialogFill(false),p.border,0));
            state.addState(new int[]{android.R.attr.state_focused},shape(a,p.dialogFill(true),p.focus,2));
            state.addState(new int[]{},shape(a,p.dialogFill(true),p.border,0));
            int start=b.getPaddingStart(),top=b.getPaddingTop(),end=b.getPaddingEnd(),bottom=b.getPaddingBottom();
            b.setBackgroundTintList(null);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(p.dialogRipple()),state,shape(a,Color.WHITE,Color.TRANSPARENT,0)));
            b.setPaddingRelative(start,top,end,bottom);
            b.setAllCaps(false);
            b.setMinHeight(Math.max(b.getMinHeight(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
            b.setMinimumHeight(Math.max(b.getMinimumHeight(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
            b.setMinWidth(Math.max(b.getMinWidth(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
            b.setMinimumWidth(Math.max(b.getMinimumWidth(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)));
            b.setTextColor(colors(p.dialogInk(positive,true),p.dialogInk(positive,false)));
            b.setTypeface(Typeface.DEFAULT,positive?Typeface.BOLD:Typeface.NORMAL);
        }
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
