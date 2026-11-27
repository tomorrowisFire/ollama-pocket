package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Typed, bounded chart data and derived static export; never accepts markup or executes code. */
final class ChartArtifact {
    enum Kind { BAR, LINE }
    static final int MAX_POINTS=64;
    static final double MAX_ABS=1_000_000_000d;
    final Kind kind;
    final String title;
    final List<Point> points;
    static final class Point {
        final String label;
        final double value;
        Point(String label,double value){
            this.label=text(label,96);
            if(!Double.isFinite(value)||Math.abs(value)>MAX_ABS)
                throw new IllegalArgumentException("Chart value");
            this.value=value==0?0:value;
        }
    }
    ChartArtifact(Kind kind,String title,List<Point> points){
        if(kind==null||points==null||points.isEmpty()||points.size()>MAX_POINTS)
            throw new IllegalArgumentException("Chart shape");
        this.kind=kind;this.title=text(title,120);
        ArrayList<Point> copy=new ArrayList<>(points);
        for(Point p:copy)if(p==null)throw new IllegalArgumentException("Chart point");
        this.points=Collections.unmodifiableList(copy);
    }
    private static String text(String s,int limit){
        if(s==null||s.length()>limit||s.trim().isEmpty())throw new IllegalArgumentException("Chart text");
        boolean visible=false;
        for(int i=0;i<s.length();){
            int cp=s.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isISOControl(cp)||cp==0x2028||cp==0x2029||
                    cp>=0xd800&&cp<=0xdfff||
                    Character.getType(cp)==Character.FORMAT&&cp!=0x200c&&cp!=0x200d)
                throw new IllegalArgumentException("Chart text control");
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&cp!=0x200c&&cp!=0x200d)visible=true;
        }
        if(!visible)throw new IllegalArgumentException("Empty chart text");
        return s;
    }
    private static String escape(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
    /** Spreadsheet text is conservatively apostrophe-prefixed and CSV-quoted.
     * Destination application interpretation is not guaranteed; values are typed numbers. */
    private static String csvLabel(String s){return "\"'"+s.replace("\"","\"\"")+"\"";}
    String csv(){
        StringBuilder out=new StringBuilder("label,value\r\n");
        for(Point p:points)out.append(csvLabel(p.label)).append(',').append(Double.toString(p.value)).append("\r\n");
        return out.toString();
    }
    private static String n(double value){return String.format(Locale.ROOT,"%.3f",value);}
    private double scale(){
        double max=0;
        for(Point p:points)max=Math.max(max,Math.abs(p.value));
        return max==0?1:max;
    }
    /** Generated HTML only, fixed CSS and numeric coordinates. Viewer must still disable scripts/network/navigation. */
    String html(){
        StringBuilder out=new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\">"
                +"<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">"
                +"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                +"<style>body{font:16px system-ui,sans-serif;color:#222;background:#fff;margin:16px}"
                +"h1{font-size:1.2em;overflow-wrap:anywhere}.plot{position:relative;width:320px;height:180px;background:#f4f6f8}"
                +".scroll{overflow-x:auto}.line,.point,.axis,.bar{position:absolute;background:#225b98}"
                +".line{height:2px;transform-origin:left center}.point{width:6px;height:6px;border-radius:50%}"
                +".axis{background:#666;height:1px;width:100%;top:50%}"
                +".track{height:14px;background:#e8edf2;position:relative;min-width:120px}"
                +".track:before{content:'';position:absolute;left:50%;height:100%;border-left:1px solid #666}"
                +".bar{height:100%}table{border-collapse:collapse;width:100%}th,td{text-align:left;border-bottom:1px solid #ccc;padding:8px;overflow-wrap:anywhere}"
                +"td:last-child{text-align:right;font-variant-numeric:tabular-nums}</style></head><body><h1>");
        out.append(escape(title)).append("</h1>");
        double scale=scale();
        if(kind==Kind.LINE){
            out.append("<div class=\"scroll\" aria-hidden=\"true\"><div class=\"plot\"><span class=\"axis\"></span>");
            double px=0,py=0;
            for(int i=0;i<points.size();i++){
                double x=points.size()==1?160:8+304d*i/(points.size()-1);
                double y=90-80*points.get(i).value/scale;
                if(i>0){
                    double dx=x-px,dy=y-py;
                    out.append("<span class=\"line\" style=\"left:").append(n(px)).append("px;top:").append(n(py))
                        .append("px;width:").append(n(Math.hypot(dx,dy))).append("px;transform:rotate(")
                        .append(n(Math.toDegrees(Math.atan2(dy,dx)))).append("deg)\"></span>");
                }
                out.append("<span class=\"point\" style=\"left:").append(n(x-3)).append("px;top:").append(n(y-3)).append("px\"></span>");
                px=x;py=y;
            }
            out.append("</div></div>");
        }
        out.append("<table><thead><tr><th scope=\"col\">Label</th>");
        if(kind==Kind.BAR)out.append("<th scope=\"col\" aria-label=\"Bar\"></th>");
        out.append("<th scope=\"col\">Value</th></tr></thead><tbody>");
        for(Point p:points){
            out.append("<tr><th scope=\"row\">").append(escape(p.label)).append("</th>");
            if(kind==Kind.BAR){
                double width=50*Math.abs(p.value)/scale;
                out.append("<td aria-hidden=\"true\"><div class=\"track\"><span class=\"bar\" style=\"left:")
                    .append(n(p.value<0?50-width:50)).append("%;width:").append(n(width)).append("%\"></span></div></td>");
            }
            out.append("<td>").append(Double.toString(p.value)).append("</td></tr>");
        }
        return out.append("</tbody></table></body></html>").toString();
    }
}
