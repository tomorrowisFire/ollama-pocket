package com.ollamapocket.app;

/** Bounded exact single-span comparison; not a line-by-line diff. */
final class ProjectVersionComparison {
    static final int MAX_TEXT=32000;
    private ProjectVersionComparison(){}
    static final class Result {
        final int prefix,suffix;
        final String previous,current;
        private Result(int prefix,int suffix,String previous,String current){
            this.prefix=prefix;this.suffix=suffix;this.previous=previous;this.current=current;
        }
        boolean identical(){return previous.isEmpty()&&current.isEmpty();}
    }
    private static void validate(String value){
        if(value==null||value.length()>MAX_TEXT)throw new IllegalArgumentException("Version text limit");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Invalid Unicode");
            }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode");
        }
    }
    static Result compare(String previous,String current){
        validate(previous);validate(current);
        int prefix=0;
        while(prefix<previous.length()&&prefix<current.length()){
            int a=previous.codePointAt(prefix),b=current.codePointAt(prefix);
            if(a!=b)break;
            prefix+=Character.charCount(a);
        }
        int aEnd=previous.length(),bEnd=current.length();
        while(aEnd>prefix&&bEnd>prefix){
            int a=previous.codePointBefore(aEnd),b=current.codePointBefore(bEnd);
            if(a!=b)break;
            aEnd-=Character.charCount(a);bEnd-=Character.charCount(b);
        }
        return new Result(prefix,previous.length()-aEnd,previous.substring(prefix,aEnd),current.substring(prefix,bEnd));
    }
}
