package com.ollamapocket.app;
import java.util.List;

/** Review the actual selected message content. Never persist a transfer approval or its preview. */
final class TransferReview {
    private static final int MAX_PREVIEW=100000;
    private TransferReview(){}
    static String fingerprint(String scope,String model,String engine,String payload){
        StringBuilder data=new StringBuilder();
        for(String part:new String[]{scope,model,engine,payload}){
            if(part==null)throw new IllegalArgumentException("Missing transfer context");
            data.append(part.length()).append(':').append(part);
        }
        return LocalChatLibrary.digest(data.toString());
    }
    static String text(List<LocalChatTurns.Message> messages,String user,String assistant,String other){
        StringBuilder out=new StringBuilder();int count=0;
        for(LocalChatTurns.Message m:messages){
            if(m==null||m.content==null)throw new IllegalArgumentException("Missing transfer message");
            String label="user".equals(m.role)?user:"assistant".equals(m.role)?assistant:other;
            if((long)out.length()+label.length()+m.content.length()+32>MAX_PREVIEW)
                throw new IllegalArgumentException("Transfer preview exceeds limit");
            out.append('\n').append(++count).append(". ").append(label).append('\n').append(m.content).append('\n');
        }
        if(count==0)throw new IllegalArgumentException("No transfer messages");
        return out.toString();
    }
}
