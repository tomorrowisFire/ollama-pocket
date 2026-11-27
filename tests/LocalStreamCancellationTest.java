package com.ollamapocket.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringReader;

public final class LocalStreamCancellationTest {
    private static int n;
    private interface Action{void run()throws Exception;}
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static final class CounterReader extends BufferedReader{
        int reads;
        CounterReader(String data){super(new StringReader(data));}
        @Override public int read()throws IOException{reads++;return super.read();}
    }
    private static void cancelled(Action action)throws Exception{
        boolean caught=false;
        try{action.run();}catch(InterruptedIOException e){caught="CHAT_CANCELLED".equals(e.getMessage());}
        ok(caught);
    }
    public static void main(String[] args)throws Exception{
        CounterReader before=new CounterReader("hello\n");
        cancelled(()->LocalStreamCancellation.line(before,100,()->true));ok(before.reads==0);
        for(int threshold:new int[]{1,255,256,257,511,512,513,1000}){
            CounterReader mid=new CounterReader("x".repeat(2048)+"\n");
            cancelled(()->LocalStreamCancellation.line(mid,3000,()->mid.reads>=threshold));
            ok(mid.reads>=threshold&&mid.reads-threshold<256);
        }
        for(String input:new String[]{"\n","a\n","a\r\n","a\r","a",""}){
            CounterReader end=new CounterReader(input);
            cancelled(()->LocalStreamCancellation.line(end,10,()->end.reads>=1));
        }
        CounterReader crlf=new CounterReader("a\r\nb");
        cancelled(()->LocalStreamCancellation.line(crlf,10,()->crlf.reads>=3));ok(crlf.reads==3);
        CounterReader normal=new CounterReader("a\r\nb\rc\n\n");
        for(String expected:new String[]{"a","b","c",""})ok(expected.equals(LocalStreamCancellation.line(normal,1,()->false)));
        ok(LocalStreamCancellation.line(normal,1,()->false)==null);
        ok("한국어".equals(LocalStreamCancellation.line(new CounterReader("한국어"),3,()->false)));
        boolean bounded=false;
        try{LocalStreamCancellation.line(new CounterReader("ab"),1,()->false);}catch(IOException e){bounded="CHAT_STREAM_LIMIT".equals(e.getMessage());}
        ok(bounded);
        Thread.currentThread().interrupt();
        try{
            cancelled(()->LocalStreamCancellation.line(new CounterReader("hello"),10,()->false));
            ok(Thread.currentThread().isInterrupted());
            ok("hello".equals(LocalStreamCancellation.line(new CounterReader("hello"),10,null)));
        }finally{Thread.interrupted();}
        System.out.println("PASS: "+n+" local stream cancellation assertions");
    }
}
