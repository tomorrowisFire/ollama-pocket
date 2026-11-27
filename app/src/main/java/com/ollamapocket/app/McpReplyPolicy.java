package com.ollamapocket.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Inert JSON-RPC replies from an already approved request. No networking or server request execution. */
final class McpReplyPolicy {
    static final int MAX_BYTES=524288,MAX_LINES=4096,MAX_EVENTS=64;
    static final long MAX_NANOS=30_000_000_000L;
    enum Kind { RESULT, TOOL_ERROR, RPC_ERROR }
    static final class Reply {
        final Kind kind;
        final JSONObject payload;
        private Reply(Kind kind,JSONObject payload){this.kind=kind;this.payload=payload;}
    }
    private McpReplyPolicy(){}
    static Reply read(InputStream input,String contentType,int expectedId,boolean toolCall,
                      BooleanSupplier cancelled)throws Exception{
        return read(input,contentType,expectedId,toolCall,cancelled,System::nanoTime);
    }
    /** Caller owns the stream and must enforce socket timeout and interrupt/close on transport cancellation. */
    static Reply read(InputStream input,String contentType,int expectedId,boolean toolCall,
                      BooleanSupplier cancelled,LongSupplier clock)throws Exception{
        if(input==null||contentType==null||expectedId<1||clock==null)throw new IllegalArgumentException("MCP reply identity");
        String mime=contentType.toLowerCase(Locale.ROOT).split(";",2)[0].trim();
        Bounded b=new Bounded(input,cancelled,clock);
        if("application/json".equals(mime)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[8192];
            for(;;){int n=b.read(buf);if(n<0)break;out.write(buf,0,n);}
            Reply reply=parse(utf8(out.toByteArray()),expectedId,toolCall,false);
            b.check();return reply;
        }
        if(!"text/event-stream".equals(mime))throw new IOException("Unsupported MCP reply media");
        StringBuilder data=new StringBuilder();
        int lines=0,events=0;
        for(;;){
            String line=b.line();
            if(line==null)break;
            if(++lines>MAX_LINES)throw new IOException("MCP SSE line limit");
            if(line.isEmpty()){
                if(data.length()==0)continue;
                if(++events>MAX_EVENTS)throw new IOException("MCP SSE event limit");
                Reply result=parse(data.toString(),expectedId,toolCall,true);
                data.setLength(0);
                if(result!=null){b.check();return result;}
                continue;
            }
            if(line.startsWith(":"))continue;
            int sep=line.indexOf(':');
            String field=sep<0?line:line.substring(0,sep),value=sep<0?"":line.substring(sep+1);
            if(value.startsWith(" "))value=value.substring(1);
            if("data".equals(field)){
                if(data.length()>0)data.append('\n');
                if((long)data.length()+value.length()>ToolJsonPolicy.MAX_REPLY_CHARS)throw new IOException("MCP SSE data limit");
                data.append(value);
            }
            // event/id/retry/unknown fields are inert; no resumption or replay.
        }
        throw new EOFException("Incomplete MCP response");
    }
    private static String utf8(byte[] bytes)throws CharacterCodingException{
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static boolean integer(Object value){
        return value instanceof Integer||value instanceof Long;
    }
    private static Reply parse(String raw,int expectedId,boolean toolCall,boolean allowNotification)throws Exception{
        ToolJsonPolicy.requireReplyObject(raw);
        JSONObject o=new JSONObject(raw);
        if(!"2.0".equals(o.opt("jsonrpc")))throw new IOException("Invalid MCP RPC version");
        if(o.has("method")){
            Object method=o.opt("method");
            if(!allowNotification||o.has("id")||o.has("result")||o.has("error")||
                    !(method instanceof String)||!((String)method).matches("notifications/[A-Za-z0-9_./-]{1,128}")||
                    (o.has("params")&&!(o.opt("params") instanceof JSONObject)))
                throw new IOException("Server requests unsupported");
            // Notifications are never acted on, interpreted as approval or injected into prompts.
            return null;
        }
        Object id=o.opt("id");
        if(!integer(id)||((Number)id).longValue()!=expectedId||
                o.length()!=3||o.has("result")==o.has("error"))
            throw new IOException("MCP response identity");
        if(o.has("error")){
            JSONObject error=o.optJSONObject("error");
            if(error==null||!integer(error.opt("code"))||!(error.opt("message") instanceof String)||
                    ((String)error.opt("message")).length()>4096)
                throw new IOException("Invalid MCP RPC error");
            return new Reply(Kind.RPC_ERROR,error);
        }
        JSONObject result=o.optJSONObject("result");
        if(result==null)throw new IOException("Invalid MCP result");
        if(toolCall&&result.has("isError")){
            if(!(result.opt("isError") instanceof Boolean))throw new IOException("Invalid MCP tool status");
            if(Boolean.TRUE.equals(result.opt("isError")))return new Reply(Kind.TOOL_ERROR,result);
        }
        return new Reply(Kind.RESULT,result);
    }
    private static final class Bounded {
        final InputStream input;
        final BooleanSupplier cancelled;
        final LongSupplier clock;
        final long started;
        int count;
        boolean skipLF;
        Bounded(InputStream input,BooleanSupplier cancelled,LongSupplier clock){
            this.input=new BufferedInputStream(input,4096);this.cancelled=cancelled;this.clock=clock;started=clock.getAsLong();
        }
        void check()throws IOException{
            long age=clock.getAsLong()-started;
            if(Thread.currentThread().isInterrupted()||cancelled!=null&&cancelled.getAsBoolean())
                throw new InterruptedIOException("MCP cancelled");
            if(age<0||age>=MAX_NANOS)throw new InterruptedIOException("MCP reply deadline");
        }
        int next()throws IOException{
            check();int c=input.read();check();
            if(c>=0&&++count>MAX_BYTES)throw new IOException("MCP reply size");
            return c;
        }
        int read(byte[] buf)throws IOException{
            check();
            int n=input.read(buf,0,Math.min(buf.length,MAX_BYTES-count+1));
            check();
            if(n==0)throw new IOException("MCP empty read");
            if(n>0&&(count+=n)>MAX_BYTES)throw new IOException("MCP reply size");
            return n;
        }
        String line()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            int c=next();
            if(skipLF){skipLF=false;if(c=='\n')c=next();}
            if(c<0)return null;
            for(;;){
                if(c=='\r'||c=='\n'){skipLF=c=='\r';return utf8(out.toByteArray());}
                out.write(c);
                c=next();
                if(c<0)throw new EOFException("Incomplete MCP SSE line");
            }
        }
    }
}
