package com.ollamapocket.app;
import java.net.Socket;

public final class RpcSocketRegistryTest {
    private static int n;
    static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    public static void main(String[] args)throws Exception{
        RpcSocketRegistry first=new RpcSocketRegistry(2);
        Socket one=new Socket(),two=new Socket(),over=new Socket();
        ok(first.track(one));ok(first.track(two));ok(first.track(one));ok(!first.track(over)&&over.isClosed());
        first.untrack(two);Socket three=new Socket();ok(first.track(three));first.close();
        ok(one.isClosed()&&three.isClosed());ok(!two.isClosed());two.close();
        Socket late=new Socket();ok(!first.track(late)&&late.isClosed());ok(!first.active());first.close();
        try(RpcSocketRegistry next=new RpcSocketRegistry(1)){
            Socket fresh=new Socket();ok(next.active()&&next.track(fresh));ok(!fresh.isClosed());
            next.close();ok(fresh.isClosed());
        }
        System.out.println("PASS: "+n+" RPC socket ownership checks");
    }
}
