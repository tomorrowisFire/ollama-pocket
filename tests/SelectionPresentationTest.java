package com.ollamapocket.app;

public final class SelectionPresentationTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){n++;}}
    public static void main(String[] args){
        String[] defaults={"전체에서 찾기","추천 순","전체 모델","전체 양자화"};
        String[] active={"Ollama에서 찾기","용량 작은 순","Qwen","Q4_K_M"};
        String[] localized={"전체 모델","All models","すべてのモデル","全部模型","所有模型","Q4_K_M"};
        for(int i=0;i<4;i++){
            ok(!SelectionPresentation.filterActive(i,defaults[i]));
            ok(SelectionPresentation.filterActive(i,active[i]));
            ok(!SelectionPresentation.filterActive(i,null));
            ok(!SelectionPresentation.filterActive(i,""));
            for(String label:localized){
                ok(SelectionPresentation.filterValue(label,false).equals(label));
                ok(SelectionPresentation.filterValue(label,true).equals("✓ "+label));
                ok(SelectionPresentation.settingText(label,active[i]).equals(label+"\n"+active[i]));
                ok(SelectionPresentation.settingDescription(label,active[i]).equals(label+" "+active[i]));
            }
        }
        ok(SelectionPresentation.settingText("Layout","Auto\nCompact").equals("Layout\nAuto\nCompact"));
        ok(SelectionPresentation.settingDescription("Layout","Auto\nCompact").equals("Layout Auto Compact"));
        rejects(()->SelectionPresentation.filterActive(-1,"x"));
        rejects(()->SelectionPresentation.filterActive(4,"x"));
        rejects(()->SelectionPresentation.filterValue(null,true));
        rejects(()->SelectionPresentation.settingText("","x"));
        rejects(()->SelectionPresentation.settingText("x",null));
        System.out.println("PASS: "+n+" selection presentation assertions");
    }
}
