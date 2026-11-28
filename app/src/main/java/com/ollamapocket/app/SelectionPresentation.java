package com.ollamapocket.app;

/** Presentation only: localized labels never become stored identifiers or option keys. */
final class SelectionPresentation {
    private SelectionPresentation(){}
    private static final String[] DEFAULTS={"전체에서 찾기","추천 순","전체 모델","전체 양자화"};
    static boolean filterActive(int index,String value){
        if(index<0||index>=DEFAULTS.length)throw new IllegalArgumentException("Filter index");
        return value!=null&&!value.isEmpty()&&!DEFAULTS[index].equals(value);
    }
    static String filterValue(String localized,boolean active){
        if(localized==null)throw new IllegalArgumentException("Missing filter label");
        return active?"✓ "+localized:localized;
    }
    static String settingText(String label,String value){
        if(label==null||label.isEmpty()||value==null||value.isEmpty())throw new IllegalArgumentException("Missing setting label");
        return label+"\n"+value;
    }
    static String settingDescription(String label,String value){
        return settingText(label,value).replace('\n',' ');
    }
}
