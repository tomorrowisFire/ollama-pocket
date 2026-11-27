package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ChartArtifactTest {
    private static int checks;
    private static void ok(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static void reject(Runnable task){checks++;try{task.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid chart accepted");}
    private static ChartArtifact.Point p(String label,double value){return new ChartArtifact.Point(label,value);}
    public static void main(String[] args){
        List<ChartArtifact.Point> points=new ArrayList<>(Arrays.asList(p("한국어 <script>alert(1)</script>",-3),
                p("=HYPERLINK(\"https://example.invalid\")",4),p("日本語 & 繁體中文",0),p("　+1",2)));
        ChartArtifact bar=new ChartArtifact(ChartArtifact.Kind.BAR,"<img src=x onerror=alert(1)>",points);
        points.clear();ok(bar.points.size()==4,"Defensive copy");
        try{bar.points.clear();throw new AssertionError("Mutable chart");}catch(UnsupportedOperationException expected){checks++;}
        String html=bar.html(),csv=bar.csv();
        ok(html.contains("default-src 'none'")&&html.contains("form-action 'none'"),"Static CSP");
        ok(!html.contains("<script")&&!html.contains("<img")&&html.contains("&lt;script&gt;")&&
                html.contains("&lt;img")&&html.contains("&amp;")&&html.contains("&quot;"),"Untrusted text escaped");
        ok(html.contains("scope=\"row\"")&&html.contains("aria-hidden=\"true\""),"Accessible data table");
        ok(html.contains("left:12.500%;width:37.500%")&&html.contains("left:50.000%;width:50.000%"),"Signed bars");
        ok(csv.startsWith("label,value\r\n")&&csv.contains("\"'=HYPERLINK(\"\"https://example.invalid\"\")\",4.0\r\n")&&
                csv.contains("\"'　+1\",2.0\r\n"),"Conservative CSV text");
        ok(csv.contains("\",-3.0\r\n"),"Trusted numeric negative");
        ChartArtifact line=new ChartArtifact(ChartArtifact.Kind.LINE,"line",bar.points);
        ok(line.html().contains("class=\"line\"")&&line.html().contains("transform:rotate(")&&line.html().contains("class=\"point\""),"Line geometry");
        ok(!line.html().contains("NaN")&&!line.html().contains("Infinity"),"Finite coordinates");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,1e9+1,-1e9-1})reject(()->p("x",bad));
        for(String bad:new String[]{null,""," ","\u00a0","\u200d","x\n","x\u0000","x\u202e","x\u2028","x\ud800","x".repeat(97)})reject(()->p(bad,0));
        reject(()->new ChartArtifact(null,"x",bar.points));
        reject(()->new ChartArtifact(ChartArtifact.Kind.LINE,"x",List.of()));
        reject(()->new ChartArtifact(ChartArtifact.Kind.LINE,"x",Arrays.asList((ChartArtifact.Point)null)));
        reject(()->new ChartArtifact(ChartArtifact.Kind.LINE,"x".repeat(121),bar.points));
        reject(()->new ChartArtifact(ChartArtifact.Kind.BAR,"x",java.util.Collections.nCopies(65,p("x",0))));
        for(int count=1;count<=64;count++){
            for(ChartArtifact.Kind kind:ChartArtifact.Kind.values()){
                for(double value:new double[]{0,Double.MIN_VALUE,-Double.MIN_VALUE,1e9,-1e9}){
                    ChartArtifact a=new ChartArtifact(kind,"图表",java.util.Collections.nCopies(count,p("데이터",value)));
                    String page=a.html();
                    ok(page.length()<128000&&!page.contains("NaN")&&!page.contains("Infinity"),"Bounded finite chart");
                    ok(a.csv().split("\r\n").length==count+1,"CSV row count");
                }
            }
        }
        Locale previous=Locale.getDefault();
        try{Locale.setDefault(Locale.GERMANY);ok(bar.html().equals(html)&&bar.csv().equals(csv),"Locale invariant export");}
        finally{Locale.setDefault(previous);}
        System.out.println("PASS: "+checks+" typed chart, static HTML and CSV safety checks");
    }
}
