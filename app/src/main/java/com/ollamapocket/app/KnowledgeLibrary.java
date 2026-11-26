package com.ollamapocket.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable, local-only knowledge snapshots. Literal search is not semantic or factual verification. */
final class KnowledgeLibrary {
    static final int MAX_SOURCES=24,MAX_TEXT=32000,MAX_QUERY=120,MAX_HITS=40,MAX_BYTES=2*1024*1024;
    private static final int MAGIC=0x4f484b31,SCHEMA=1,HASH_BYTES=32;
    private KnowledgeLibrary(){}
    private static IllegalArgumentException invalid(String msg){return new IllegalArgumentException(msg);}
    private static String checked(String s,int max){
        if(s==null||s.length()>max||s.isEmpty())throw invalid("Text limit");
        boolean visible=false;
        for(int i=0;i<s.length();){
            char c=s.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(i+1)))throw invalid("Invalid Unicode");
            }else if(Character.isLowSurrogate(c))throw invalid("Invalid Unicode");
            int cp=s.codePointAt(i);i+=Character.charCount(cp);
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&!Character.isISOControl(cp)&&Character.getType(cp)!=Character.FORMAT)visible=true;
        }
        if(!visible)throw invalid("Blank text");
        return s;
    }
    static final class Source {
        final String id,title,text;
        private Source(String id,String title,String text){
            if(id==null||!id.matches("[A-Za-z0-9_-]{1,64}"))throw invalid("Invalid source ID");
            this.id=id;this.title=ProjectLibrary.title(title);this.text=checked(text,MAX_TEXT);
        }
    }
    static final class State {
        final long revision;final List<Source> sources;
        private State(long revision,List<Source> sources){
            if(revision<0||sources.size()>MAX_SOURCES||(revision==0&&!sources.isEmpty()))throw invalid("State limit");
            Set<String> ids=new HashSet<>();
            for(Source s:sources)if(s==null||!ids.add(s.id))throw invalid("Duplicate source");
            this.revision=revision;this.sources=Collections.unmodifiableList(new ArrayList<>(sources));
        }
        Source source(String id){
            for(Source s:sources)if(s.id.equals(id))return s;
            throw invalid("Unknown source");
        }
    }
    static State empty(){return new State(0,Collections.emptyList());}
    private static long next(State s){
        if(s.revision==Long.MAX_VALUE)throw invalid("Revision limit");
        return s.revision+1;
    }
    static State add(State s,String id,String title,String text){
        if(s.sources.size()>=MAX_SOURCES)throw invalid("Source limit");
        for(Source old:s.sources)if(old.id.equals(id))throw invalid("Duplicate source");
        List<Source> sources=new ArrayList<>(s.sources);sources.add(new Source(id,title,text));
        return new State(next(s),sources);
    }
    static State remove(State s,String id){
        List<Source> sources=new ArrayList<>(s.sources);sources.remove(s.source(id));
        return new State(next(s),sources);
    }
    /** Inclusive 1-based UTF-16 coordinates in this exact stored snapshot, not in the original file. */
    static final class Hit {
        final String id,title,match;
        final int start,end;
        private Hit(Source s,int at,int length){
            id=s.id;title=s.title;start=at+1;end=at+length;match=s.text.substring(at,at+length);
        }
    }
    static List<Hit> search(State state,String query){
        checked(query,MAX_QUERY);
        List<Hit> hits=new ArrayList<>();
        for(Source s:state.sources){
            for(int from=0;;){
                int at=s.text.indexOf(query,from);
                if(at<0)break;
                hits.add(new Hit(s,at,query.length()));
                if(hits.size()==MAX_HITS)return Collections.unmodifiableList(hits);
                from=at+query.length();
            }
        }
        return Collections.unmodifiableList(hits);
    }
    private static byte[] hash(byte[] b)throws Exception{return MessageDigest.getInstance("SHA-256").digest(b);}
    private static void writeText(DataOutputStream out,String s)throws IOException{
        byte[] bytes=s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);out.write(bytes);
    }
    private static String readText(DataInputStream in,int max)throws IOException{
        int n=in.readInt();
        if(n<=0||n>max*4||n>in.available())throw new IOException("String length");
        byte[] bytes=new byte[n];in.readFully(bytes);
        String s=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if(s.length()>max)throw new IOException("String limit");
        return s;
    }
    static byte[] encode(State state)throws Exception{
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(buffer);
        out.writeInt(MAGIC);out.writeInt(SCHEMA);out.writeLong(state.revision);out.writeInt(state.sources.size());
        for(Source s:state.sources){
            writeText(out,s.id);writeText(out,s.title);writeText(out,s.text);
            if(buffer.size()>MAX_BYTES-HASH_BYTES)throw invalid("Store byte limit");
        }
        out.flush();byte[] payload=buffer.toByteArray();
        out.write(hash(payload));out.flush();return buffer.toByteArray();
    }
    static State decode(byte[] bytes)throws Exception{
        if(bytes==null||bytes.length<20+HASH_BYTES||bytes.length>MAX_BYTES)throw invalid("Store size");
        int end=bytes.length-HASH_BYTES;
        byte[] payload=Arrays.copyOf(bytes,end);
        if(!MessageDigest.isEqual(hash(payload),Arrays.copyOfRange(bytes,end,bytes.length)))throw invalid("Checksum");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload));
        if(in.readInt()!=MAGIC||in.readInt()!=SCHEMA)throw invalid("Schema");
        long revision=in.readLong();int count=in.readInt();
        if(revision<1||count<0||count>MAX_SOURCES)throw invalid("State limit");
        List<Source> sources=new ArrayList<>();
        for(int i=0;i<count;i++)sources.add(new Source(readText(in,64),readText(in,80),readText(in,MAX_TEXT)));
        if(in.available()!=0)throw invalid("Trailing bytes");
        State state=new State(revision,sources);
        if(!Arrays.equals(bytes,encode(state)))throw invalid("Noncanonical store");
        return state;
    }
    static final ProjectTransactions.Codec<State> CODEC=new ProjectTransactions.Codec<State>(){
        public State decode(byte[] bytes)throws Exception{return KnowledgeLibrary.decode(bytes);}
        public byte[] encode(State state)throws Exception{return KnowledgeLibrary.encode(state);}
        public long revision(State state){return state.revision;}
    };
}
