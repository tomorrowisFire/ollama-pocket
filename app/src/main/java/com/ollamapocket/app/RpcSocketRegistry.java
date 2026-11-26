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
