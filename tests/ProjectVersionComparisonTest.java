package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;

public final class ProjectVersionComparisonTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("comparison "+n);n++;}
    private static void rejects(Runnable action){
        try{action.run();}catch(IllegalArgumentException expected){n++;return;}
        throw new AssertionError("Invalid version accepted");
    }
    private static boolean boundary(String s,int at){
        return at>=0&&at<=s.length()&&(at==0||at==s.length()||!Character.isLowSurrogate(s.charAt(at)));
    }
    private static void verify(String a,String b){
        ProjectVersionComparison.Result r=ProjectVersionComparison.compare(a,b);
        int aEnd=a.length()-r.suffix,bEnd=b.length()-r.suffix;
        ok(r.prefix>=0&&r.suffix>=0&&r.prefix<=aEnd&&r.prefix<=bEnd);
        ok(boundary(a,r.prefix)&&boundary(b,r.prefix)&&boundary(a,aEnd)&&boundary(b,bEnd));
        String prefix=a.substring(0,r.prefix),suffix=a.substring(aEnd);
        ok(prefix.equals(b.substring(0,r.prefix))&&suffix.equals(b.substring(bEnd)));
        ok((prefix+r.previous+suffix).equals(a));
        ok((prefix+r.current+suffix).equals(b));
        ok(r.identical()==a.equals(b));
        ok(r.prefix==a.length()||r.prefix==b.length()||a.codePointAt(r.prefix)!=b.codePointAt(r.prefix));
        ok(aEnd==r.prefix||bEnd==r.prefix||a.codePointBefore(aEnd)!=b.codePointBefore(bEnd));
    }
    private static void exact(String a,String b,int p,int s,String old,String newer){
        ProjectVersionComparison.Result r=ProjectVersionComparison.compare(a,b);
        ok(r.prefix==p&&r.suffix==s&&r.previous.equals(old)&&r.current.equals(newer));
        verify(a,b);
    }
    private static void generate(List<String> out,String prefix,int remaining){
        out.add(prefix);
        if(remaining>0)for(String part:new String[]{"a","b","😀","😃"," "})generate(out,prefix+part,remaining-1);
    }
    public static void main(String[] args){
        exact("","",0,0,"","");
        exact("같은😀","같은😀",4,0,"","");
        exact("a","abc",1,0,"","bc");
        exact("abc","a",1,0,"bc","");
        exact("abc","axc",1,1,"b","x");
        exact("abc","zabc",0,3,"","z");
        exact("zabc","abc",0,3,"z","");
        exact("aaa","aaaa",3,0,"","a");
        exact("prefix\t\nend","prefix  end",6,3,"\t\n","  ");
        exact("😀末","😃末",0,1,"😀","😃");
        exact("頭😀","頭😃",1,0,"😀","😃");
        exact("한글😀日本語","한글😃日本語",2,3,"😀","😃");
        exact("e\u0301","é",0,0,"e\u0301","é");
        exact("a\u0000z","a\nz",1,1,"\u0000","\n");
        String left="x".repeat(15999),right="y".repeat(15999);
        exact(left+"😀"+right,left+"😃"+right,15999,15999,"😀","😃");
        verify("😀".repeat(16000),"😀".repeat(16000));
        verify("a".repeat(32000),"");
        verify("","b".repeat(32000));
        rejects(()->ProjectVersionComparison.compare(null,"a"));
        rejects(()->ProjectVersionComparison.compare("a",null));
        rejects(()->ProjectVersionComparison.compare("a".repeat(32001),""));
        rejects(()->ProjectVersionComparison.compare("","a".repeat(32001)));
        rejects(()->ProjectVersionComparison.compare("\uD800","x"));
        rejects(()->ProjectVersionComparison.compare("x","\uDC00"));
        rejects(()->ProjectVersionComparison.compare("a\uD800b","ab"));
        List<String> values=new ArrayList<>();generate(values,"",3);
        ok(values.size()==156);
        for(String a:values)for(String b:values)verify(a,b);
        System.out.println("PASS: "+n+" exact version comparison assertions ("+(values.size()*values.size())+" exhaustive pairs)");
    }
}
