package com.ollamapocket.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;

/** Small, consistent native pictograms. Decorative only: the existing labels, callbacks and a11y names stay authoritative. */
final class OnHandGlyphs extends Drawable {
    enum Kind { MENU, PLUS, MORE, SEARCH, SEND, STOP, MIC, ATTACH, WRITE, PLAN, CODE, DOC,
        FLOW, CLOUD, PROJECT, KNOWLEDGE, EXPORT, CHIP, SETTINGS, DEVICE, LANGUAGE, THEME,
        FONT, SHIELD, CHECK, CLOSE, BACK, DOWN, CARET, CHEVRON, COPY, REFRESH, PLAY, TRASH, BOOK, IMAGE, NEW_CHAT }
    private final Kind kind;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private int color,alpha=255;
    private android.content.res.ColorStateList labelColors;
    private int stateAlpha=255;
    private OnHandGlyphs(Kind k,int ink,int size){kind=k;color=ink;this.size=size;pen.setStrokeWidth(1.75f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);pen.setStyle(Paint.Style.STROKE);}
    private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static Drawable drawable(Context c,Kind kind,int color,int sp){
        int size=dp(c,sp);OnHandGlyphs d=new OnHandGlyphs(kind,color,size);d.setBounds(0,0,size,size);return d;
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){pen.setColorFilter(value);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){
        boolean enabled=false;for(int s:state)if(s==android.R.attr.state_enabled)enabled=true;
        int nextAlpha=labelColors!=null||enabled||state.length==0?255:115;
        int nextColor=labelColors==null?color:labelColors.getColorForState(state,labelColors.getDefaultColor());
        if(nextAlpha==stateAlpha&&nextColor==color)return false;
        stateAlpha=nextAlpha;color=nextColor;invalidateSelf();return true;
    }
    private void line(Canvas c,float... xy){Path p=new Path();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);c.drawPath(p,pen);}
    private void box(Canvas c,float l,float t,float r,float b,float corner){c.drawRoundRect(l,t,r,b,corner,corner,pen);}
    private void dot(Canvas c,float x,float y){pen.setStyle(Paint.Style.FILL);c.drawCircle(x,y,1.3f,pen);pen.setStyle(Paint.Style.STROKE);}
    @Override public void draw(Canvas c){
        Rect b=getBounds();if(b.isEmpty())return;
        c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
        pen.setColor(color);pen.setAlpha(Math.round(alpha*stateAlpha/255f));
        switch(kind){
            case MENU:line(c,4,6,20,6);line(c,4,12,20,12);line(c,4,18,20,18);break;
            case PLUS:line(c,12,5,12,19);line(c,5,12,19,12);break;
            case NEW_CHAT:{
                Path bubble=new Path();
                bubble.moveTo(7,4);bubble.lineTo(17,4);bubble.quadTo(21,4,21,8);
                bubble.lineTo(21,14);bubble.quadTo(21,18,17,18);bubble.lineTo(10,18);
                bubble.lineTo(5,21);bubble.lineTo(5,17);bubble.quadTo(3,16,3,13);
                bubble.lineTo(3,8);bubble.quadTo(3,4,7,4);bubble.close();c.drawPath(bubble,pen);
                line(c,12,7.5f,12,14.5f);line(c,8.5f,11,15.5f,11);break;
            }
            case MORE:dot(c,5,12);dot(c,12,12);dot(c,19,12);break;
            case SEARCH:c.drawCircle(10,10,5.7f,pen);line(c,14,14,20,20);break;
            case SEND:line(c,4,11,20,4,15,20,11,13,4,11);line(c,11,13,20,4);break;
            case STOP:box(c,6,6,18,18,2);break;
            case MIC:box(c,9,3,15,15,3);c.drawArc(new RectF(6,6,18,18),0,180,false,pen);line(c,12,18,12,21);line(c,9,21,15,21);break;
            case ATTACH:{Path a=new Path();a.moveTo(8,13);a.lineTo(15,6);a.cubicTo(19,2,23,7,19,11);a.lineTo(11,19);a.cubicTo(6,24,0,18,5,13);a.lineTo(13,5);c.drawPath(a,pen);break;}
            case WRITE:line(c,4,19,5,14,16,3,21,8,10,19,4,19);line(c,13,6,18,11);break;
            case PLAN:box(c,4,5,20,21,2);line(c,8,3,8,7);line(c,16,3,16,7);line(c,4,10,20,10);line(c,8,14,11,14);line(c,8,17,15,17);break;
            case CODE:line(c,8,7,3,12,8,17);line(c,16,7,21,12,16,17);line(c,14,4,10,20);break;
            case DOC:line(c,6,3,15,3,19,7,19,21,5,21,5,3,6,3);line(c,14,3,14,8,19,8);line(c,8,12,16,12);line(c,8,16,16,16);break;
            case FLOW:box(c,9,2,15,8,1.2f);box(c,2,16,8,22,1.2f);box(c,16,16,22,22,1.2f);line(c,12,8,12,12,5,12,5,16);line(c,12,12,19,12,19,16);break;
            case CLOUD:{Path a=new Path();a.moveTo(6,18);a.cubicTo(0,18,0,10,6,10);a.cubicTo(6,3,17,3,18,10);a.cubicTo(24,10,24,18,18,18);a.close();c.drawPath(a,pen);break;}
            case PROJECT:line(c,3,7,3,20,21,20,21,8,12,8,10,5,3,5,3,7);break;
            case KNOWLEDGE:case BOOK:line(c,12,5,5,3,3,4,3,19,5,18,12,20,19,18,21,19,21,4,19,3,12,5,12,20);break;
            case EXPORT:line(c,12,3,12,15);line(c,7,10,12,15,17,10);line(c,4,15,4,21,20,21,20,15);break;
            case CHIP:box(c,6,6,18,18,2);box(c,9,9,15,15,1);for(int k=9;k<=15;k+=3){line(c,k,2,k,6);line(c,k,18,k,22);line(c,2,k,6,k);line(c,18,k,22,k);}break;
            case SETTINGS:c.drawCircle(12,12,6,pen);c.drawCircle(12,12,2.3f,pen);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,3,12,6);c.restore();}break;
            case DEVICE:box(c,3,4,21,16,2);line(c,12,16,12,21);line(c,7,21,17,21);break;
            case LANGUAGE:c.drawCircle(12,12,9,pen);c.drawOval(8,3,16,21,pen);line(c,3,12,21,12);break;
            case THEME:{Path a=new Path();a.moveTo(13,3);a.cubicTo(5,2,1,10,5,17);a.cubicTo(10,24,20,20,21,12);a.cubicTo(14,16,8,9,13,3);c.drawPath(a,pen);break;}
            case FONT:line(c,4,20,10,4,16,20);line(c,6,15,14,15);line(c,17,10,21,10);line(c,19,10,19,20);break;
            case SHIELD:{Path a=new Path();a.moveTo(12,3);a.lineTo(21,7);a.lineTo(20,14);a.quadTo(18,19,12,22);a.quadTo(6,19,4,14);a.lineTo(3,7);a.close();c.drawPath(a,pen);line(c,8,12,11,15,16,9);break;}
            case CHECK:line(c,5,12,10,17,20,7);break;
            case CLOSE:line(c,6,6,18,18);line(c,18,6,6,18);break;
            case BACK:line(c,15,5,8,12,15,19);break;
            case DOWN:line(c,12,4,12,20);line(c,6,14,12,20,18,14);break;
            case CARET:line(c,5,9,12,16,19,9);break;
            case CHEVRON:line(c,9,5,16,12,9,19);break;
            case COPY:box(c,8,8,20,21,2);line(c,15,5,15,3,3,3,3,16,5,16);break;
            case REFRESH:c.drawArc(new RectF(4,4,20,20),40,300,false,pen);line(c,20,4,20,10,14,10);break;
            case PLAY:line(c,8,4,20,12,8,20,8,4);break;
            case TRASH:line(c,4,6,20,6);line(c,6,6,7,21,17,21,18,6);line(c,9,6,9,3,15,3,15,6);line(c,10,10,10,17);line(c,14,10,14,17);break;
            case IMAGE:box(c,3,4,21,20,2);c.drawCircle(8,9,1.5f,pen);line(c,4,18,11,11,15,15,18,12,21,15);break;
        }
        c.restore();
    }
    private static final class Spec{
        final Kind kind;final boolean only,accent,above;
        Spec(Kind kind,boolean only,boolean accent){this(kind,only,accent,false);}
        Spec(Kind kind,boolean only,boolean accent,boolean above){this.kind=kind;this.only=only;this.accent=accent;this.above=above;}
    }
    static void label(TextView view,Kind kind,boolean iconOnly){
        view.setTag(R.id.onhand_glyph_spec,new Spec(kind,iconOnly,false));apply(view);
    }
    static void stackedLabel(TextView view,Kind kind){
        view.setTag(R.id.onhand_glyph_spec,new Spec(kind,false,false,true));apply(view);
    }
    static void accentLabel(TextView view,Kind kind){
        view.setTag(R.id.onhand_glyph_spec,new Spec(kind,false,true));apply(view);
    }
    private static void apply(TextView v){
        Object raw=v.getTag(R.id.onhand_glyph_spec);if(!(raw instanceof Spec))return;
        Spec s=(Spec)raw;Context c=v.getContext();WorkbenchVisualPolicy.Palette p=WorkbenchStyle.palette(c);
        OnHandGlyphs d=(OnHandGlyphs)drawable(c,s.kind,s.accent?p.focus:v.getCurrentTextColor(),s.only||s.above?22:20);
        if(!s.accent)d.labelColors=v.getTextColors();
        d.setState(v.getDrawableState());
        if(s.above){
            v.setCompoundDrawablesRelative(null,d,null,null);v.setCompoundDrawablePadding(dp(c,2));
            v.setGravity(Gravity.CENTER);v.setPaddingRelative(dp(c,8),dp(c,4),dp(c,8),dp(c,4));
            v.setMinWidth(dp(c,48));v.setMinimumWidth(dp(c,48));v.setMinHeight(dp(c,48));v.setMinimumHeight(dp(c,48));
            return;
        }
        v.setCompoundDrawablesRelative(d,null,null,null);
        v.setCompoundDrawablePadding(s.only?0:dp(c,10));
        if(s.only){
            v.setText("");v.setGravity(Gravity.CENTER);
            int target=dp(c,48),glyph=d.getIntrinsicWidth();
            int leading=WorkbenchLayoutPolicy.iconInsetPx(target,glyph,false),trailing=WorkbenchLayoutPolicy.iconInsetPx(target,glyph,true);
            v.setPaddingRelative(leading,leading,trailing,trailing);
            v.setMinWidth(target);v.setMinimumWidth(target);
        }
    }
    /** Paint-only visual badge; never exposes a duplicate clickable/accessibility node. */
    static TextView tile(Context c,Kind kind){
        TextView v=new TextView(c);WorkbenchVisualPolicy.Palette p=WorkbenchStyle.palette(c);
        GradientDrawable shape=new GradientDrawable();shape.setColor(p.field);shape.setCornerRadius(dp(c,13));v.setBackground(shape);
        v.setTextColor(p.focus);v.setTag(R.id.onhand_glyph_spec,new Spec(kind,true,true));apply(v);
        v.setPadding(dp(c,9),dp(c,9),dp(c,9),dp(c,9));v.setMinWidth(dp(c,40));v.setMinimumWidth(dp(c,40));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);v.setFocusable(false);v.setClickable(false);
        return v;
    }
    private static GradientDrawable spinnerSurface(Context c,int fill,int stroke,int strokeDp){
        GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setCornerRadius(dp(c,14));shape.setStroke(dp(c,strokeDp),stroke);return shape;
    }
    static void spinner(Spinner view){
        Context c=view.getContext();WorkbenchVisualPolicy.Palette p=WorkbenchStyle.palette(c);
        StateListDrawable surfaces=new StateListDrawable();
        surfaces.addState(new int[]{-android.R.attr.state_enabled},spinnerSurface(c,p.disabled,p.border,1));
        surfaces.addState(new int[]{android.R.attr.state_focused},spinnerSurface(c,p.field,p.focus,2));
        surfaces.addState(new int[]{},spinnerSurface(c,p.field,p.border,1));
        LayerDrawable layers=new LayerDrawable(new Drawable[]{surfaces,drawable(c,Kind.DOWN,p.muted,16)});
        layers.setLayerWidth(1,dp(c,16));layers.setLayerHeight(1,dp(c,16));
        layers.setLayerGravity(1,Gravity.END|Gravity.CENTER_VERTICAL);layers.setLayerInsetRelative(1,0,0,dp(c,10),0);
        view.setBackground(layers);view.setPaddingRelative(dp(c,12),dp(c,6),dp(c,32),dp(c,6));view.setMinimumHeight(dp(c,48));
        view.setTag(R.id.onhand_spinner_spec,Boolean.TRUE);
    }
    /** Compact selected label, full-size touch target and normal native drop-down. */
    static void compactFilterSpinner(Spinner view){compactFilterSpinner(view,false);}
    static void filterSelection(Spinner view,boolean active){
        String wanted=active?"compact_filter_active":"compact_filter";
        if(!wanted.equals(view.getTag(R.id.onhand_spinner_spec)))compactFilterSpinner(view,active);
    }
    private static void compactFilterSpinner(Spinner view,boolean active){
        Context c=view.getContext();WorkbenchVisualPolicy.Palette p=WorkbenchStyle.palette(c);
        StateListDrawable surfaces=new StateListDrawable();
        surfaces.addState(new int[]{-android.R.attr.state_enabled},spinnerSurface(c,p.disabled,p.border,1));
        int fill=active?p.choiceFill(true):p.field,border=active?p.choiceBorder(true):p.border;
        surfaces.addState(new int[]{android.R.attr.state_focused},spinnerSurface(c,fill,p.focus,2));
        surfaces.addState(new int[]{},spinnerSurface(c,fill,border,1));
        LayerDrawable layers=new LayerDrawable(new Drawable[]{surfaces,drawable(c,Kind.DOWN,active?p.choiceInk(true):p.muted,12)});
        layers.setLayerWidth(1,dp(c,12));layers.setLayerHeight(1,dp(c,12));
        layers.setLayerGravity(1,Gravity.END|Gravity.CENTER_VERTICAL);layers.setLayerInsetRelative(1,0,0,dp(c,2),0);
        view.setBackground(layers);view.setPaddingRelative(dp(c,4),dp(c,6),dp(c,16),dp(c,6));
        view.setMinimumHeight(dp(c,48));view.setMinimumWidth(dp(c,48));
        view.setDropDownWidth(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setTag(R.id.onhand_spinner_spec,active?"compact_filter_active":"compact_filter");
    }
    static void refresh(View v){
        if(v instanceof TextView&&v.getTag(R.id.onhand_glyph_spec) instanceof Spec){
            Spec s=(Spec)v.getTag(R.id.onhand_glyph_spec);
            if(s.only&&s.accent){
                TextView t=(TextView)v;WorkbenchVisualPolicy.Palette p=WorkbenchStyle.palette(t.getContext());
                t.setTextColor(p.focus);t.setCompoundDrawablesRelative(drawable(t.getContext(),s.kind,p.focus,22),null,null,null);
            }else apply((TextView)v);
        }else if(v instanceof Spinner){
            Object spec=v.getTag(R.id.onhand_spinner_spec);
            if("compact_filter".equals(spec)||"compact_filter_active".equals(spec))compactFilterSpinner((Spinner)v,"compact_filter_active".equals(spec));
            else if(Boolean.TRUE.equals(spec))spinner((Spinner)v);
        }
    }
    static Kind forResource(int id){
        if(id==R.string.send)return Kind.SEND;
        if(id==R.string.stop)return Kind.STOP;
        if(id==R.string.close||id==R.string.cancel)return Kind.CLOSE;
        if(id==R.string.ok||id==R.string.project_save||id==R.string.project_report_save)return Kind.CHECK;
        if(id==R.string.copy||id==R.string.chat_copy_code||id==R.string.chat_copy_table||
            id==R.string.chat_copy_math||id==R.string.chat_copy_spreadsheet||
            id==R.string.integration_copy_csv||id==R.string.integration_copy_html)return Kind.COPY;
        if(id==R.string.search||id==R.string.knowledge_search)return Kind.SEARCH;
        if(id==R.string.new_chat||id==R.string.auto_editor_add)return Kind.PLUS;
        if(id==R.string.project_workspace)return Kind.PROJECT;
        if(id==R.string.knowledge_workspace||id==R.string.knowledge_review)return Kind.KNOWLEDGE;
        if(id==R.string.auto_title||id==R.string.integration_form_editor)return Kind.FLOW;
        if(id==R.string.cloud||id==R.string.api_manager_title||id==R.string.cloud_fetch_models)return Kind.CLOUD;
        if(id==R.string.chat_export)return Kind.EXPORT;
        if(id==R.string.compute_select||id==R.string.compute||id==R.string.integration_form_bind_model)return Kind.CHIP;
        if(id==R.string.project_delete||id==R.string.auto_editor_remove||id==R.string.cloud_key_delete)return Kind.TRASH;
        if(id==R.string.conference_start||id==R.string.auto_start||id==R.string.auto_resume||id==R.string.knowledge_generate)return Kind.PLAY;
        if(id==R.string.voice||id==R.string.voice_models_title)return Kind.MIC;
        if(id==R.string.voice_model_install)return Kind.DOWN;
        if(id==R.string.voice_model_remove)return Kind.TRASH;
        if(id==R.string.voice_model_license)return Kind.BOOK;
        if(id==R.string.voice_model_cancel_download)return Kind.STOP;
        if(id==R.string.refresh||id==R.string.diag_refresh)return Kind.REFRESH;
        if(id==R.string.language)return Kind.LANGUAGE;
        if(id==R.string.settings)return Kind.SETTINGS;
        if(id==R.string.devices||id==R.string.guest||id==R.string.workspace_mode)return Kind.DEVICE;
        if(id==R.string.models||id==R.string.import_gguf)return Kind.CHIP;
        if(id==R.string.privacy_settings)return Kind.SHIELD;
        if(id==R.string.license)return Kind.BOOK;
        if(id==R.string.batch_goal_templates)return Kind.DOC;
        if(id==R.string.project_back)return Kind.BACK;
        return null;
    }
}
