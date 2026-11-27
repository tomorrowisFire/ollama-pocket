package com.ollamapocket.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.BooleanSupplier;

/** Private single-turn wire payload only. No transport, route selection, history, or approval bypass. */
final class VisionChatRequest {
    static final int MAX_PROMPT_CHARS=4096;
    static final int MAX_TOKENS=256;
    static final int MAX_REQUEST_BYTES=3*1024*1024;
    private VisionChatRequest() { }

    private static String quotePrompt(String value) {
        if (value==null || value.trim().isEmpty() || value.length()>MAX_PROMPT_CHARS)
            throw new IllegalArgumentException("Vision prompt limit");
        StringBuilder out=new StringBuilder(value.length()+32);
        out.append('"');
        boolean visible=false;
        for(int i=0;i<value.length();) {
            char c=value.charAt(i);
            if (Character.isLowSurrogate(c) || Character.isHighSurrogate(c) &&
                    (i+1>=value.length() || !Character.isLowSurrogate(value.charAt(i+1))))
                throw new IllegalArgumentException("Invalid vision prompt Unicode");
            int cp=value.codePointAt(i); i+=Character.charCount(cp);
            if (Character.isISOControl(cp) && cp!='\n' && cp!='\r' && cp!='\t' ||
                    Character.getType(cp)==Character.FORMAT && cp!=0x200c && cp!=0x200d)
                throw new IllegalArgumentException("Invalid vision prompt control");
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&cp!=0x200c&&cp!=0x200d)visible=true;
            switch(cp) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: out.appendCodePoint(cp);
            }
        }
        if(!visible)throw new IllegalArgumentException("Empty vision prompt");
        return out.append('"').toString();
    }

    static byte[] build(VisionImagePolicy.Prepared image, String prompt, int maxTokens,
                        BooleanSupplier cancelled) throws IOException {
        VisionImagePolicy.check(cancelled);
        if (image==null || !VisionImagePolicy.validOutput(image.width,image.height) ||
                image.size()<4 || image.size()>VisionImagePolicy.MAX_OUTPUT_BYTES ||
                maxTokens<1 || maxTokens>MAX_TOKENS)
            throw new IllegalArgumentException("Vision request bounds");
        String quoted=quotePrompt(prompt);
        String data=Base64.getEncoder().encodeToString(image.copyBytes());
        VisionImagePolicy.check(cancelled);
        // Fixed alias and fixed single-image data URI. No remote URL or filesystem path is accepted.
        String body="{\"model\":\""+VisionModelProfile.ALIAS+"\",\"messages\":[{\"role\":\"user\",\"content\":["+
                "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,"+data+"\"}},"+
                "{\"type\":\"text\",\"text\":"+quoted+"}]}],\"stream\":false,\"temperature\":0,"+
                "\"seed\":42,\"cache_prompt\":false,\"max_tokens\":"+maxTokens+"}";
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_REQUEST_BYTES)throw new IOException("Vision request size");
        VisionImagePolicy.check(cancelled);
        return bytes;
    }
}
