package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pack measured actions in stable focus order; an oversized action gets its own wrapping row. */
final class ActionFlowPolicy {
    private ActionFlowPolicy(){}
    static final class Row {
        final int first,end;
        Row(int first,int end){this.first=first;this.end=end;}
    }
    static List<Row> rows(int available,int gap,boolean single,int[] widths){
        if(available<0||gap<0||widths==null)throw new IllegalArgumentException("Action viewport");
        List<Row> result=new ArrayList<>();
        int start=0;long used=0;
        for(int i=0;i<widths.length;i++){
            if(widths[i]<0)throw new IllegalArgumentException("Action width");
            int width=Math.min(available,widths[i]);
            if(i>start&&(single||widths[i-1]>available||widths[i]>available||used+gap+width>available)){
                result.add(new Row(start,i));start=i;used=0;
            }
            used+=(i==start?0L:gap)+width;
        }
        if(start<widths.length)result.add(new Row(start,widths.length));
        return Collections.unmodifiableList(result);
    }
    static int fittedWidth(int available,int natural){
        if(available<0||natural<0)throw new IllegalArgumentException("Action width");
        return Math.min(available,natural);
    }
}
