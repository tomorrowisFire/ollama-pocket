package com.ollamapocket.app;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/** Per-session socket ownership. Closing permanently rejects new sockets. */
final class RpcSocketRegistry implements AutoCloseable {
    private final Set<Socket> sockets=new HashSet<>();
    private final int limit;
    private boolean closed;
    RpcSocketRegistry(int limit){
        if(limit<1||limit>64)throw new IllegalArgumentException("Invalid socket limit");
        this.limit=limit;
    }
    synchronized boolean track(Socket socket){
        if(socket==null)return false;
        if(closed||socket.isClosed()||(!sockets.contains(socket)&&sockets.size()>=limit)){
            closeSocket(socket);return false;
        }
        sockets.add(socket);return true;
    }
    /** Atomically reserve both relay endpoints before any worker is dispatched. */
    synchronized Pair trackPair(Socket first,Socket second){
        if(closed||first==null||second==null||first==second||first.isClosed()||second.isClosed()||
                sockets.contains(first)||sockets.contains(second)||sockets.size()>limit-2){
            // Never close an endpoint already owned by a different live lease.
            if(first!=null&&!sockets.contains(first))closeSocket(first);
            if(second!=null&&second!=first&&!sockets.contains(second))closeSocket(second);
            return null;
        }
        sockets.add(first);sockets.add(second);
        return new Pair(this,first,second);
    }
    static final class Pair implements AutoCloseable {
        private final RpcSocketRegistry owner;
        private final Socket first,second;
        private boolean released;
        private Pair(RpcSocketRegistry owner,Socket first,Socket second){
            this.owner=owner;this.first=first;this.second=second;
        }
        @Override public void close(){owner.release(this);}
    }
    private synchronized void release(Pair pair){
        if(pair.released)return;
        pair.released=true;
        closeSocket(pair.first);closeSocket(pair.second);
        sockets.remove(pair.first);sockets.remove(pair.second);
    }
    synchronized void untrack(Socket socket){sockets.remove(socket);}
    synchronized boolean active(){return !closed;}
    @Override public synchronized void close(){
        closed=true;
        for(Socket socket:sockets)closeSocket(socket);
        sockets.clear();
    }
    private static void closeSocket(Socket socket){
        try{socket.close();}catch(IOException ignored){}
    }
}
