package com.ollamapocket.app;

public final class WorkspaceTextPolicyTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("workspace text "+n);}
    private static void rejects(Runnable work){
        try{work.run();}catch(IllegalArgumentException expected){check(true);return;}
        throw new AssertionError("Invalid text review accepted");
    }
    private static void pages(String text){
        WorkspaceTextPolicy.Snapshot s=WorkspaceTextPolicy.review(text);
        StringBuilder out=new StringBuilder();
        WorkspaceTextPolicy.Window w=s.first(),last=null;
        int end=0;
        while(w!=null){
            check(w.start==end&&w.end>w.start&&w.end-w.start<=WorkspaceTextPolicy.PAGE);
            check(!Character.isLowSurrogate(w.text().charAt(0))&&!Character.isHighSurrogate(w.text().charAt(w.text().length()-1)));
            out.append(w.text());end=w.end;last=w;w=s.next(w);
        }
        check(out.toString().equals(text)&&end==text.length());
        out.setLength(0);w=last;int start=text.length();
        while(w!=null){
            check(w.end==start&&w.end-w.start<=WorkspaceTextPolicy.PAGE);
            out.insert(0,w.text());start=w.start;w=s.previous(w);
        }
        check(start==0&&out.toString().equals(text));
    }
    public static void main(String[] args){
        pages("  한글\n日本語\t中文 English 😀  ");
        pages("x".repeat(3999)+"😀"+"y".repeat(3999)+"😀"+"z".repeat(9001));
        pages("😀".repeat(80000));
        pages("a".repeat(160000));
        for(int i=3996;i<=4004;i++)pages("x".repeat(i)+"😀"+"z".repeat(8000-i));
        String text="x".repeat(3999)+"A😀B literal.*\n"+"x".repeat(4200)+"A😀B literal.*\n";
        WorkspaceTextPolicy.Snapshot s=WorkspaceTextPolicy.review(text);
        WorkspaceTextPolicy.Window first=s.find("A😀B",null,true);
        check(first.matchStart==3999&&first.matchEnd==4003);
        check(first.text().substring(first.matchStart-first.start,first.matchEnd-first.start).equals("A😀B"));
        WorkspaceTextPolicy.Window next=s.find("A😀B",first,true);
        check(next.matchStart>first.matchStart&&s.find("A😀B",next,true)==null);
        check(s.find("A😀B",next,false).matchStart==first.matchStart&&s.find("A😀B",first,false)==null);
        check(s.find("A😀B",null,false).matchStart==next.matchStart);
        check(s.find("a😀b",null,true)==null&&s.find("literal.*",null,true)!=null&&s.find("literal.+",null,true)==null);
        check(s.matches(text)&&!s.matches(text+" ")&&!s.matches(null));
        check(s.copy(first,true,text).equals(text)&&s.copy(first,false,text).equals(first.text()));
        rejects(()->s.copy(first,false,text+" "));
        WorkspaceTextPolicy.Snapshot other=WorkspaceTextPolicy.review(text);
        rejects(()->s.copy(other.first(),true,text));rejects(()->s.next(other.first()));
        rejects(()->s.find("A😀B",other.first(),true));rejects(()->s.find("literal.*",first,true));
        rejects(()->s.find("",null,true));rejects(()->s.find(" ".repeat(10),null,true));rejects(()->s.find("x".repeat(161),null,true));
        rejects(()->WorkspaceTextPolicy.review(null));rejects(()->WorkspaceTextPolicy.review(""));
        rejects(()->WorkspaceTextPolicy.review(" \n\t"));rejects(()->WorkspaceTextPolicy.review("x".repeat(160001)));
        rejects(()->WorkspaceTextPolicy.review("a\u0000b"));rejects(()->WorkspaceTextPolicy.review("\uD800"));
        rejects(()->WorkspaceTextPolicy.review("\uDC00"));rejects(()->s.find("\uD800",null,true));
        WorkspaceTextPolicy.Snapshot overlap=WorkspaceTextPolicy.review("aaaaa");
        WorkspaceTextPolicy.Window a=overlap.find("aa",null,true),b=overlap.find("aa",a,true);
        check(a.matchStart==0&&b.matchStart==2&&overlap.find("aa",b,true)==null);
        check(overlap.find("aa",b,false).matchStart==0);
        System.out.println("PASS: "+n+" exact text navigation and copy assertions");
    }
}
