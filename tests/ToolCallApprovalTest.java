package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ToolCallApprovalTest {
    private static final String ENDPOINT = "http://192.168.0.10:3001/mcp";
    private static final String NAME = "lookup", IDENTITY = "catalog-1", ARGS = "{\"q\":\"한글😀\"}";
    private static int checks;
    private static void ok(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static void fails(Class<? extends Throwable> type, Runnable task) {
        checks++;
        try { task.run(); } catch (Throwable e) { if (type.isInstance(e)) return; throw new AssertionError(e); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static ToolCallApproval.Review prepared(ToolCallApproval guard) {
        return guard.prepare(NAME, IDENTITY, ARGS, true);
    }
    private static ToolCallApproval.Review approved(ToolCallApproval guard) {
        ToolCallApproval.Review r = prepared(guard);
        ok(guard.approve(r), "explicit approval");
        return r;
    }
    private static ToolCallApproval.Lease begin(ToolCallApproval g, ToolCallApproval.Review r) {
        return g.begin(r, ENDPOINT, NAME, IDENTITY, ARGS, true);
    }
    private static void identityTests() {
        AtomicLong clock = new AtomicLong();
        try (ToolCallApproval g = new ToolCallApproval(ENDPOINT, 4, clock::get);
             ToolCallApproval other = new ToolCallApproval(ENDPOINT, 4, clock::get)) {
            ToolCallApproval.Review r = prepared(g);
            ok(begin(g,r)==null && g.attempted()==0, "not approved");
            ok(!other.approve(r) && begin(other,r)==null, "foreign review");
            ok(g.approve(r) && !g.approve(r), "one explicit approval");
            ok(g.begin(r, ENDPOINT+"/other", NAME, IDENTITY, ARGS, true)==null, "changed endpoint");
            ok(begin(g,r)==null && !g.approve(r), "mismatch consumed");
            r=approved(g);
            ok(g.begin(r,ENDPOINT,"other",IDENTITY,ARGS,true)==null,"changed tool");
            r=approved(g);
            ok(g.begin(r,ENDPOINT,NAME,"catalog-2",ARGS,true)==null,"changed catalog");
            r=approved(g);
            ok(g.begin(r,ENDPOINT,NAME,IDENTITY,ARGS+" ",true)==null,"even whitespace changed");
            r=approved(g);
            ok(g.begin(r,ENDPOINT,NAME,IDENTITY,ARGS,false)==null,"revoked permission/schema");
            ok(g.attempted()==0 && g.remaining()==4,"denials not admitted");
            // Exact UTF-16 identity must not collapse malformed catalog strings to replacement characters.
            r=g.prepare(NAME,"id\ud800",ARGS,true);ok(g.approve(r),"opaque catalog identity");
            ok(g.begin(r,ENDPOINT,NAME,"id\ud801",ARGS,true)==null,"different code units");
            r=approved(g);ToolCallApproval.Review replacement=prepared(g);
            ok(!g.approve(r) && begin(g,r)==null,"superseded token");
            ok(g.approve(replacement),"replacement approved");g.decline(r);
            ToolCallApproval.Lease lease=begin(g,replacement);
            ok(lease!=null && g.inFlight() && g.remaining()==3,"exact request admitted");
            ok(begin(g,replacement)==null,"no replay");
            fails(IllegalStateException.class,()->prepared(g));
            g.revoke();ok(g.inFlight(),"revoke does not fake transport completion");
            lease.close();lease.close();ok(!g.inFlight()&&g.attempted()==1,"idempotent release/no refund");
        }
    }
    private static void expiryTests() {
        AtomicLong clock=new AtomicLong(100);
        try(ToolCallApproval g=new ToolCallApproval(ENDPOINT,4,clock::get)){
            ToolCallApproval.Review r=prepared(g);
            clock.addAndGet(ToolCallApproval.REVIEW_NANOS);
            ok(!g.approve(r)&&begin(g,r)==null,"expiry before approval");
            r=approved(g);clock.addAndGet(ToolCallApproval.REVIEW_NANOS);
            ok(begin(g,r)==null,"expiry after approval");
            r=approved(g);clock.decrementAndGet();ok(begin(g,r)==null,"backwards clock");
            clock.set(Long.MAX_VALUE-2);r=approved(g);clock.addAndGet(8);
            ToolCallApproval.Lease lease=begin(g,r);ok(lease!=null,"monotonic wraparound");lease.close();
            r=approved(g);clock.addAndGet(ToolCallApproval.REVIEW_NANOS-1);
            lease=begin(g,r);ok(lease!=null,"one nanosecond before deadline");lease.close();
            r=approved(g);g.decline(r);ok(begin(g,r)==null,"declined");
            r=approved(g);g.revoke();ok(begin(g,r)==null,"revoked");
            r=approved(g);g.close();ok(begin(g,r)==null&&g.remaining()==0,"closed");
            fails(IllegalStateException.class,()->prepared(g));
        }
        // The request expires during its final identity check.
        AtomicLong ticks=new AtomicLong();
        try(ToolCallApproval g=new ToolCallApproval(ENDPOINT,1,()->ticks.getAndIncrement()*40_000_000_000L)){
            ToolCallApproval.Review r=approved(g);
            ok(begin(g,r)==null&&g.attempted()==0,"final TTL check");
        }
    }
    private static void boundsTests() {
        for(String endpoint:new String[]{null,"","bad endpoint","a".repeat(129),"x\n"})
            fails(IllegalArgumentException.class,()->new ToolCallApproval(endpoint,1));
        for(int limit:new int[]{-1,0,17,Integer.MAX_VALUE})fails(IllegalArgumentException.class,()->new ToolCallApproval(ENDPOINT,limit));
        fails(IllegalArgumentException.class,()->new ToolCallApproval(ENDPOINT,1,null));
        try(ToolCallApproval g=new ToolCallApproval(ENDPOINT,2,()->0L)){
            for(String name:new String[]{null,"","x y","x/other","a".repeat(129)})
                fails(IllegalArgumentException.class,()->g.prepare(name,IDENTITY,ARGS,true));
            for(String identity:new String[]{null,"","a".repeat(32769)})
                fails(IllegalArgumentException.class,()->g.prepare(NAME,identity,ARGS,true));
            for(String args:new String[]{null,"","[]","{x:1}","{\"x\":1,\"x\":2}","{\"x\":1,\"\\u0078\":2}"})
                fails(IllegalArgumentException.class,()->g.prepare(NAME,IDENTITY,args,true));
            fails(IllegalArgumentException.class,()->g.prepare(NAME,IDENTITY,ARGS,false));
            ToolCallApproval.Review r=approved(g);
            fails(IllegalArgumentException.class,()->g.prepare(NAME,IDENTITY,"{x:1}",true));
            ok(begin(g,r)==null,"invalid replacement revokes prior review");
            r=approved(g);ok(g.begin(r,ENDPOINT,NAME,IDENTITY,"{x:1}",true)==null,"strict final admission");
            r=approved(g);ToolCallApproval.Lease l=begin(g,r);ok(l!=null,"attempt one");l.close();
            r=approved(g);ToolCallApproval.Lease current=begin(g,r);ok(current!=null,"attempt two");
            l.close();ok(g.inFlight(),"old lease cannot release current lease");
            g.close();ok(g.inFlight(),"close retains actual outstanding lease");current.close();
            ok(!g.inFlight()&&g.attempted()==2&&g.remaining()==0,"closed transport release");
        }
        try(ToolCallApproval g=new ToolCallApproval(ENDPOINT,1,()->0L)){
            ToolCallApproval.Lease lease=begin(g,approved(g));ok(lease!=null,"budget admission");lease.close();
            fails(IllegalStateException.class,()->prepared(g));ok(g.remaining()==0,"no refund after failure");
        }
    }
    private static void concurrentTests() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(16);
        try(ToolCallApproval g=new ToolCallApproval(ENDPOINT,16,()->0L)){
            for(int round=0;round<16;round++){
                ToolCallApproval.Review r=approved(g);
                CountDownLatch start=new CountDownLatch(1);
                List<Future<ToolCallApproval.Lease>> futures=new ArrayList<>();
                for(int i=0;i<16;i++)futures.add(pool.submit(()->{start.await();return begin(g,r);}));
                start.countDown();int admitted=0;ToolCallApproval.Lease owner=null;
                for(Future<ToolCallApproval.Lease> f:futures){ToolCallApproval.Lease lease=f.get(5,TimeUnit.SECONDS);if(lease!=null){admitted++;owner=lease;}}
                ok(admitted==1 && g.attempted()==round+1 && g.inFlight(),"one concurrent winner");
                owner.close();ok(!g.inFlight(),"release winner");
            }
            ok(g.remaining()==0,"concurrent budget ceiling");
            fails(IllegalStateException.class,()->prepared(g));
        }finally{pool.shutdownNow();}
    }
    public static void main(String[] args) throws Exception {
        identityTests();expiryTests();boundsTests();concurrentTests();
        System.out.println("PASS: "+checks+" exact one-shot tool review, expiration, budget and concurrency checks");
    }
}
