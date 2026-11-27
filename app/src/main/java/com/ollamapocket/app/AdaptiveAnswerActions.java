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
    AdaptiveAnswerActions(Context context){
        super(context);
        gap=Math.round(ActionLayoutPolicy.GAP*getResources().getDisplayMetrics().density);
        setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        setOrientation(HORIZONTAL);
    }
    /** The foreground mask does not replace the existing background or theme palette. */
    static void feedback(View view,int radiusDp){
        GradientDrawable mask=new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusDp*view.getResources().getDisplayMetrics().density);
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(0x33808080),null,mask));
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int count=0;
        for(int i=0;i<getChildCount();i++)if(getChildAt(i).getVisibility()!=GONE)count++;
        int[] widths=new int[count];
        int free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        int index=0;
        float density=getResources().getDisplayMetrics().density;
        int target=Math.round(ActionLayoutPolicy.MIN_TARGET*density);
        java.util.List<ActionLayoutPolicy.Action> measured=new java.util.ArrayList<>();
        boolean policyEligible=count<=ActionLayoutPolicy.MAX_ACTIONS&&density>0;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            if(child instanceof android.widget.Button){
                if(child.getMinimumWidth()<target)child.setMinimumWidth(target);
                if(child.getMinimumHeight()<target)child.setMinimumHeight(target);
            }
            child.measure(free,free);
            widths[index++]=child.getMeasuredWidth();
            if(policyEligible){
                int w=(int)Math.ceil(child.getMeasuredWidth()/density),h=(int)Math.ceil(child.getMeasuredHeight()/density);
                if(w>2048||h>1024)policyEligible=false;
                else measured.add(new ActionLayoutPolicy.Action(w,h));
            }
        }
        int available=Math.max(0,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());
        boolean stacked=MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&
            ChatWorkspacePolicy.stackAnswerActions(available,gap,widths);
        if(policyEligible&&MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED){
            int widthDp=(int)Math.floor(available/density);
            float scale=getResources().getConfiguration().fontScale;
            if(widthDp<=8192&&Float.isFinite(scale)&&scale>=.5f&&scale<=4f){
                ActionLayoutPolicy.Layout fit=ActionLayoutPolicy.layout(widthDp,8192,scale,measured);
                stacked|=fit.rows.size()>1||(!fit.rows.isEmpty()&&fit.rows.get(0).horizontalScroll);
            }
        }
        int orientation=stacked?VERTICAL:HORIZONTAL;
        if(getOrientation()!=orientation)setOrientation(orientation);
        index=0;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);
            LayoutParams lp=(LayoutParams)child.getLayoutParams();
            boolean visible=child.getVisibility()!=GONE;
            boolean next=visible&&++index<count;
            lp.width=stacked?LayoutParams.MATCH_PARENT:LayoutParams.WRAP_CONTENT;
            lp.height=LayoutParams.WRAP_CONTENT;lp.weight=0;
            lp.setMargins(0,0,0,stacked&&next?gap:0);
            lp.setMarginStart(0);lp.setMarginEnd(!stacked&&next?gap:0);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
