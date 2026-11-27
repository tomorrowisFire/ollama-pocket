package com.ollamapocket.app;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Worker-side, private one-shot request ownership. Does not start a server or send data. */
final class VisionTurnReview implements AutoCloseable {
    static final long TTL_NANOS=120_000_000_000L;
    private final LongSupplier clock;
    private Review pending;
    private Lease active;
    private boolean approved, closed;

    /** Generation must come from the actual owned local server after its capability/identity checks. */
    static final class Backend {
        final String generation;
        Backend(String generation,String commit,String revision,String weights,long weightBytes,
                String projector,long projectorBytes,boolean visionVerified){
            if(generation==null||!generation.matches("[A-Za-z0-9_-]{16,128}")||!visionVerified||
                    !VisionModelProfile.BACKEND_COMMIT.equals(commit)||
                    !VisionModelProfile.matches(revision,weights,weightBytes,projector,projectorBytes))
                throw new IllegalArgumentException("Unverified vision backend");
            this.generation=generation;
        }
    }
    static final class Review {
        final String imageSha256;
        final int width,height,maxTokens;
        private final String generation;
        private final byte[] fingerprint;
        private final long issued;
        private Review(VisionImagePolicy.Prepared image,int tokens,String generation,byte[] fingerprint,long issued){
            this.imageSha256=image.sha256;this.width=image.width;this.height=image.height;this.maxTokens=tokens;
            this.generation=generation;this.fingerprint=fingerprint;this.issued=issued;
        }
    }
    final class Lease implements AutoCloseable {
        private byte[] payload;
        private final String generation;
        private final int tokens;
        private boolean revoked;
        private Lease(byte[] payload,String generation,int tokens){
            this.payload=payload;this.generation=generation;this.tokens=tokens;
        }
        byte[] takePayload(String expectedGeneration,int expectedTokens,BooleanSupplier cancelled)throws IOException{
            synchronized(VisionTurnReview.this){
                if(!generation.equals(expectedGeneration)||tokens!=expectedTokens)throw new IOException("Visual transport identity changed");
                return takePayload(cancelled);
            }
        }
        /** Moves the bytes to the transport owner once. Owner must clear its buffer after use. */
        byte[] takePayload(BooleanSupplier cancelled)throws IOException{
            synchronized(VisionTurnReview.this){
                VisionImagePolicy.check(cancelled);
                if(active!=this||closed||revoked||payload==null)throw new IOException("Vision request unavailable");
                byte[] result=payload;payload=null;return result;
            }
        }
        private void revoke(){revoked=true;if(payload!=null){Arrays.fill(payload,(byte)0);payload=null;}}
        @Override public void close(){synchronized(VisionTurnReview.this){revoke();if(active==this)active=null;}}
    }
    VisionTurnReview(){this(System::nanoTime);}
    VisionTurnReview(LongSupplier clock){if(clock==null)throw new IllegalArgumentException("Clock");this.clock=clock;}
    /** Prepare and later begin on a worker; UI must display exactly this image and prompt for approval. */
    synchronized Review prepare(Backend backend,VisionImagePolicy.Prepared image,String prompt,int tokens,
                                BooleanSupplier cancelled)throws IOException{
        if(closed||active!=null)throw new IllegalStateException("Vision review unavailable");
        clear();
        if(backend==null)throw new IllegalArgumentException("Verified backend required");
        byte[] data=VisionChatRequest.build(image,prompt,tokens,cancelled);
        try{
            byte[] fingerprint=hash(data);
            VisionImagePolicy.check(cancelled);
            pending=new Review(image,tokens,backend.generation,fingerprint,clock.getAsLong());
            return pending;
        }finally{Arrays.fill(data,(byte)0);}
    }
    synchronized boolean approve(Review review){
        if(!live(review)||approved)return false;
        approved=true;return true;
    }
    synchronized void decline(Review review){if(review!=null&&pending==review)clear();}
    /** Rebuild from the current input; changed content, stale server, or cancelled review cannot send. */
    synchronized Lease begin(Review review,Backend backend,VisionImagePolicy.Prepared image,String prompt,
                             int tokens,BooleanSupplier cancelled)throws IOException{
        if(!live(review)||!approved)return null;
        clear(); // one-shot even when final validation fails
        VisionImagePolicy.check(cancelled);
        if(backend==null||!review.generation.equals(backend.generation)||image==null||
                !review.imageSha256.equals(image.sha256)||review.width!=image.width||review.height!=image.height||
                tokens!=review.maxTokens)return null;
        byte[] data=VisionChatRequest.build(image,prompt,tokens,cancelled);
        try{
            if(!MessageDigest.isEqual(review.fingerprint,hash(data))||!fresh(review))return null;
            VisionImagePolicy.check(cancelled);
            active=new Lease(data,review.generation,review.maxTokens);data=null;return active;
        }finally{if(data!=null)Arrays.fill(data,(byte)0);}
    }
    /** Caller cancels actual transport separately; never release a live owner falsely. */
    synchronized void cancel(){clear();if(active!=null)active.revoke();}
    synchronized boolean inFlight(){return active!=null;}
    @Override public synchronized void close(){closed=true;cancel();}
    private void clear(){pending=null;approved=false;}
    private boolean fresh(Review r){long age=clock.getAsLong()-r.issued;return age>=0&&age<TTL_NANOS;}
    private boolean live(Review r){
        if(closed||active!=null||r==null||pending!=r)return false;
        if(!fresh(r)){clear();return false;}return true;
    }
    private static byte[] hash(byte[] data){
        try{return MessageDigest.getInstance("SHA-256").digest(data);}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
