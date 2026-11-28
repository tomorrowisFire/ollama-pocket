package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only, bounded line diff. Original line endings and UTF-16 text are preserved. */
final class ProjectLineDiff {
    static final int MAX_TEXT=32000, MAX_CELLS=500000, MAX_PREVIEW_LINES=200, MAX_PREVIEW_CHARS=16000;
    enum Kind { SAME, REMOVE, ADD }
    static final class Run {
        final Kind kind;
        final int oldStart,newStart,count;
        private Run(Kind kind,int oldStart,int newStart,int count){
            this.kind=kind;this.oldStart=oldStart;this.newStart=newStart;this.count=count;
        }
    }
    static final class Preview {
        final String text;
        final boolean truncated;
        private Preview(String text,boolean truncated){this.text=text;this.truncated=truncated;}
    }
    static final class Result {
        final List<String> previous,current;
        final List<Run> runs;
        final int removed,added;
        final boolean coarse;
        private Result(List<String> previous,List<String> current,List<Run> runs,boolean coarse){
            this.previous=Collections.unmodifiableList(previous);this.current=Collections.unmodifiableList(current);
            this.runs=Collections.unmodifiableList(runs);this.coarse=coarse;
            int minus=0,plus=0;
            for(Run run:runs){if(run.kind==Kind.REMOVE)minus+=run.count;else if(run.kind==Kind.ADD)plus+=run.count;}
            removed=minus;added=plus;
        }
        Preview preview(){
            StringBuilder out=new StringBuilder();int lines=0;
            for(Run run:runs){
                if(run.kind==Kind.SAME)continue;
                boolean add=run.kind==Kind.ADD;
                List<String> source=add?current:previous;
                int start=add?run.newStart:run.oldStart;
                for(int i=0;i<run.count;i++){
                    if(lines>=MAX_PREVIEW_LINES)return new Preview(out.toString(),true);
                    String line=source.get(start+i);
                    String eol=line.endsWith("\r\n")?"CRLF":line.endsWith("\n")?"LF":line.endsWith("\r")?"CR":"EOF";
                    int end=line.length()-(eol.equals("CRLF")?2:eol.equals("EOF")?0:1);
                    String head=(add?"+ ":"- ")+(start+i+1)+" | ";
                    String suffix=" ["+eol+"]\n";
                    int available=MAX_PREVIEW_CHARS-out.length();
                    if(head.length()+end+suffix.length()>available){
                        int cut=Math.min(end,available-head.length()-1);
                        if(cut>0){
                            if(cut<end&&Character.isLowSurrogate(line.charAt(cut)))cut--;
                            out.append(head).append(line,0,cut).append('…');
                        }
                        return new Preview(out.toString(),true);
                    }
                    out.append(head).append(line,0,end).append(suffix);lines++;
                }
            }
            return new Preview(out.toString(),false);
        }
    }
    private ProjectLineDiff(){}
    private static List<String> lines(String value){
        if(value==null||value.length()>MAX_TEXT)throw new IllegalArgumentException("Version text limit");
        List<String> result=new ArrayList<>();int start=0;
        for(int i=0;i<value.length();){
            char c=value.charAt(i++);
            if(Character.isHighSurrogate(c)){
                if(i>=value.length()||!Character.isLowSurrogate(value.charAt(i++)))throw new IllegalArgumentException("Invalid Unicode");
            }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode");
            else if(c=='\r'||c=='\n'){
                if(c=='\r'&&i<value.length()&&value.charAt(i)=='\n')i++;
                result.add(value.substring(start,i));start=i;
            }
        }
        if(start<value.length())result.add(value.substring(start));
        return result;
    }
    private static void append(List<Run> out,Kind kind,int a,int b,int count){
        if(count==0)return;
        int last=out.size()-1;
        if(last>=0&&out.get(last).kind==kind){
            Run previous=out.get(last);
            out.set(last,new Run(kind,previous.oldStart,previous.newStart,previous.count+count));
        }else out.add(new Run(kind,a,b,count));
    }
    static Result compare(String previous,String current){
        List<String> a=lines(previous),b=lines(current);List<Run> runs=new ArrayList<>();
        int prefix=0,ae=a.size(),be=b.size();
        while(prefix<ae&&prefix<be&&a.get(prefix).equals(b.get(prefix)))prefix++;
        while(ae>prefix&&be>prefix&&a.get(ae-1).equals(b.get(be-1))){ae--;be--;}
        append(runs,Kind.SAME,0,0,prefix);
        int n=ae-prefix,m=be-prefix;
        boolean coarse=n>0&&m>0&&(long)(n+1)*(m+1)>MAX_CELLS;
        if(n==0||m==0||coarse){
            append(runs,Kind.REMOVE,prefix,prefix,n);
            append(runs,Kind.ADD,ae,prefix,m);
        }else{
            int stride=m+1;
            int[] lcs=new int[(n+1)*stride];
            for(int i=n-1;i>=0;i--)for(int j=m-1;j>=0;j--){
                int at=i*stride+j;
                lcs[at]=a.get(prefix+i).equals(b.get(prefix+j))?1+lcs[at+stride+1]:Math.max(lcs[at+stride],lcs[at+1]);
            }
            int i=0,j=0;
            while(i<n||j<m){
                if(i<n&&j<m&&a.get(prefix+i).equals(b.get(prefix+j))){append(runs,Kind.SAME,prefix+i,prefix+j,1);i++;j++;}
                else if(i<n&&(j==m||lcs[(i+1)*stride+j]>=lcs[i*stride+j+1])){append(runs,Kind.REMOVE,prefix+i,prefix+j,1);i++;}
                else{append(runs,Kind.ADD,prefix+i,prefix+j,1);j++;}
            }
        }
        append(runs,Kind.SAME,ae,be,a.size()-ae);
        return new Result(a,b,runs,coarse);
    }
}
