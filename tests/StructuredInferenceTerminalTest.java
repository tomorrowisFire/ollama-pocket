package com.ollamapocket.app;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class StructuredInferenceTerminalTest {
    private static int n;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void reject(Object reason)throws Exception{
        boolean denied=false;
        try{StructuredInferenceTerminal.truncated(reason);}catch(IOException e){denied="CHAT_STREAM_INCOMPLETE".equals(e.getMessage());}
        ok(denied);
    }
    public static void main(String[] args)throws Exception{
        ok(!StructuredInferenceTerminal.truncated(null));
        ok(!StructuredInferenceTerminal.truncated(""));
        ok(!StructuredInferenceTerminal.truncated("stop"));
        ok(StructuredInferenceTerminal.truncated("length"));
        for(String reason:new String[]{"load","unload","error","cancelled","tool_calls","content_filter","eos","STOP","Length"," stop","stop ","\n"," ","null","정상","停止","x".repeat(100000)})reject(reason);
        for(Object reason:new Object[]{true,false,0,1,1.5,List.of("stop"),Map.of("reason","stop"),new StringBuilder("stop"),new Object()})reject(reason);
        // Repeated classifications are deterministic and do not mutate earlier state.
        for(int i=0;i<32;i++){
            ok(StructuredInferenceTerminal.truncated("length"));
            reject("load");
            ok(!StructuredInferenceTerminal.truncated("stop"));
        }
        System.out.println("PASS: "+n+" structured terminal assertions");
    }
}
