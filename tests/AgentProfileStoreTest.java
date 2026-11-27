package com.ollamapocket.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class AgentProfileStoreTest {
    private static int checks;
    private interface Action{void run()throws Exception;}
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("check "+checks);}
    private static void reject(Action a)throws Exception{checks++;try{a.run();}catch(Exception e){return;}throw new AssertionError("Unsafe profile write");}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("agent-profile-").toRealPath();
        try{
            AgentProfileStore a=new AgentProfileStore(root.toFile()),b=new AgentProfileStore(root.toFile());
            AgentProfile first=new AgentProfile("한국어","qwen:7b","a".repeat(64),"local instruction",750,64);
            AgentProfile second=new AgentProfile("English","qwen:7b","a".repeat(64),"changed",500,32);
            ok(a.read()==null);
            ok(a.save(null,first).reviewDigest().equals(first.reviewDigest()));
            ok(b.read().encode().equals(first.encode()));
            reject(()->b.save(null,second));
            ok(a.read().reviewDigest().equals(first.reviewDigest()));
            ok(b.save(first.reviewDigest(),second).reviewDigest().equals(second.reviewDigest()));
            reject(()->a.delete(first.reviewDigest()));
            a.delete(second.reviewDigest());ok(b.read()==null);
            Path file=root.resolve("agent-profile-v1.json");
            Files.writeString(file,"{\"bad\":true}");
            reject(a::read);reject(()->a.save(null,first));
            ok(Files.readString(file).equals("{\"bad\":true}"));
            Files.delete(file);
            Path outside=Files.createTempFile("agent-outside-",".json");
            try{
                Files.createSymbolicLink(file,outside);
                reject(a::read);reject(()->a.save(null,first));ok(Files.size(outside)==0);
                Files.delete(file);
            }finally{Files.deleteIfExists(outside);}
            a.save(null,first);
            Files.writeString(file,first.encode()+" ");
            reject(a::read);
            System.out.println("PASS: "+checks+" private agent profile review, conflict and storage checks");
        }finally{try(var all=Files.walk(root)){for(Path path:all.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(path);}}
    }
}
