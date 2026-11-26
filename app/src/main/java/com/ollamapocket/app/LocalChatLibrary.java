package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Local, bounded search and export helpers. No storage-provider or network access. */
final class LocalChatLibrary {
    static final int MAX_QUERY=200, MAX_RESULTS=120, MAX_SEARCH_CONVERSATION=1000000, MAX_SEARCH_TOTAL=16000000, MAX_EXPORT=2000000;
    private LocalChatLibrary(){}
    static String query(String text){
        if(text==null)return "";
        String q=Normalizer.normalize(text.trim(),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return q.length()<=MAX_QUERY?q:q.substring(0,MAX_QUERY);
    }
    static boolean matches(String text,String normalizedQuery){
        return text!=null&&!normalizedQuery.isEmpty()&&Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }
    static String excerpt(String source,String q){
        if(source==null||source.isEmpty())return "";
        String clean=source.replace('\n',' ').replace('\r',' ').replace('\t',' ');
        int hit=clean.toLowerCase(Locale.ROOT).indexOf(q);
        int start=Math.max(0,hit-36),end=Math.min(clean.length(),start+116);
        if(start>0&&Character.isLowSurrogate(clean.charAt(start)))start--;
        if(end<clean.length()&&end>0&&Character.isHighSurrogate(clean.charAt(end-1)))end++;
        return (start>0?"…":"")+clean.substring(start,end)+(end<clean.length()?"…":"");
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
