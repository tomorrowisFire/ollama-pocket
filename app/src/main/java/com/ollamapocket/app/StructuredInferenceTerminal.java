package com.ollamapocket.app;

import java.io.IOException;

/** Completion reason for structured CPU tasks; the transport separately requires boolean done. */
final class StructuredInferenceTerminal {
    private StructuredInferenceTerminal(){}
    static boolean truncated(Object reason)throws IOException{
        // Older local engines may omit done_reason. Do not coerce typed metadata.
        if(reason==null)return false;
        if(!(reason instanceof String))throw new IOException("CHAT_STREAM_INCOMPLETE");
        String value=(String)reason;
        if(value.isEmpty()||"stop".equals(value))return false;
        if("length".equals(value))return true;
        throw new IOException("CHAT_STREAM_INCOMPLETE");
    }
}
