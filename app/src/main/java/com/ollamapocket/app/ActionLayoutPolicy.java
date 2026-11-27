package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure policy using caller-measured action widths/heights, including actual localized text and padding. */
final class ActionLayoutPolicy {
    static final int MAX_ACTIONS=12;
    static final int MIN_TARGET=WorkbenchVisualPolicy.MIN_TARGET_DP;
    static final int GAP=WorkbenchVisualPolicy.GAP_DP;
    static final class Action {
        final int width,height;
        Action(int measuredWidth,int measuredHeight){
            if(measuredWidth<0||measuredWidth>2048||measuredHeight<0||measuredHeight>1024)
                throw new IllegalArgumentException("Action measurement");
            width=Math.max(MIN_TARGET,measuredWidth);
            height=Math.max(MIN_TARGET,measuredHeight);
        }
    }
    static final class Row {
        final int first,count,width,height;
        final boolean horizontalScroll;
        private Row(int first,int count,int width,int height,int viewport){
            this.first=first;this.count=count;this.width=width;this.height=height;
            horizontalScroll=width>viewport;
        }
    }
    static final class Layout {
        final List<Row> rows;
        final int contentHeight;
        final boolean verticalScroll;
        private Layout(List<Row> rows,int contentHeight,int viewportHeight){
            this.rows=Collections.unmodifiableList(new ArrayList<>(rows));
            this.contentHeight=contentHeight;verticalScroll=contentHeight>viewportHeight;
        }
    }
    private ActionLayoutPolicy(){}
    static Layout layout(int width,int height,float scale,List<Action> actions){
        if(width<0||width>8192||height<0||height>8192||!Float.isFinite(scale)||scale<0.5f||scale>4f||
                actions==null||actions.size()>MAX_ACTIONS)
            throw new IllegalArgumentException("Action viewport");
        ArrayList<Action> copy=new ArrayList<>(actions);
        ArrayList<Row> rows=new ArrayList<>();
        int first=0,count=0,rowWidth=0,rowHeight=0,totalHeight=0;
        boolean single=WorkbenchLayoutPolicy.largeText(scale)&&width<600;
        for(int i=0;i<copy.size();i++){
            Action a=copy.get(i);
            if(a==null)throw new IllegalArgumentException("Missing action");
            if(count>0&&(single||rowWidth+GAP+a.width>width)){
                rows.add(new Row(first,count,rowWidth,rowHeight,width));
                totalHeight+=rowHeight+(rows.size()>1?GAP:0);
                first=i;count=0;rowWidth=0;rowHeight=0;
            }
            rowWidth+=(count==0?0:GAP)+a.width;count++;
            rowHeight=Math.max(rowHeight,a.height);
        }
        if(count>0){
            rows.add(new Row(first,count,rowWidth,rowHeight,width));
            totalHeight+=rowHeight+(rows.size()>1?GAP:0);
        }
        return new Layout(rows,totalHeight,height);
    }
}
