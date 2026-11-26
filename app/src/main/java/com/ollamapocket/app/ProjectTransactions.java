package com.ollamapocket.app;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Store-independent optimistic transaction, with no implicit creation over corrupt bytes. */
final class ProjectTransactions<T> {
    interface Backend {
        /** Null means absent, never unreadable or corrupt. */
        byte[] read() throws IOException;
        void write(byte[] bytes) throws IOException;
    }
    interface Codec<T> {
        T decode(byte[] bytes) throws Exception;
        byte[] encode(T value) throws Exception;
        long revision(T value);
    }
    interface Mutation<T> { T apply(T current) throws Exception; }
    static final class Snapshot<T> {
        final T value;
        final long revision;
        private final byte[] bytes;
        private Snapshot(T value,long revision,byte[] bytes){
            this.value=value;this.revision=revision;this.bytes=bytes==null?null:bytes.clone();
        }
    }
    enum Failure { STALE, INVALID, NO_CHANGE, WRITE_UNCERTAIN }
    static final class TransactionFailure extends IOException {
        final Failure reason;
        TransactionFailure(Failure reason,Exception cause){super(reason.name(),cause);this.reason=reason;}
    }
    private final Backend backend;
    private final Codec<T> codec;
    private final Object lock;
    private final T empty;
    private final int maxBytes;
    ProjectTransactions(Backend backend,Codec<T> codec,Object lock,T empty,int maxBytes){
        this.backend=Objects.requireNonNull(backend);this.codec=Objects.requireNonNull(codec);
        this.lock=Objects.requireNonNull(lock);this.empty=Objects.requireNonNull(empty);
        if(maxBytes<=0||codec.revision(empty)!=0)throw new IllegalArgumentException("Invalid empty store");
        this.maxBytes=maxBytes;
    }
    private Snapshot<T> readLocked()throws Exception{
        byte[] bytes=backend.read();
        if(bytes==null)return new Snapshot<>(empty,0,null);
        if(bytes.length==0||bytes.length>maxBytes)throw new IOException("Store size");
        T value=codec.decode(bytes);
        long revision=codec.revision(value);
        if(revision<1||!Arrays.equals(bytes,codec.encode(value)))throw new IOException("Noncanonical store");
        return new Snapshot<>(value,revision,bytes);
    }
    Snapshot<T> read()throws Exception{synchronized(lock){return readLocked();}}
    Snapshot<T> commit(Snapshot<T> reviewed,Mutation<T> mutation)throws Exception{
        synchronized(lock){
            Snapshot<T> current=readLocked();
            if(reviewed==null||reviewed.revision!=current.revision||!Arrays.equals(reviewed.bytes,current.bytes))
                throw new TransactionFailure(Failure.STALE,null);
            T candidate;byte[] bytes;
            try{
                if(current.revision==Long.MAX_VALUE)throw new IllegalArgumentException("Revision limit");
                candidate=Objects.requireNonNull(mutation.apply(current.value));
                long next=codec.revision(candidate);
                bytes=codec.encode(candidate);
                if(next==current.revision&&Arrays.equals(bytes,codec.encode(current.value)))
                    throw new TransactionFailure(Failure.NO_CHANGE,null);
                if(next!=current.revision+1||bytes==null||bytes.length==0||bytes.length>maxBytes)
                    throw new IllegalArgumentException("Candidate limit/revision");
                T validated=codec.decode(bytes);
                if(codec.revision(validated)!=next||!Arrays.equals(bytes,codec.encode(validated)))
                    throw new IllegalArgumentException("Candidate round trip");
            }catch(TransactionFailure e){throw e;}
            catch(Exception e){throw new TransactionFailure(Failure.INVALID,e);}
            // A backend failure may occur after data reached disk. Do not claim rollback.
            try{
                backend.write(bytes.clone());
                Snapshot<T> saved=readLocked();
                if(saved.revision!=current.revision+1||!Arrays.equals(saved.bytes,bytes))
                    throw new IOException("Post-write mismatch");
                return saved;
            }catch(Exception e){throw new TransactionFailure(Failure.WRITE_UNCERTAIN,e);}
        }
    }
}
