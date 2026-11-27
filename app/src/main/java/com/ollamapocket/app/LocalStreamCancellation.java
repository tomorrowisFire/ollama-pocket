package com.ollamapocket.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Bounded line reading with opt-in cancellation for interactive local inference. */
final class LocalStreamCancellation {
    private LocalStreamCancellation(){}
    static void check(BooleanSupplier cancelled)throws InterruptedIOException{
        if(cancelled!=null&&(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()))
            throw new InterruptedIOException("CHAT_CANCELLED");
    }
    static String line(BufferedReader reader,int limit,BooleanSupplier cancelled)throws IOException{
        check(cancelled);
        StringBuilder out=new StringBuilder();
        for(int ch;(ch=reader.read())!=-1;){
            if(ch=='\n'){check(cancelled);return out.toString();}
            if(ch=='\r'){
                check(cancelled);reader.mark(1);if(reader.read()!='\n')reader.reset();
                check(cancelled);return out.toString();
            }
            if(out.length()>=limit){check(cancelled);throw new IOException("CHAT_STREAM_LIMIT");}
            out.append((char)ch);
            if((out.length()&255)==0)check(cancelled);
        }
        check(cancelled);
        return out.length()==0?null:out.toString();
    }
}
