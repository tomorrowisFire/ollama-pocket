package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Local, bounded search and export helpers. No storage-provider or network access. */
final class LocalChatLibrary {
    static final int MAX_QUERY=200, MAX_RESULTS=120, MAX_SEARCH_CONVERSATION=1000000, MAX_SEARCH_TOTAL=16000000, MAX_EXPORT=2000000;
    static final int MAX_EXCERPT=320;
    private static final int TITLE_LIMIT=38, EXCERPT_WINDOW=4096, EXCERPT_OVERLAP=512, EXCERPT_STEP=64;
    private LocalChatLibrary(){}
    private static String fold(String text){
        return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
    /** A cut immediately before a low surrogate would split a valid pair. */
    private static int boundary(String text,int cut){
        return cut>0&&cut<text.length()&&Character.isHighSurrogate(text.charAt(cut-1))&&Character.isLowSurrogate(text.charAt(cut))?cut-1:cut;
    }
    private static String flatten(String text){
        return text.replace('\n',' ').replace('\r',' ').replace('\t',' ');
    }
    static String shortTitle(String prompt){
        String title=flatten(prompt==null?"":prompt).trim();
        return title.length()>TITLE_LIMIT?title.substring(0,boundary(title,TITLE_LIMIT))+"…":title;
    }
    static String query(String text){
        if(text==null)return "";
        String q=fold(text.trim());
        return q.length()<=MAX_QUERY?q:q.substring(0,boundary(q,MAX_QUERY));
    }
    static boolean matches(String text,String normalizedQuery){
        return text!=null&&normalizedQuery!=null&&!normalizedQuery.isEmpty()&&fold(text).contains(normalizedQuery);
    }
    private static String verifiedExcerpt(String source,String q,int from,int width){
        int start=boundary(source,Math.max(0,Math.min(source.length(),from)));
        int end=boundary(source,Math.min(source.length(),start+width));
        String part=source.substring(start,end);
        if(!matches(part,q))return "";
        return (start>0?"…":"")+part+(end<source.length()?"…":"");
    }
    static String excerpt(String source,String q){
        if(source==null||source.isEmpty()||source.length()>MAX_SEARCH_CONVERSATION||q==null||q.isEmpty()||q.length()>MAX_QUERY||Thread.currentThread().isInterrupted())return "";
        String clean=flatten(source);
        int hit=fold(clean).indexOf(q);
        if(hit<0)return "";
        int width=Math.max(116,Math.min(MAX_EXCERPT,q.length()+48));
        // Normalized offsets are only hints: compatibility expansion, combining
        // marks and case conversion can move them relative to the source.
        String result=verifiedExcerpt(clean,q,Math.min(hit,clean.length()-1)-36,width);
        if(!result.isEmpty())return result;
        // Check bounded overlapping windows only when the cheap hint fails.
        // Never substitute normalized characters for the original source text.
        for(int base=0;base<clean.length();base+=EXCERPT_WINDOW-EXCERPT_OVERLAP){
            if(Thread.currentThread().isInterrupted())return "";
            int start=boundary(clean,base),end=boundary(clean,Math.min(clean.length(),base+EXCERPT_WINDOW));
            int local=fold(clean.substring(start,end)).indexOf(q);
            if(local<0)continue;
            result=verifiedExcerpt(clean,q,Math.max(start,Math.min(end-1,start+local)-36),width);
            if(!result.isEmpty())return result;
            for(int i=start;i<end;i+=EXCERPT_STEP){
                if(Thread.currentThread().isInterrupted())return "";
                result=verifiedExcerpt(clean,q,i,MAX_EXCERPT);
                if(!result.isEmpty())return result;
            }
        }
        // The matching message is still navigable; omit an unverifiable preview.
        return "";
    }
    static String digest(String text){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte b:bytes){int v=b&255;hex.append("0123456789abcdef".charAt(v>>>4)).append("0123456789abcdef".charAt(v&15));}
            return hex.toString();
        }catch(Exception e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    static String markdown(String model,List<LocalChatTurns.Message> messages,String user,String assistant,String other){
        StringBuilder b=new StringBuilder("# OnHand AI\n\n").append(model.replace('\n',' ').replace('\r',' ')).append("\n\n");
        for(LocalChatTurns.Message message:messages){
            if(message==null)continue;
            String role="user".equals(message.role)?user:"assistant".equals(message.role)?assistant:other;
            String content=message.content==null?"":message.content;
            if((long)b.length()+content.length()+role.length()+10>MAX_EXPORT)throw new IllegalArgumentException("Export too large");
            b.append("## ").append(role).append("\n\n").append(content).append("\n\n");
        }
        return b.toString();
    }
}
