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
        String t=title(value);return !t.isEmpty()&&t.length()<=MAX_TITLE;
    }
    static int compare(boolean aPinned,long aCreated,String aKey,boolean bPinned,long bCreated,String bKey){
        int pin=Boolean.compare(bPinned,aPinned);
        if(pin!=0)return pin;
        int time=Long.compare(bCreated,aCreated);
        return time!=0?time:aKey.compareTo(bKey);
    }
}
