package com.ollamapocket.app;

import java.util.Locale;
public final class DiagnosticValuesTest {
    private static int count;
    private static void eq(String a,String b){if(!a.equals(b))throw new AssertionError(a+" != "+b);count++;}
    public static void main(String[] args){
        for(Locale locale:new Locale[]{Locale.US,Locale.KOREA,Locale.SIMPLIFIED_CHINESE,Locale.TRADITIONAL_CHINESE,Locale.JAPAN}){
            eq(DiagnosticValues.percentage(-1,locale,"N/A"),"N/A");
            eq(DiagnosticValues.percentage(1001,locale,"N/A"),"N/A");
            eq(DiagnosticValues.percentage(0,locale,"N/A"),"0.0%");
            eq(DiagnosticValues.percentage(1000,locale,"N/A"),"100.0%");
            eq(DiagnosticValues.percentage(123,locale,"N/A"),"12.3%");
            eq(DiagnosticValues.battery(-1,locale,"N/A"),"N/A");
            eq(DiagnosticValues.battery(337,locale,"N/A"),"33.7℃");
        }
        eq(DiagnosticValues.nonnegative(0,"N/A"),"0");
        eq(DiagnosticValues.nonnegative(-1,"N/A"),"N/A");
        eq(DiagnosticValues.positive(0,"N/A"),"N/A");
        eq(DiagnosticValues.unknownText("미확인","N/A"),"N/A");
        eq(DiagnosticValues.unknownText("unknown","N/A"),"N/A");
        eq(DiagnosticValues.unknownText("Qwen2.5","N/A"),"Qwen2.5");
        System.out.println("DiagnosticValuesTest PASS: "+count+" assertions");
    }
}
