package com.ollamapocket.app;

/** Metadata-only conversation organization. No message reads or writes. */
final class ChatOrganization {
    static final int MAX_TITLE=80;
    private ChatOrganization(){}
    static String title(String value){
        if(value==null)return "";
        return value.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim();
    }
    static boolean validTitle(String value){
        if(value==null)return false;
        boolean visible=false;
        for(int i=0;i<value.length();){
            int cp=value.codePointAt(i);i+=Character.charCount(cp);
            if(cp>=0xD800&&cp<=0xDFFF||cp==0x2028||cp==0x2029||
                Character.isISOControl(cp)&&cp!='\r'&&cp!='\n'&&cp!='\t'||
                Character.getType(cp)==Character.FORMAT&&cp!=0x200C&&cp!=0x200D)return false;
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&Character.getType(cp)!=Character.FORMAT)visible=true;
        }
        return visible&&title(value).length()<=MAX_TITLE;
    }
    static int compare(boolean aPinned,long aCreated,String aKey,boolean bPinned,long bCreated,String bKey){
        int pin=Boolean.compare(bPinned,aPinned);
        if(pin!=0)return pin;
        int time=Long.compare(bCreated,aCreated);
        return time!=0?time:aKey.compareTo(bKey);
    }
}
