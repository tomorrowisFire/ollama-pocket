package com.ollamapocket.app;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class NativeWeightPreflightTest {
    private static int checks;
    private static final long MIN=1048576L;
    private static void check(boolean value){if(!value)throw new AssertionError("weight preflight "+checks);checks++;}
    private static void make(File file,long size,String prefix)throws Exception{
        try(RandomAccessFile out=new RandomAccessFile(file,"rw")){
            out.setLength(0);out.write(prefix.getBytes(StandardCharsets.US_ASCII));out.setLength(size);
        }
    }
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("native-weights-");
        try{
            File root=Files.createDirectory(tmp.resolve("models")).toFile();
            File blobs=Files.createDirectory(root.toPath().resolve("blobs")).toFile();
            String digest="sha256:"+"a".repeat(64);
            File weights=new File(blobs,"sha256-"+digest.substring(7));
            check(NativeWeightPreflight.resolve(root,digest,-1)==null);
            for(long size:new long[]{0,1,3,4,MIN-1,MIN,MIN+1,MIN+4096}){
                for(String magic:new String[]{"GGUF","GGML","gguf","G","GGU",""}){
                    make(weights,size,magic);
                    for(long declared:new long[]{-1,0,size,size+1,Long.MAX_VALUE}){
                        boolean valid=size>MIN&&"GGUF".equals(magic)&&(declared<=0||declared==size);
                        File found=NativeWeightPreflight.resolve(root,digest,declared);
                        check(valid?weights.getCanonicalFile().equals(found):found==null);
                    }
                }
            }
            make(weights,MIN+1,"GGUF");
            for(String bad:new String[]{null,"","sha256:","SHA256:"+"a".repeat(64),"sha256:"+"g".repeat(64),"sha256:"+"a".repeat(63),"sha256:"+"a".repeat(65),"../"+digest})
                check(NativeWeightPreflight.resolve(root,bad,-1)==null);
            check(NativeWeightPreflight.resolve(null,digest,-1)==null);
            check(NativeWeightPreflight.resolve(root,digest,MIN+1)!=null);
            Files.delete(weights.toPath());Files.createDirectory(weights.toPath());
            check(NativeWeightPreflight.resolve(root,digest,-1)==null);
            Files.delete(weights.toPath());
            File outside=tmp.resolve("outside").toFile();make(outside,MIN+1,"GGUF");
            Files.createSymbolicLink(weights.toPath(),outside.toPath());
            check(NativeWeightPreflight.resolve(root,digest,-1)==null);
            Files.delete(weights.toPath());Files.delete(blobs.toPath());
            Path other=Files.createDirectory(tmp.resolve("other"));
            make(other.resolve(weights.getName()).toFile(),MIN+1,"GGUF");
            Files.createSymbolicLink(blobs.toPath(),other);
            check(NativeWeightPreflight.resolve(root,digest,-1)==null);
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(tmp)){
                for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS: "+checks+" native weight preflight checks");
    }
}
