package com.ollamapocket.app;

import java.util.Locale;

/** Pure display conversion. Unavailable samples are never converted to invented zero readings. */
final class DiagnosticValues {
    private DiagnosticValues(){}
    static String percentage(int permille,Locale locale,String unavailable){
        if(permille<0||permille>1000)return unavailable;
        return String.format(locale,"%.1f%%",permille/10.0);
    }
    static String battery(int tenths,Locale locale,String unavailable){
        if(tenths<0)return unavailable;
        return String.format(locale,"%.1f℃",tenths/10.0);
    }
    static String nonnegative(long value,String unavailable){return value<0?unavailable:Long.toString(value);}
    static String positive(long value,String unavailable){return value<=0?unavailable:Long.toString(value);}
    static String unknownText(String value,String unavailable){
        return value==null||value.trim().isEmpty()||"미확인".equals(value)||"unknown".equalsIgnoreCase(value)?unavailable:value;
    }
}
