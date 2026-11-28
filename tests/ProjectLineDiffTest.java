package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ProjectLineDiffTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Invalid input");}catch(IllegalArgumentException expected){n++;}}
    private static void verify(String a,String b){
        ProjectLineDiff.Result diff=ProjectLineDiff.compare(a,b);
        StringBuilder left=new StringBuilder(),right=new StringBuilder();int old=0,next=0,removed=0,added=0;
        ProjectLineDiff.Kind prior=null;
        for(ProjectLineDiff.Run run:diff.runs){
            ok(run.count>0&&run.oldStart==old&&run.newStart==next&&run.kind!=prior);prior=run.kind;
            for(int i=0;i<run.count;i++){
                switch(run.kind){
                    case SAME:String x=diff.previous.get(old++),y=diff.current.get(next++);ok(x.equals(y));left.append(x);right.append(y);break;
                    case REMOVE:left.append(diff.previous.get(old++));removed++;break;
                    case ADD:right.append(diff.current.get(next++));added++;break;
                }
            }
        }
        ok(left.toString().equals(a)&&right.toString().equals(b));
        ok(old==diff.previous.size()&&next==diff.current.size());
        ok(removed==diff.removed&&added==diff.added);
        ok((removed==0&&added==0)==a.equals(b));
        ProjectLineDiff.Preview preview=diff.preview();
        ok(preview.text.length()<=ProjectLineDiff.MAX_PREVIEW_CHARS);
        if(!preview.text.isEmpty())ok(!Character.isHighSurrogate(preview.text.charAt(preview.text.length()-1)));
        if(!diff.coarse&&diff.previous.size()<=12&&diff.current.size()<=12){
            int[][] lcs=new int[diff.previous.size()+1][diff.current.size()+1];
            for(int i=1;i<lcs.length;i++)for(int j=1;j<lcs[i].length;j++)
                lcs[i][j]=diff.previous.get(i-1).equals(diff.current.get(j-1))?lcs[i-1][j-1]+1:Math.max(lcs[i-1][j],lcs[i][j-1]);
            ok(removed+added==old+next-2*lcs[old][next]);
        }
    }
    private static void generate(List<String> all,String prefix,int depth){
        all.add(prefix);
        if(depth>0)for(String line:new String[]{"a\n","b\n","😀\r\n","\r"})generate(all,prefix+line,depth-1);
    }
    public static void main(String[] args){
        ProjectLineDiff.Result separated=ProjectLineDiff.compare("a\nb\nc\nd\ne\n","a\nB\nc\nD\ne\n");
        ok(separated.removed==2&&separated.added==2&&!separated.coarse);
        ok(separated.preview().text.equals("- 2 | b [LF]\n+ 2 | B [LF]\n- 4 | d [LF]\n+ 4 | D [LF]\n"));
        ok(ProjectLineDiff.compare("a\n","a\r\n").preview().text.equals("- 1 | a [LF]\n+ 1 | a [CRLF]\n"));
        ok(ProjectLineDiff.compare("a","a\n").preview().text.contains("[EOF]"));
        ok(ProjectLineDiff.compare("a\r","a\n").preview().text.contains("[CR]"));
        verify("","");verify("x","");verify("","x");verify("한국어\n日本語\r\n😀","한국어\r日本語\n😃");
        verify("a\u0000\n","a\t\n");
        List<String> all=new ArrayList<>();generate(all,"",3);
        for(String a:all)for(String b:all)verify(a,b);
        Random random=new Random(1124);
        for(int i=0;i<500;i++){
            StringBuilder a=new StringBuilder(),b=new StringBuilder();
            for(int j=random.nextInt(12);j>0;j--)a.append(random.nextInt(4)).append('\n');
            for(int j=random.nextInt(12);j>0;j--)b.append(random.nextInt(4)).append('\n');
            verify(a.toString(),b.toString());
        }
        ProjectLineDiff.Result large=ProjectLineDiff.compare("a\n".repeat(1000),"b\n".repeat(1000));
        ok(large.coarse&&large.removed==1000&&large.added==1000&&large.preview().truncated);
        verify("a\n".repeat(1000),"b\n".repeat(1000));
        ProjectLineDiff.Result huge=ProjectLineDiff.compare("😀".repeat(16000),"x");
        ok(huge.preview().truncated&&huge.preview().text.endsWith("…"));
        verify("😀".repeat(16000),"x");verify("\n".repeat(32000),"\n".repeat(32000));
        ok(!ProjectLineDiff.compare("a\n".repeat(15999)+"x\n","a\n".repeat(15999)+"y\n").coarse);
        rejects(()->ProjectLineDiff.compare(null,""));rejects(()->ProjectLineDiff.compare("","x".repeat(32001)));
        rejects(()->ProjectLineDiff.compare("\uD800",""));rejects(()->ProjectLineDiff.compare("","\uDC00"));
        try{separated.runs.clear();throw new AssertionError("Mutable runs");}catch(UnsupportedOperationException expected){n++;}
        System.out.println("PASS: "+n+" bounded exact line diff assertions");
    }
}
