package com.ollamapocket.app;

import java.util.List;
import java.util.Random;

public final class ActionFlowPolicyTest {
    private static int n;
    private static void ok(boolean pass){n++;if(!pass)throw new AssertionError("check "+n);}
    private static void rejects(Runnable r){try{r.run();throw new AssertionError("Invalid geometry");}catch(IllegalArgumentException expected){n++;}}
    private static void verify(int width,int gap,boolean single,int[] values){
        List<ActionFlowPolicy.Row> rows=ActionFlowPolicy.rows(width,gap,single,values);
        int cursor=0;
        for(ActionFlowPolicy.Row row:rows){
            ok(row.first==cursor&&row.end>row.first&&row.end<=values.length);
            long used=0;
            for(int i=row.first;i<row.end;i++){
                int fitted=ActionFlowPolicy.fittedWidth(width,values[i]);
                ok(fitted>=0&&fitted<=width&&fitted<=values[i]);
                if(values[i]>width||single)ok(row.end-row.first==1);
                used+=(i==row.first?0L:gap)+fitted;
            }
            ok(used<=width);cursor=row.end;
            if(cursor<values.length&&!single&&values[cursor-1]<=width&&values[cursor]<=width)
                ok(used+gap+values[cursor]>width);
        }
        ok(cursor==values.length);
    }
    public static void main(String[] args){
        List<ActionFlowPolicy.Row> compact=ActionFlowPolicy.rows(320,10,false,new int[]{100,100,100,100});
        ok(compact.size()==2&&compact.get(0).end==3&&compact.get(1).first==3);
        ok(ActionFlowPolicy.rows(320,10,true,new int[]{100,100,100,100}).size()==4);
        ok(ActionFlowPolicy.rows(320,10,false,new int[]{321,100,100}).size()==2);
        ok(ActionFlowPolicy.rows(320,10,false,new int[]{}).isEmpty());
        verify(Integer.MAX_VALUE,Integer.MAX_VALUE,false,new int[]{Integer.MAX_VALUE,1,0});
        verify(0,10,false,new int[]{0,1,0});
        Random random=new Random(1114);
        for(int i=0;i<2000;i++){
            int[] widths=new int[random.nextInt(13)];
            for(int j=0;j<widths.length;j++)widths[j]=random.nextInt(900);
            verify(random.nextInt(1401),random.nextInt(25),random.nextBoolean(),widths);
        }
        rejects(()->ActionFlowPolicy.rows(-1,10,false,new int[]{1}));
        rejects(()->ActionFlowPolicy.rows(10,-1,false,new int[]{1}));
        rejects(()->ActionFlowPolicy.rows(10,0,false,new int[]{-1}));
        rejects(()->ActionFlowPolicy.rows(10,0,false,null));
        rejects(()->ActionFlowPolicy.fittedWidth(-1,0));
        System.out.println("PASS: "+n+" flow layout assertions");
    }
}
