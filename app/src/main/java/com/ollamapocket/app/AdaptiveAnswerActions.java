package com.ollamapocket.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/** Shared local/cloud reply toolbar. Measure actual translated, font-scaled labels before laying out. */
final class AdaptiveAnswerActions extends LinearLayout {
    private final int gap;
    private final java.util.List<View> visible=new java.util.ArrayList<>();
    private final java.util.List<Integer> rowHeights=new java.util.ArrayList<>();
    private java.util.List<ActionFlowPolicy.Row> rows=java.util.Collections.emptyList();
    AdaptiveAnswerActions(Context context){
        super(context);
        gap=Math.round(ActionLayoutPolicy.GAP*getResources().getDisplayMetrics().density);
        setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        setOrientation(HORIZONTAL);setBaselineAligned(false);
    }
    /** The foreground mask does not replace the existing background or theme palette. */
    static void feedback(View view,int radiusDp){
        GradientDrawable mask=new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusDp*view.getResources().getDisplayMetrics().density);
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(0x33808080),null,mask));
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        visible.clear();rowHeights.clear();
        int free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        float density=getResources().getDisplayMetrics().density;
        if(!Float.isFinite(density)||density<=0)density=1f;
        int target=Math.round(ActionLayoutPolicy.MIN_TARGET*density);
        long natural=0;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            if(child instanceof android.widget.Button){
                if(child.getMinimumWidth()<target)child.setMinimumWidth(target);
                if(child.getMinimumHeight()<target)child.setMinimumHeight(target);
            }
            child.measure(free,free);
            natural+=(visible.isEmpty()?0L:gap)+child.getMeasuredWidth();
            visible.add(child);
        }
        int horizontalPadding=getPaddingLeft()+getPaddingRight();
        int mode=MeasureSpec.getMode(widthSpec);
        int desired=(int)Math.min(0x3fffffffL,Math.max(getSuggestedMinimumWidth(),natural+horizontalPadding));
        int width=resolveSize(desired,widthSpec);
        int available=Math.max(0,width-horizontalPadding);
        int[] widths=new int[visible.size()];
        for(int i=0;i<widths.length;i++)widths[i]=visible.get(i).getMeasuredWidth();
        float appScale=getContext() instanceof android.app.Activity?
            WorkbenchVisualPolicy.textScale(((android.app.Activity)getContext()).getPreferences(android.app.Activity.MODE_PRIVATE).getString("font_size","normal")):1f;
        float scale=WorkbenchDialogPresentation.combinedScale(getResources().getConfiguration().fontScale,appScale);
        boolean single=mode!=MeasureSpec.UNSPECIFIED&&WorkbenchLayoutPolicy.largeText(scale)&&available/density<600;
        rows=ActionFlowPolicy.rows(available,gap,single,widths);
        long total=getPaddingTop()+getPaddingBottom();
        for(ActionFlowPolicy.Row row:rows){
            int height=0;
            for(int i=row.first;i<row.end;i++){
                View child=visible.get(i);
                int fitted=ActionFlowPolicy.fittedWidth(available,widths[i]);
                if(single&&child instanceof android.widget.Button)fitted=available;
                if(fitted!=child.getMeasuredWidth())
                    child.measure(MeasureSpec.makeMeasureSpec(fitted,MeasureSpec.EXACTLY),free);
                height=Math.max(height,child.getMeasuredHeight());
            }
            total+=(rowHeights.isEmpty()?0L:gap)+height;
            rowHeights.add(height);
        }
        setMeasuredDimension(width,resolveSize((int)Math.min(0x3fffffffL,Math.max(total,getSuggestedMinimumHeight())),heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        int y=getPaddingTop();
        for(int r=0;r<rows.size();r++){
            ActionFlowPolicy.Row row=rows.get(r);
            int height=rowHeights.get(r),x=rtl?right-left-getPaddingRight():getPaddingLeft();
            for(int i=row.first;i<row.end;i++){
                View child=visible.get(i);int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();
                int start=rtl?x-w:x,childTop=y+(height-h)/2;
                child.layout(start,childTop,start+w,childTop+h);
                x+=rtl?-(w+gap):w+gap;
            }
            y+=height+gap;
        }
    }
}
