package com.ollamapocket.app;

public final class WorkspaceChromePolicyTest {
    private static int n;
    private static void ok(boolean pass){n++;if(!pass)throw new AssertionError("check "+n);}
    public static void main(String[] args){
        for(int active=-1;active<=5;active++)for(int destination=-1;destination<=5;destination++)
            ok(WorkspaceChromePolicy.selectedDestination(active,destination)==(destination>=1&&destination<=3&&active==destination));
        for(String label:new String[]{"Models","모델","モデル","模型","裝置","👩‍💻"}){
            ok(WorkspaceChromePolicy.selectionDescription(label,true,"Selected").equals(label+" · Selected"));
            ok(WorkspaceChromePolicy.selectionDescription(label,false,"Selected").equals(label));
        }
        ok(WorkspaceChromePolicy.centeredInsetPx(1000,880,6)==66);
        ok(WorkspaceChromePolicy.centeredInsetPx(5,5,6)==2);
        ok(WorkspaceChromePolicy.centeredInsetPx(0,100,6)==0);
        ok(WorkspaceChromePolicy.centeredInsetPx(Integer.MAX_VALUE,0,Integer.MAX_VALUE)==Integer.MAX_VALUE/2);
        ok(WorkspaceChromePolicy.bubbleWidthPx(30,10,640,80)==20);
        ok(WorkspaceChromePolicy.bubbleWidthPx(400,8,640,80)==337);
        ok(WorkspaceChromePolicy.bubbleWidthPx(1000,8,640,80)==640);
        ok(WorkspaceChromePolicy.bubbleWidthPx(100,200,640,80)==0);
        ok(WorkspaceChromePolicy.bubbleWidthPx(Integer.MAX_VALUE,0,Integer.MAX_VALUE,0)==1846835936);
        for(int width=0;width<=1800;width+=3){
            for(int padding:new int[]{0,8,20,96,400,Integer.MAX_VALUE}){
                int usable=Math.max(0,width-padding);
                int bubble=WorkspaceChromePolicy.bubbleWidthPx(width,padding,640,80);
                ok(bubble>=0&&bubble<=usable&&bubble<=640);
                if(usable>=80)ok(bubble>=80);
            }
            for(int measure:new int[]{0,320,640,880,2000})for(int gutter:new int[]{0,6,48}){
                int side=WorkspaceChromePolicy.centeredInsetPx(width,measure,gutter);
                ok(side>=0&&2L*side<=width);
                ok(side==Math.min(width/2,(width-Math.min(width,measure))/2+gutter));
            }
        }
        System.out.println("PASS: "+n+" viewport and navigation assertions");
    }
}
