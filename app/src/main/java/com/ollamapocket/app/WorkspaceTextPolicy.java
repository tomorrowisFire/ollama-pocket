package com.ollamapocket.app;

/** Immutable bounded literal-text review. Offsets are exact UTF-16 boundaries. */
final class WorkspaceTextPolicy {
    static final int MAX_TEXT=160000, PAGE=4000, MAX_QUERY=160;
    private WorkspaceTextPolicy(){}
    private static void validate(String text,int limit){
        if(text==null||text.isEmpty()||text.length()>limit)throw new IllegalArgumentException("Text limit");
        boolean content=false;
        for(int i=0;i<text.length();){
            int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(cp==0||cp>=0xD800&&cp<=0xDFFF)throw new IllegalArgumentException("Invalid Unicode");
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))content=true;
        }
        if(!content)throw new IllegalArgumentException("Empty text");
    }
    static void validateQuery(String query){validate(query,MAX_QUERY);}
    static Snapshot review(String text){validate(text,MAX_TEXT);return new Snapshot(text);}
    static final class Snapshot {
        final String text;
        private Snapshot(String value){text=value;}
        boolean matches(String current){return text.equals(current);}
        int length(){return text.length();}
        private boolean split(int offset){
            return offset>0&&offset<length()&&Character.isHighSurrogate(text.charAt(offset-1))&&Character.isLowSurrogate(text.charAt(offset));
        }
        private int floor(int offset){return split(offset)?offset-1:offset;}
        private int ceil(int offset){return split(offset)?offset+1:offset;}
        private Window window(int start,int end,int matchStart,int matchEnd){
            if(start<0||end<=start||end>length()||end-start>PAGE||split(start)||split(end))
                throw new IllegalArgumentException("Window bounds");
            if(matchStart!=-1&&(matchStart<start||matchEnd> end||matchEnd<=matchStart||split(matchStart)||split(matchEnd)))
                throw new IllegalArgumentException("Match bounds");
            return new Window(this,start,end,matchStart,matchEnd);
        }
        Window first(){return from(0);}
        private Window from(int start){
            int a=floor(start),b=floor(Math.min(length(),a+PAGE));
            return window(a,b,-1,-1);
        }
        boolean owns(Window value){return value!=null&&value.source==this;}
        private void require(Window value){if(!owns(value))throw new IllegalArgumentException("Different snapshot");}
        Window next(Window value){require(value);return value.end==length()?null:from(value.end);}
        Window previous(Window value){
            require(value);if(value.start==0)return null;
            return window(ceil(Math.max(0,value.start-PAGE)),value.start,-1,-1);
        }
        Window find(String query,Window previous,boolean forward){
            validateQuery(query);
            if(previous!=null){
                require(previous);
                if(previous.matchStart<0||!text.substring(previous.matchStart,previous.matchEnd).equals(query))
                    throw new IllegalArgumentException("Different query");
            }
            int from=previous==null?(forward?0:length()):forward?previous.matchEnd:previous.matchStart-query.length();
            int hit=forward?text.indexOf(query,from):text.lastIndexOf(query,from);
            if(hit<0)return null;
            int a=floor(Math.max(0,hit-600)),b=floor(Math.min(length(),a+PAGE));
            return window(a,b,hit,hit+query.length());
        }
        String copy(Window selected,boolean full,String current){
            if(!matches(current))throw new IllegalArgumentException("Stale snapshot");
            require(selected);
            return full?text:selected.text();
        }
    }
    static final class Window {
        private final Snapshot source;
        final int start,end,matchStart,matchEnd;
        private Window(Snapshot source,int start,int end,int matchStart,int matchEnd){
            this.source=source;this.start=start;this.end=end;this.matchStart=matchStart;this.matchEnd=matchEnd;
        }
        String text(){return source.text.substring(start,end);}
        boolean hasMatch(){return matchStart>=0;}
    }
}
