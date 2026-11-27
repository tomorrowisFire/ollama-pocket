package com.ollamapocket.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Bounded credential and abandoned-document handling; no secret values in errors. */
final class PrivacyPolicy {
    static final long PDF_STALE_MS=24L*60*60*1000;
    private PrivacyPolicy(){}
    static boolean validApiKey(String value){
        if(value==null||value.isEmpty()||value.length()>4096)return false;
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<0x21||c>0x7e)return false;}
        return true;
    }
    static boolean validModelId(String model){
        if(model==null||model.isEmpty()||model.length()>200||!model.equals(model.trim()))return false;
        for(int i=0;i<model.length();){
            int cp=model.codePointAt(i);i+=Character.charCount(cp);
            if(cp>=0xd800&&cp<=0xdfff||Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||
                Character.isWhitespace(cp)||Character.isSpaceChar(cp))return false;
        }
        return true;
    }
    static boolean validProviderScope(String provider){
        return "OPENAI".equals(provider)||"GEMINI".equals(provider)||"CLAUDE".equals(provider)||
            "XAI".equals(provider)||"DEEPSEEK".equals(provider)||"OPENROUTER".equals(provider);
    }
    static boolean stalePdfName(String name,long modified,long now){
        return name!=null&&name.matches("pdf-excerpt-[0-9-]{1,40}\\.pdf")&&modified>0&&now>=modified&&now-modified>=PDF_STALE_MS;
    }
    /** Only exact legacy temporary PDF names, direct regular children, older than one day.
        Symlinks and other cache files are never removed. Intended once on process startup. */
    static int cleanAbandonedPdf(File cache,long now)throws IOException{
        if(cache==null||!cache.isDirectory())return 0;
        File root=cache.getCanonicalFile();
        File[] files=root.listFiles();if(files==null)return 0;
        int removed=0,examined=0;
        for(File f:files){
            if(++examined>10000)break;
            if(!f.getName().matches("pdf-excerpt-[0-9-]{1,40}\\.pdf"))continue;
            try{
                BasicFileAttributes attrs=Files.readAttributes(f.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!attrs.isRegularFile()||!stalePdfName(f.getName(),attrs.lastModifiedTime().toMillis(),now))continue;
                if(root.equals(f.getCanonicalFile().getParentFile())&&Files.deleteIfExists(f.toPath()))removed++;
            }catch(IOException|SecurityException ignored){}
        }
        return removed;
    }
}
