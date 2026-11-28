package com.ollamapocket.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact local manifest identity and bounded isolated CPU request, no cloud routing. */
final class ReviewedLocalPolicy {
    private static final String SEG="[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
    static final int MAX_OUTPUT=4000;
    private ReviewedLocalPolicy(){}
    static boolean modelTag(String tag){
        return tag!=null&&tag.matches("(?:"+SEG+"/)?"+SEG+":"+SEG)&&!tag.contains("..");
    }
    static String text(String text,int limit){
        if(text==null||text.length()>limit||text.trim().isEmpty())throw new IllegalArgumentException("Local input");
        boolean visible=false;
        for(int i=0;i<text.length();){
            int c=text.codePointAt(i);i+=Character.charCount(c);
            if(c>=0xd800&&c<=0xdfff||c==0x2028||c==0x2029||
                Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'||
                Character.getType(c)==Character.FORMAT&&c!=0x200c&&c!=0x200d)
                throw new IllegalArgumentException("Local text control");
            if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c)&&c!=0x200c&&c!=0x200d)visible=true;
        }
        if(!visible)throw new IllegalArgumentException("Empty input");
        return text;
    }
    static String sha(byte[] data){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder out=new StringBuilder(64);
            for(byte b:bytes)out.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static String sha(String text){return sha(text.getBytes(StandardCharsets.UTF_8));}
    static byte[] readFile(File file,int limit)throws IOException{
        if(!file.equals(file.getCanonicalFile())||!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)||
                file.length()<1||file.length()>limit)throw new IOException("Invalid private file");
        try(InputStream in=Files.newInputStream(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            return read(in,limit);
        }
    }
    static byte[] read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];
        for(;;){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");
            int n=in.read(b,0,Math.min(b.length,limit-out.size()+1));
            if(n<0)break;
            if(n==0||n>limit-out.size())throw new IOException("Local byte limit");
            out.write(b,0,n);
        }
        return out.toByteArray();
    }
    static String utf8(byte[] bytes)throws CharacterCodingException{
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    /** Model identity is the Ollama manifest digest, not a claim that every referenced weight is rehashed. */
    static String manifestDigest(File models,String tag,JSONObject catalog)throws Exception{
        if(models==null||!modelTag(tag)||catalog==null)throw new IOException("Local model identity");
        File root=models.getCanonicalFile();
        if(!models.getAbsoluteFile().equals(root)||!root.isDirectory())throw new IOException("Invalid local model root");
        String[] fields=tag.split(":",2);String name=fields[0];
        String path=(name.contains("/")?name:"library/"+name)+"/"+fields[1];
        File manifest=new File(root,"manifests/registry.ollama.ai/"+path);
        byte[] bytes=readFile(manifest,2*1024*1024);
        String digest=sha(bytes);
        JSONArray entries=catalog.optJSONArray("models");
        if(entries==null||entries.length()>4096)throw new IOException("Local model catalog");
        int matches=0;
        for(int i=0;i<entries.length();i++){
            JSONObject item=entries.optJSONObject(i);if(item==null)continue;
            if(!tag.equals(item.opt("name"))&&!tag.equals(item.opt("model")))continue;
            Object nameValue=item.opt("name"),modelValue=item.opt("model"),d=item.opt("digest");
            if(nameValue!=null&&!tag.equals(nameValue)||modelValue!=null&&!tag.equals(modelValue)||
                    !(d instanceof String)||!digest.equals(((String)d).replaceFirst("^sha256:","")))
                throw new IOException("Installed manifest changed");
            matches++;
        }
        if(matches!=1)throw new IOException("Exact installed model unavailable");
        return digest;
    }
    static JSONObject request(String model,String instruction,String prompt,int tokens,int milli)throws Exception{
        if(!modelTag(model)||tokens<1||tokens>512||milli<0||milli>2000)
            throw new IllegalArgumentException("Local task parameters");
        JSONArray messages=new JSONArray();
        if(instruction!=null&&!instruction.isEmpty())messages.put(new JSONObject().put("role","system").put("content",text(instruction,4096)));
        messages.put(new JSONObject().put("role","user").put("content",text(prompt,4096)));
        return new JSONObject().put("model",model).put("messages",messages).put("stream",true).put("keep_alive",0)
                .put("options",new JSONObject().put("num_ctx",2048).put("num_gpu",0)
                    .put("num_thread",Math.max(1,Math.min(6,Runtime.getRuntime().availableProcessors())))
                    .put("num_predict",tokens).put("temperature",milli/1000.0));
    }
    static final class Result {
        final String text;final boolean truncated;
        Result(String text,boolean truncated){
            if(text==null||text.length()>MAX_OUTPUT)throw new IllegalArgumentException("Local output limit");
            // A result may be empty or incomplete, but its exact evidence must be encodable
            // without replacement characters or hidden control data.
            for(int i=0;i<text.length();){
                int cp=text.codePointAt(i);i+=Character.charCount(cp);
                if(cp>=0xd800&&cp<=0xdfff||Character.isISOControl(cp)&&cp!='\n'&&cp!='\r'&&cp!='\t')
                    throw new IllegalArgumentException("Invalid local result text");
            }
            this.text=text;this.truncated=truncated;
        }
        String evidence(){return sha((truncated?"length\n":"stop\n")+text);}
    }
}
