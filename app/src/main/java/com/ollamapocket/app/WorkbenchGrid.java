package com.ollamapocket.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** A measured one/two-column launcher. Existing child instances stay in place across resize. */
final class WorkbenchGrid extends ViewGroup {
    private final int gap;
    private final float density,appScale;
    private int columns=1;
    private final List<View> visible=new ArrayList<>();
    private final List<Integer> heights=new ArrayList<>();
    WorkbenchGrid(Context context,float appScale){
        super(context);
        this.appScale=Float.isFinite(appScale)&&appScale>0?appScale:1f;
        density=getResources().getDisplayMetrics().density;
        gap=Math.round(WorkbenchLayoutPolicy.GRID_GAP_DP*density);
    }
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?Math.round(320*density):MeasureSpec.getSize(widthSpec);
        int inner=Math.max(0,width-getPaddingLeft()-getPaddingRight());
        columns=WorkbenchLayoutPolicy.columns((int)(inner/density),appScale*Math.max(1f,getResources().getConfiguration().fontScale));
        visible.clear();heights.clear();
        for(int i=0;i<getChildCount();i++)if(getChildAt(i).getVisibility()!=GONE)visible.add(getChildAt(i));
        int total=getPaddingTop()+getPaddingBottom();
        int rows=WorkbenchLayoutPolicy.rows(visible.size(),columns);
        for(int row=0;row<rows;row++){
            int start=row*columns,end=Math.min(start+columns,visible.size()),height=0;
            for(int i=start;i<end;i++){
                View child=visible.get(i);
                int cell=WorkbenchLayoutPolicy.cellWidth(inner,gap,columns,i-start);
                child.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                height=Math.max(height,child.getMeasuredHeight());
            }
            for(int i=start;i<end;i++){
                View child=visible.get(i);
                int cell=WorkbenchLayoutPolicy.cellWidth(inner,gap,columns,i-start);
                if(child.getMeasuredHeight()!=height)child.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
            }
            heights.add(height);total+=height+(row==0?0:gap);
        }
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(total,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        int y=getPaddingTop();
        for(int row=0;row<heights.size();row++){
            int start=row*columns,end=Math.min(start+columns,visible.size()),height=heights.get(row);
            int x=rtl?right-left-getPaddingRight():getPaddingLeft();
            for(int i=start;i<end;i++){
                View child=visible.get(i);int w=child.getMeasuredWidth();
                int a=rtl?x-w:x;
                child.layout(a,y,a+w,y+height);x+=rtl?-(w+gap):w+gap;
            }
            y+=height+gap;
        }
    }
}
