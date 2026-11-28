package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded display-only Markdown table cells; preserve inline code and escaped-pipe meaning. */
final class MarkdownTableCells {
    private MarkdownTableCells(){}
    static List<String> split(String input,int maxLine,int maxColumns){
        if(input==null||maxLine<1||maxLine>4000||maxColumns<1||maxColumns>10||input.length()>maxLine)
            return Collections.emptyList();
        String s=input.trim();
        int i=s.startsWith("|")?1:0;
        List<String> out=new ArrayList<>();
        StringBuilder cell=new StringBuilder();
        boolean trailing=false;
        while(i<s.length()){
            char c=s.charAt(i);
            trailing=false;
            if(c=='\\'){
                int end=runEnd(s,i,'\\');
                boolean odd=(end-i)%2!=0;
                if(odd&&end<s.length()&&s.charAt(end)=='|'){
                    cell.append(s,i,end-1).append('|');i=end+1;continue;
                }
                cell.append(s,i,end);i=end;
                // Outside code, a backslash can escape the first backtick of a run.
                if(odd&&i<s.length()&&s.charAt(i)=='`'){cell.append('`');i++;}
                continue;
            }
            if(c=='`'){
                int end=runEnd(s,i,'`'),close=closingRun(s,end,end-i);
                if(close>=0){
                    int after=close+end-i;
                    appendCode(cell,s,i,after);i=after;
                }else{cell.append(s,i,end);i=end;}
                continue;
            }
            if(c=='|'){
                out.add(cell.toString().trim());cell.setLength(0);
                if(out.size()>maxColumns)return Collections.emptyList();
                trailing=++i==s.length();
            }else{cell.append(c);i++;}
        }
        if(!trailing)out.add(cell.toString().trim());
        return out.size()<=maxColumns?Collections.unmodifiableList(out):Collections.emptyList();
    }
    private static int runEnd(String s,int start,char marker){
        int end=start+1;
        while(end<s.length()&&s.charAt(end)==marker)end++;
        return end;
    }
    /** An inline code delimiter closes only on an exact-width backtick run. */
    private static int closingRun(String s,int from,int width){
        for(int i=from;i<s.length();){
            if(s.charAt(i)!='`'){i++;continue;}
            int end=runEnd(s,i,'`');
            if(end-i==width)return i;
            i=end;
        }
        return -1;
    }
    /** As in table text, an odd protecting slash before a pipe is display escaping, not a new cell. */
    private static void appendCode(StringBuilder out,String s,int start,int end){
        for(int i=start;i<end;){
            if(s.charAt(i)!='\\'){out.append(s.charAt(i++));continue;}
            int next=runEnd(s,i,'\\');
            if(next<end&&s.charAt(next)=='|'&&(next-i)%2!=0){
                out.append(s,i,next-1).append('|');i=next+1;
            }else{out.append(s,i,Math.min(next,end));i=Math.min(next,end);}
        }
    }
}
