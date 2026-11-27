package com.ollamapocket.app;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

public final class VisionImagePolicyTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void ok(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
    private static void fails(Class<? extends Throwable> type,Action action)throws Exception{
        checks++;try{action.run();}catch(Throwable e){if(type.isInstance(e))return;throw new AssertionError("wrong error",e);}
        throw new AssertionError("unexpected acceptance at "+checks);
    }
    private static byte[] chunk(String type,byte[] bytes)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);
        byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);d.writeInt(bytes.length);d.write(name);d.write(bytes);
        CRC32 crc=new CRC32();crc.update(name);crc.update(bytes);d.writeInt((int)crc.getValue());return out.toByteArray();
    }
    private static byte[] ihdr(int w,int h)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);
        d.writeInt(w);d.writeInt(h);d.write(new byte[]{8,2,0,0,0});return chunk("IHDR",out.toByteArray());
    }
    private static byte[] png(int w,int h,byte[]... middle)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});out.write(ihdr(w,h));
        for(byte[] c:middle)out.write(c);out.write(chunk("IEND",new byte[0]));return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        byte[] jpeg={(byte)255,(byte)216,(byte)255,(byte)224,0,2,(byte)255,(byte)217}; // signature fixture, not a decoded JPEG
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)){z.write(new byte[]{0,1,2,3});}
        byte[] idat=chunk("IDAT",compressed.toByteArray()), image=png(1,1,idat);
        VisionImagePolicy.Header p=VisionImagePolicy.inspect(image),j=VisionImagePolicy.inspect(jpeg);
        ok(p.format==VisionImagePolicy.Format.PNG&&p.width==1&&p.height==1&&p.matchesDecoder("image/png",1,1));
        ok(j.format==VisionImagePolicy.Format.JPEG&&j.matchesDecoder("image/jpeg",100,100));
        ok(!p.matchesDecoder("image/jpeg",1,1)&&!p.matchesDecoder("image/png",2,1));
        ok(!j.matchesDecoder("image/jpeg",0,1)&&!j.matchesDecoder("image/jpeg",16384,16384));
        for(byte[] bad:new byte[][]{null,new byte[0],new byte[]{1,2,3,4},Arrays.copyOf(jpeg,jpeg.length-1),
            Arrays.copyOf(image,image.length-1),Arrays.copyOf(image,image.length+1),png(0,1,idat),png(-1,1,idat),
            png(16384,16384,idat),png(1,1),png(1,1,chunk("IDAT",new byte[0])),png(1,1,ihdr(1,1),idat),
            png(1,1,chunk("acTL",new byte[8]),idat),png(1,1,chunk("fcTL",new byte[26]),idat),
            png(1,1,idat,chunk("fdAT",new byte[4])),png(1,1,idat,chunk("tEXt",new byte[0]),idat),
            png(1,1,chunk("1DAT",new byte[]{1}),idat)})
            fails(IOException.class,()->VisionImagePolicy.inspect(bad));
        byte[] corrupt=image.clone();corrupt[29]^=1;fails(IOException.class,()->VisionImagePolicy.inspect(corrupt));
        byte[] overflow=image.clone();Arrays.fill(overflow,8,12,(byte)255);fails(IOException.class,()->VisionImagePolicy.inspect(overflow));
        byte[][] many=new byte[4096][];Arrays.fill(many,chunk("tEXt",new byte[0]));fails(IOException.class,()->VisionImagePolicy.inspect(png(1,1,many)));
        for(int[] pair:new int[][]{{0,1},{1,0},{-1,1},{16385,1},{1,16385},{Integer.MAX_VALUE,Integer.MAX_VALUE},{7000,7000}})
            fails(IllegalArgumentException.class,()->VisionImagePolicy.sample(pair[0],pair[1]));
        ok(VisionImagePolicy.sample(1024,1024)==1&&VisionImagePolicy.sample(1025,1024)==2);
        ok(VisionImagePolicy.sample(16384,1)==16);
        Random random=new Random(44131);
        for(int i=0;i<2000;i++){
            int w=1+random.nextInt(16384),h=1+random.nextInt(16384);
            if(!VisionImagePolicy.validSource(w,h)){
                fails(IllegalArgumentException.class,()->VisionImagePolicy.sample(w,h));continue;
            }
            int s=VisionImagePolicy.sample(w,h);ok(s>0&&(s&(s-1))==0);
            ok(VisionImagePolicy.validOutput((w+s-1)/s,(h+s-1)/s));
            if(s>1){int half=s/2;ok(!VisionImagePolicy.validOutput((w+half-1)/half,(h+half-1)/half));}
        }
        int cap=VisionImagePolicy.MAX_SOURCE_BYTES;
        ok(VisionImagePolicy.read(new ByteArrayInputStream(new byte[cap]),()->false).length==cap);
        fails(IOException.class,()->VisionImagePolicy.read(new ByteArrayInputStream(new byte[cap+1]),()->false));
        fails(IOException.class,()->VisionImagePolicy.read(new ByteArrayInputStream(new byte[0]),()->false));
        fails(IOException.class,()->VisionImagePolicy.read(null,()->false));
        fails(InterruptedIOException.class,()->VisionImagePolicy.read(new ByteArrayInputStream(image),()->true));
        java.util.concurrent.atomic.AtomicInteger polls=new java.util.concurrent.atomic.AtomicInteger();
        fails(InterruptedIOException.class,()->VisionImagePolicy.read(new ByteArrayInputStream(image),()->polls.incrementAndGet()>=2));
        fails(IOException.class,()->VisionImagePolicy.read(new InputStream(){public int read(){return 0;}public int read(byte[] b,int o,int l){return 0;}},()->false));
        VisionImagePolicy.BoundedOutput output=new VisionImagePolicy.BoundedOutput(8);output.write(jpeg);ok(output.size()==8);
        fails(IOException.class,()->output.write(1));fails(IOException.class,()->output.write(new byte[]{1},0,1));
        fails(IndexOutOfBoundsException.class,()->output.write(new byte[3],2,2));ok(Arrays.equals(output.bytes(),jpeg));
        VisionImagePolicy.Prepared prepared=VisionImagePolicy.finish(jpeg,100,100);
        byte[] copy=prepared.copyBytes();copy[0]=0;jpeg[0]=0;
        ok(prepared.size()==8&&(prepared.copyBytes()[0]&255)==255&&prepared.sha256.matches("[a-f0-9]{64}"));
        ok(prepared.width==100&&prepared.height==100);
        fails(IOException.class,()->VisionImagePolicy.finish(image,1,1));
        fails(IOException.class,()->VisionImagePolicy.finish(prepared.copyBytes(),1025,1));
        fails(IOException.class,()->VisionImagePolicy.finish(new byte[VisionImagePolicy.MAX_OUTPUT_BYTES+1],1,1));
        System.out.println("PASS: "+checks+" bounded visual input policy checks");
    }
}
