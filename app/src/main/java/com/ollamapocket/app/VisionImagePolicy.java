package com.ollamapocket.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/** Bounded static-image admission; no network, file paths, persistence or OCR. */
final class VisionImagePolicy {
    static final int MAX_SOURCE_BYTES = 8 * 1024 * 1024;
    static final int MAX_SOURCE_SIDE = 16384;
    static final long MAX_SOURCE_PIXELS = 40_000_000L;
    static final int MAX_SIDE = 1024, MAX_OUTPUT_BYTES = 2 * 1024 * 1024;
    static final long MAX_PIXELS = 1_048_576L;
    private static final int MAX_PNG_CHUNKS = 4096;
    private VisionImagePolicy() { }

    enum Format {
        JPEG("image/jpeg"), PNG("image/png");
        final String mime;
        Format(String mime) { this.mime = mime; }
    }

    static final class Header {
        final Format format;
        final int width, height;
        Header(Format format, int width, int height) {
            this.format = format; this.width = width; this.height = height;
        }
        boolean matchesDecoder(String mime, int w, int h) {
            return format.mime.equals(mime) && validSource(w,h) &&
                (format != Format.PNG || width == w && height == h);
        }
    }

    static boolean validSource(int w, int h) {
        return w > 0 && h > 0 && w <= MAX_SOURCE_SIDE && h <= MAX_SOURCE_SIDE && (long)w*h <= MAX_SOURCE_PIXELS;
    }
    static boolean validOutput(int w, int h) {
        return w > 0 && h > 0 && w <= MAX_SIDE && h <= MAX_SIDE && (long)w*h <= MAX_PIXELS;
    }
    static int sample(int w, int h) {
        if (!validSource(w,h)) throw new IllegalArgumentException("Vision source dimensions");
        int sample = 1;
        while (!validOutput((int)((w+sample-1L)/sample),(int)((h+sample-1L)/sample))) sample *= 2;
        return sample;
    }
    static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancelled != null && cancelled.getAsBoolean())
            throw new InterruptedIOException("Vision input cancelled");
    }
    static byte[] read(InputStream in, BooleanSupplier cancelled) throws IOException {
        if (in == null) throw new IOException("Missing vision source");
        BoundedOutput out = new BoundedOutput(MAX_SOURCE_BYTES);
        byte[] buffer = new byte[16384];
        for (;;) {
            check(cancelled);
            int n = in.read(buffer,0,Math.min(buffer.length,MAX_SOURCE_BYTES-out.size()+1));
            check(cancelled);
            if (n < 0) break;
            if (n == 0) throw new IOException("Empty vision source read");
            out.write(buffer,0,n);
        }
        if (out.size() == 0) throw new IOException("Empty vision source");
        return out.bytes();
    }
    private static boolean jpeg(byte[] b) {
        return b != null && b.length >= 4 && (b[0]&255)==255 && (b[1]&255)==216 &&
            (b[2]&255)==255 && (b[b.length-2]&255)==255 && (b[b.length-1]&255)==217;
    }
    private static long u32(byte[] b, int p) {
        return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)|((long)(b[p+2]&255)<<8)|(b[p+3]&255);
    }
    private static boolean type(byte[] b, int p, String name) {
        for (int i=0;i<4;i++) if (b[p+i] != (byte)name.charAt(i)) return false;
        return true;
    }
    static Header inspect(byte[] b) throws IOException {
        if (b==null || b.length<4 || b.length>MAX_SOURCE_BYTES) throw new IOException("Vision source size");
        if (jpeg(b)) return new Header(Format.JPEG,0,0);
        byte[] png={(byte)137,80,78,71,13,10,26,10};
        if (b.length<8) throw new IOException("Unsupported vision format");
        for (int i=0;i<8;i++) if(b[i]!=png[i]) throw new IOException("Unsupported vision format");
        int pos=8, chunks=0, w=0,h=0;
        boolean idat=false, positiveData=false, endedData=false;
        while(pos<b.length){
            if(++chunks>MAX_PNG_CHUNKS || b.length-pos<12) throw new IOException("PNG chunk bounds");
            long size=u32(b,pos), end=(long)pos+12+size;
            if(end>b.length) throw new IOException("Truncated PNG chunk");
            int t=pos+4,n=(int)size;
            for(int i=0;i<4;i++){
                int c=b[t+i]&255;
                if(!((c>='A'&&c<='Z')||(c>='a'&&c<='z')))throw new IOException("Invalid PNG chunk type");
            }
            CRC32 crc=new CRC32();crc.update(b,t,4+n);
            if(crc.getValue()!=u32(b,t+4+n))throw new IOException("PNG checksum");
            if(chunks==1){
                if(!type(b,t,"IHDR")||n!=13)throw new IOException("Missing PNG header");
                long width=u32(b,t+4),height=u32(b,t+8);
                if(width>Integer.MAX_VALUE||height>Integer.MAX_VALUE||!validSource((int)width,(int)height))
                    throw new IOException("PNG source dimensions");
                w=(int)width;h=(int)height;
            }else if(type(b,t,"IHDR"))throw new IOException("Duplicate PNG header");
            if(type(b,t,"acTL")||type(b,t,"fcTL")||type(b,t,"fdAT"))throw new IOException("Animated PNG unsupported");
            if(type(b,t,"IDAT")){
                if(endedData)throw new IOException("Noncontiguous PNG data");
                idat=true;positiveData|=n>0;
            }else if(idat)endedData=true;
            if(type(b,t,"IEND")){
                if(n!=0||!positiveData||end!=b.length)throw new IOException("Invalid PNG end");
                return new Header(Format.PNG,w,h);
            }
            pos=(int)end;
        }
        throw new IOException("Missing PNG end");
    }

    static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private final int limit;
        BoundedOutput(int limit) {
            if(limit<=0||limit>MAX_SOURCE_BYTES)throw new IllegalArgumentException("Vision buffer limit");
            this.limit=limit;
        }
        int size(){return data.size();}
        byte[] bytes(){return data.toByteArray();}
        @Override public void write(int b)throws IOException{
            if(data.size()>=limit)throw new IOException("Vision buffer limit");
            data.write(b);
        }
        @Override public void write(byte[] b,int off,int len)throws IOException{
            if(b==null||off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();
            if(len>limit-data.size())throw new IOException("Vision buffer limit");
            data.write(b,off,len);
        }
    }
    /** Produced only after the Android worker re-encodes pixels, not raw imported files. */
    static final class Prepared {
        private final byte[] data;
        final int width,height;
        final String sha256;
        private Prepared(byte[] bytes,int w,int h){
            data=bytes.clone();width=w;height=h;
            try{
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);
                StringBuilder s=new StringBuilder(64);
                for(byte b:hash)s.append(String.format(Locale.ROOT,"%02x",b&255));
                sha256=s.toString();
            }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
        }
        int size(){return data.length;}
        byte[] copyBytes(){return data.clone();}
    }
    static Prepared finish(byte[] jpeg,int w,int h)throws IOException{
        if(!validOutput(w,h)||jpeg==null||jpeg.length>MAX_OUTPUT_BYTES||!jpeg(jpeg))
            throw new IOException("Invalid prepared JPEG");
        return new Prepared(jpeg,w,h);
    }
}
