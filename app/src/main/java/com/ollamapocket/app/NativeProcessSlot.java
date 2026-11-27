package com.ollamapocket.app;

/** Identity-owned native process. Lifecycle side effects belong to the caller's main thread. */
final class NativeProcessSlot<T> {
    private T current;
    synchronized T current(){return current;}
    synchronized void install(T next){
        if(next==null)throw new IllegalArgumentException("Missing process");
        current=next;
    }
    synchronized T detach(){T old=current;current=null;return old;}
    synchronized boolean retire(T expected){
        if(expected==null||current!=expected)return false;
        current=null;return true;
    }
}
