package com.ollamapocket.app;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static com.ollamapocket.app.ActionLayoutPolicy.*;

public final class ActionLayoutPolicyTest {
    private static int checks;
    private static void ok(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static void reject(Runnable task){checks++;try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid measurement accepted");}
    private static void verify(Layout result,List<Action> input,int width,int height,float scale){
        int cursor=0,total=0;
        for(Row row:result.rows){
            ok(row.first==cursor&&row.count>0,"Stable focus order");
            int w=0,h=0;
            for(int i=0;i<row.count;i++){
                Action a=input.get(cursor++);
                ok(a.width>=48&&a.height>=48,"Minimum target");
                w+=a.width+(i>0?GAP:0);h=Math.max(h,a.height);
            }
            ok(w==row.width&&h==row.height,"Measured geometry");
            ok(row.horizontalScroll==(w>width)&&(!row.horizontalScroll||row.count==1),"No hidden or squeezed action");
            if(scale>1.2f&&width<600)ok(row.count==1,"Large text stacked");
            total+=h+(row.first>0?GAP:0);
        }
        ok(cursor==input.size()&&total==result.contentHeight,"All actions retained");
        ok(result.verticalScroll==(total>height),"Height scroll");
    }
    public static void main(String[] args){
        Action minimum=new Action(1,0);ok(minimum.width==48&&minimum.height==48,"Minimum dimensions");
        Layout pair=layout(108,48,1f,List.of(minimum,minimum));
        ok(pair.rows.size()==1&&pair.rows.get(0).width==104,"Two targets fit");
        Layout wrap=layout(103,80,1f,List.of(minimum,minimum));
        ok(wrap.rows.size()==2&&wrap.contentHeight==104&&wrap.verticalScroll,"Wrap");
        Layout wide=layout(300,48,1f,List.of(new Action(700,60)));
        ok(wide.rows.get(0).horizontalScroll&&wide.verticalScroll,"Oversized measured label retained");
        // Synthetic measurement fixtures for all five UI locales, not actual text rendering.
        List<List<Action>> localized=List.of(
                List.of(new Action(90,48),new Action(110,48),new Action(150,48)),
                List.of(new Action(145,48),new Action(190,60),new Action(230,60)),
                List.of(new Action(130,48),new Action(160,48),new Action(160,56)),
                List.of(new Action(96,48),new Action(128,48),new Action(140,48)),
                List.of(new Action(100,48),new Action(140,48),new Action(150,56)));
        for(List<Action> actions:localized){
            for(int width:new int[]{0,24,48,180,320,411,599,600,720,880,1280,8192}){
                for(int height:new int[]{0,48,100,360,1000}){
                    for(float scale:new float[]{0.5f,1f,1.2f,1.21f,2f,4f})
                        verify(layout(width,height,scale,actions),actions,width,height,scale);
                }
            }
        }
        java.util.Random random=new java.util.Random(144);
        for(int round=0;round<1000;round++){
            List<Action> actions=new ArrayList<>();
            for(int n=random.nextInt(13);n>0;n--)actions.add(new Action(random.nextInt(2049),random.nextInt(1025)));
            int w=random.nextInt(8193),h=random.nextInt(8193);
            float scale=0.5f+random.nextFloat()*3.5f;
            verify(layout(w,h,scale,actions),actions,w,h,scale);
        }
        for(int bad:new int[]{-1,8193,Integer.MAX_VALUE})reject(()->layout(bad,100,1f,List.of(minimum)));
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0.49f,4.01f})reject(()->layout(100,100,bad,List.of(minimum)));
        reject(()->new Action(-1,48));reject(()->new Action(100,1025));reject(()->new Action(2049,48));
        reject(()->layout(100,100,1f,null));reject(()->layout(100,100,1f,Arrays.asList((Action)null)));
        reject(()->layout(100,100,1f,java.util.Collections.nCopies(13,minimum)));
        System.out.println("PASS: "+checks+" responsive action, focus order and scroll layout checks");
    }
}
