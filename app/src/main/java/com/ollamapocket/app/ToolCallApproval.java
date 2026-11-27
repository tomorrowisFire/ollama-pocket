package com.ollamapocket.app;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.LongSupplier;

/** In-memory admission guard. Transport, schema, eligibility and human UI checks remain external. */
final class ToolCallApproval implements AutoCloseable {
    static final int MAX_ATTEMPTS = 16;
    static final int MAX_IDENTITY_CHARS = 32768;
    static final long REVIEW_NANOS = 120_000_000_000L;
    private final String endpoint;
    private final int limit;
    private final LongSupplier clock;
    private int attempts;
    private boolean closed, approved;
    private Review pending;
    private Lease active;

    static final class Review {
        private final byte[] fingerprint;
        private final long issued;
        private Review(byte[] fingerprint, long issued) {
            this.fingerprint = fingerprint;
            this.issued = issued;
        }
    }

    final class Lease implements AutoCloseable {
        private Lease() { }
        @Override public void close() { finish(this); }
    }

    /** Endpoint must already have passed the existing transport's private-LAN validation. */
    ToolCallApproval(String validatedEndpoint, int limit) {
        this(validatedEndpoint, limit, System::nanoTime);
    }
    ToolCallApproval(String validatedEndpoint, int limit, LongSupplier clock) {
        if (validatedEndpoint == null || !validatedEndpoint.matches("[!-~]{1,128}") ||
                limit < 1 || limit > MAX_ATTEMPTS || clock == null)
            throw new IllegalArgumentException("Invalid tool review session");
        this.endpoint = validatedEndpoint;
        this.limit = limit;
        this.clock = clock;
    }

    /** Prepare the exact text to be displayed for review, only after existing schema/eligibility checks. */
    synchronized Review prepare(String name, String identity, String rawArgs, boolean eligibleAndValid) {
        if (closed || active != null || attempts >= limit) throw new IllegalStateException("Tool review unavailable");
        clearReview();
        if (!eligibleAndValid) throw new IllegalArgumentException("Tool is not eligible");
        byte[] hash = fingerprint(endpoint, name, identity, rawArgs);
        pending = new Review(hash, clock.getAsLong());
        return pending;
    }

    /** Must be called only from an explicit user approval action for the displayed Review. */
    synchronized boolean approve(Review review) {
        if (!live(review) || approved) return false;
        approved = true;
        return true;
    }

    synchronized boolean approveExact(Review review,String currentEndpoint,String name,String identity,String rawArgs){
        if(!matchesLive(review,currentEndpoint,name,identity,rawArgs)||approved)return false;
        approved=true;return true;
    }
    synchronized boolean approvedForDispatch(Review review,String currentEndpoint,String name,String identity,String rawArgs){
        return approved&&matchesLive(review,currentEndpoint,name,identity,rawArgs);
    }
    private boolean matchesLive(Review review,String currentEndpoint,String name,String identity,String rawArgs){
        if(!live(review))return false;
        try{
            if(endpoint.equals(currentEndpoint)&&MessageDigest.isEqual(review.fingerprint,fingerprint(endpoint,name,identity,rawArgs))&&fresh(review))return true;
        }catch(IllegalArgumentException ignored){}
        clearReview();return false;
    }

    synchronized void decline(Review review) {
        if (review != null && pending == review) clearReview();
    }

    /** Invalidate any pending approval. The caller must cancel any active transport separately. */
    synchronized void revoke() { clearReview(); }

    /**
     * Atomically consume an approved request after rechecking the refreshed catalog,
     * existing permission and schema. An admitted attempt is never refunded, even
     * if the transport fails or its side effects are uncertain.
     */
    synchronized Lease begin(Review review, String currentEndpoint, String name, String identity,
                             String rawArgs, boolean eligiblePermittedAndValid) {
        if (!live(review) || !approved) return null;
        clearReview(); // A failed recheck also requires a fresh user review.
        if (!eligiblePermittedAndValid || !endpoint.equals(currentEndpoint)) return null;
        final byte[] current;
        try { current = fingerprint(currentEndpoint, name, identity, rawArgs); }
        catch (IllegalArgumentException invalid) { return null; }
        if (!MessageDigest.isEqual(review.fingerprint, current)) return null;
        // Recheck the TTL after hashing the bounded inputs.
        if (!fresh(review)) return null;
        attempts++;
        active = new Lease();
        return active;
    }

    private boolean fresh(Review review) {
        long age = clock.getAsLong() - review.issued;
        return age >= 0 && age < REVIEW_NANOS;
    }

    private boolean live(Review review) {
        if (closed || active != null || attempts >= limit || review == null || pending != review) return false;
        if (!fresh(review)) { clearReview(); return false; }
        return true;
    }

    private void clearReview() { pending = null; approved = false; }
    private synchronized void finish(Lease lease) { if (active == lease) active = null; }
    synchronized boolean inFlight() { return active != null; }
    synchronized int attempted() { return attempts; }
    synchronized int remaining() { return closed ? 0 : limit - attempts; }
    @Override public synchronized void close() {
        closed = true;
        clearReview();
        // Do not pretend an in-flight request stopped. Its actual owner releases its lease.
    }

    private static byte[] fingerprint(String endpoint, String name, String identity, String args) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,128}") || identity == null ||
                identity.isEmpty() || identity.length() > MAX_IDENTITY_CHARS)
            throw new IllegalArgumentException("Tool review identity");
        ToolJsonPolicy.requireObject(args);
        final MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        field(hash, "onhand-tool-review-v1");
        field(hash, endpoint);
        field(hash, name);
        field(hash, identity);
        field(hash, args);
        return hash.digest();
    }

    /** Length framing and UTF-16 code units preserve exact Java string identity without replacement. */
    private static void field(MessageDigest hash, String text) {
        int n = text.length();
        hash.update((byte)(n >>> 24)); hash.update((byte)(n >>> 16));
        hash.update((byte)(n >>> 8)); hash.update((byte)n);
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            hash.update((byte)(c >>> 8)); hash.update((byte)c);
        }
    }
}
