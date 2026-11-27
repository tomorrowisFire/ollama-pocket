package com.ollamapocket.app;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** A single explicitly reviewed local profile. Does not activate models, tools, or permissions. */
final class AgentProfileStore {
    private static final Object LOCK=new Object();
    private static final int MAX_BYTES=64000;
    private final Path root,file;
    AgentProfileStore(File noBackupRoot)throws IOException{
        if(noBackupRoot==null||!noBackupRoot.isDirectory()||
                !noBackupRoot.getAbsoluteFile().equals(noBackupRoot.getCanonicalFile()))
            throw new IOException("Invalid private profile root");
        root=noBackupRoot.toPath();file=root.resolve("agent-profile-v1.json");
    }
    private void checkRoot()throws IOException{
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.toFile().getCanonicalFile().equals(root.toFile()))
            throw new IOException("Invalid private profile root");
    }
    private AgentProfile readLocked()throws Exception{
        checkRoot();
        BasicFileAttributes attrs;
        try{attrs=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
        catch(NoSuchFileException absent){return null;}
        if(!attrs.isRegularFile()||attrs.size()<1||attrs.size()>MAX_BYTES)throw new IOException("Invalid profile file");
        byte[] data=new byte[(int)attrs.size()];
        try(InputStream in=Files.newInputStream(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            int off=0;
            while(off<data.length){int n=in.read(data,off,data.length-off);if(n<=0)throw new EOFException("Profile truncated");off+=n;}
            if(in.read()!=-1)throw new IOException("Profile grew");
        }
        String raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        AgentProfile p=AgentProfile.decode(raw);
        if(!raw.equals(p.encode()))throw new IOException("Noncanonical profile");
        return p;
    }
    AgentProfile read()throws Exception{synchronized(LOCK){return readLocked();}}
    private void requireCurrent(String expected)throws Exception{
        AgentProfile now=readLocked();
        if(!java.util.Objects.equals(expected,now==null?null:now.reviewDigest()))
            throw new IOException("Profile changed; review again");
    }
    AgentProfile save(String expected,AgentProfile reviewed)throws Exception{
        synchronized(LOCK){
            if(reviewed==null)throw new IllegalArgumentException("Reviewed profile required");
            requireCurrent(expected);
            byte[] data=reviewed.encode().getBytes(StandardCharsets.UTF_8);
            if(data.length<1||data.length>MAX_BYTES)throw new IOException("Profile size");
            Path stage=Files.createTempFile(root,".agent-profile-",".part");
            try{
                try(FileChannel out=FileChannel.open(stage,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                    ByteBuffer b=ByteBuffer.wrap(data);
                    while(b.hasRemaining())if(out.write(b)<=0)throw new IOException("Profile write failed");
                    out.force(true);
                }
                Files.move(stage,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                AgentProfile saved=readLocked();
                if(saved==null||!saved.reviewDigest().equals(reviewed.reviewDigest()))throw new IOException("Profile write uncertain");
                return saved;
            }finally{Files.deleteIfExists(stage);}
        }
    }
    void delete(String expected)throws Exception{
        synchronized(LOCK){
            if(expected==null)throw new IllegalArgumentException("Reviewed profile required");
            requireCurrent(expected);Files.delete(file);
            if(readLocked()!=null)throw new IOException("Profile delete uncertain");
        }
    }
}
