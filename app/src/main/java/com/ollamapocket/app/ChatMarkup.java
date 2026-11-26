package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, offline, display-only subset of Markdown. Source strings stay unchanged. */
final class ChatMarkup {
    enum Kind { BOLD, ITALIC, CODE, CODE_BLOCK, HEADING, TABLE }
    static final class Span {
        final Kind kind; final int start,end;
        Span(Kind k,int a,int b){kind=k;start=a;end=b;}
    }
    static final class Result {
        final String text;final List<Span> spans;
        Result(String text,List<Span> spans){this.text=text;this.spans=Collections.unmodifiableList(spans);}
    }
    private static final int LIMIT=160000;
    private static final Pattern INLINE=Pattern.compile("`([^`\\n]+)`|\\*\\*([^*\\n]+)\\*\\*|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final Pattern HEADER=Pattern.compile("^(#{1,6})[ \\t]+(.+)$");
    private static final Pattern FENCE=Pattern.compile("^ {0,3}```[ \\t]*[A-Za-z0-9_+.#-]*[ \\t]*$");
    private static final Pattern TABLE_SEPARATOR=Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern BULLET=Pattern.compile("^( {0,3})[-+*][ \\t]+(.+)$");
    private ChatMarkup(){}
    static Result parse(String source){
        if(source==null)source="";
        List<Span> spans=new ArrayList<>();
        if(source.length()>LIMIT)return new Result(source,spans);
        String[] lines=source.replace("\r\n","\n").split("\n",-1);
        StringBuilder out=new StringBuilder();
        for(int i=0;i<lines.length;i++){
            String line=lines[i];
            if(FENCE.matcher(line).matches()){
                int end=i+1;while(end<lines.length&&!lines[end].trim().equals("```"))end++;
                if(end<lines.length){
                    int start=out.length();
                    for(int j=i+1;j<end;j++){if(j>i+1)out.append('\n');out.append(lines[j]);}
                    if(out.length()>start)spans.add(new Span(Kind.CODE_BLOCK,start,out.length()));
                    i=end;
                    if(i<lines.length-1)out.append('\n');
                    continue;
                }
                // Incomplete blocks are preserved literally, not reparsed as inline emphasis.
                int start=out.length();for(int j=i;j<lines.length;j++){if(j>i)out.append('\n');out.append(lines[j]);}
                if(out.length()>start)spans.add(new Span(Kind.CODE_BLOCK,start,out.length()));break;
            }
            if(line.indexOf('|')>=0&&i+1<lines.length&&TABLE_SEPARATOR.matcher(lines[i+1]).matches()){
                int start=out.length();
                out.append(line).append('\n').append(lines[++i]);
                while(i+1<lines.length&&!lines[i+1].trim().isEmpty()&&lines[i+1].indexOf('|')>=0)out.append('\n').append(lines[++i]);
                spans.add(new Span(Kind.TABLE,start,out.length()));
            }else{
                Matcher h=HEADER.matcher(line), b=BULLET.matcher(line);
                if(h.matches()){
                    int start=out.length();inline(h.group(2),out,spans);
                    spans.add(new Span(Kind.HEADING,start,out.length()));
                }else if(b.matches()){
                    out.append(b.group(1)).append("• ");inline(b.group(2),out,spans);
                }else inline(line,out,spans);
            }
            if(i<lines.length-1)out.append('\n');
        }
        return new Result(out.toString(),spans);
    }
    private static void inline(String line,StringBuilder out,List<Span> spans){
        Matcher m=INLINE.matcher(line);int cursor=0;
        while(m.find()){
            // Preserve escaped markers and unmatched delimiters as ordinary text.
            if(m.start()>0&&line.charAt(m.start()-1)=='\\')continue;
            out.append(line,cursor,m.start());int start=out.length();
            Kind kind=m.group(1)!=null?Kind.CODE:m.group(2)!=null?Kind.BOLD:Kind.ITALIC;
            out.append(m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3));
            spans.add(new Span(kind,start,out.length()));cursor=m.end();
        }
        out.append(line,cursor,line.length());
    }
}
