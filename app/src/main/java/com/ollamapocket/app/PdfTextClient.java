package com.ollamapocket.app;

import android.annotation.TargetApi;
import android.content.*;
import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import android.os.*;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** A bounded seekable descriptor is passed to a unique isolated process. No original document is retained. */
final class PdfTextClient {
    private PdfTextClient(){}
    private static AttachmentText.Failure failure(AttachmentText.Problem p){return new AttachmentText.Failure(p);}
    static AttachmentText.Result read(Context context,String name,InputStream source)throws IOException{return read(context,name,source,1);}
    static AttachmentText.Result read(Context context,String name,InputStream source,int first)throws IOException {
        if(!OcrPolicy.validPage(first))throw failure(AttachmentText.Problem.RANGE);
        Bundle b=readBundle(context,source,first,false);
        String text=b.getString("text","");
        int pages=b.getInt("pages"),examined=b.getInt("examined"),withText=b.getInt("textPages");
        if(text.trim().isEmpty()||text.length()>AttachmentText.MAX_EXCERPT||pages<first||examined<=0||
            b.getInt("first")!=first||examined>Math.min(pages-first+1,PdfExcerpt.MAX_PAGES)||withText<=0||withText>examined)
            throw failure(AttachmentText.Problem.INVALID);
        return new AttachmentText.Result(AttachmentText.safeName(name),text,b.getBoolean("partial"),false,true,
            pages,examined,withText,false,0,0,0,0,0,first);
    }
    @TargetApi(35)
    static AttachmentText.Result ocr(Context context,String name,InputStream source,OcrPolicy.Script script,int number)throws IOException{
        if(!OcrPolicy.validPage(number))throw failure(AttachmentText.Problem.OCR_PAGE);
        Bundle b=readBundle(context,source,number,true);b.setClassLoader(SharedMemory.class.getClassLoader());
        SharedMemory pixels=b.getParcelable("pixels",SharedMemory.class);
        if(pixels==null)throw failure(AttachmentText.Problem.INVALID);
        Bitmap bitmap=null;
        try(SharedMemory memory=pixels){
            int w=b.getInt("width"),h=b.getInt("height"),page=b.getInt("page"),pages=b.getInt("pages");
            if(!OcrPolicy.validBitmap(w,h)||page!=number||pages<page||memory.getSize()!=w*h*4)
                throw failure(AttachmentText.Problem.INVALID);
            bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            if(bitmap.getRowBytes()!=w*4||bitmap.getByteCount()!=memory.getSize())
                throw failure(AttachmentText.Problem.INVALID);
            ByteBuffer buffer=memory.mapReadOnly();
            try{bitmap.copyPixelsFromBuffer(buffer);}finally{SharedMemory.unmap(buffer);}
            Bitmap owned=bitmap;bitmap=null;
            return ImageOcr.readPage(name,owned,script,page,pages);
        }catch(AttachmentText.Failure e){throw e;}
        catch(OutOfMemoryError e){throw failure(AttachmentText.Problem.TOO_LARGE);}
        catch(Exception e){throw failure(AttachmentText.Problem.OCR_FAILED);}
        finally{if(bitmap!=null)bitmap.recycle();}
    }
    private static Bundle readBundle(Context context,InputStream source,int page,boolean raster)throws IOException{
        if(Build.VERSION.SDK_INT<35)throw failure(AttachmentText.Problem.PDF_VERSION);
        if(source==null)throw failure(AttachmentText.Problem.INVALID);
        File temporary=null;
        try{
            temporary=File.createTempFile("pdf-excerpt-",".pdf",context.getCacheDir());
            long count=0;byte[] buf=new byte[8192];
            try(FileOutputStream out=new FileOutputStream(temporary)){
                int n;
                while((n=source.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    if(n==0)continue;
                    count+=n;
                    if(count>PdfExcerpt.MAX_BYTES)throw failure(AttachmentText.Problem.TOO_LARGE);
                    out.write(buf,0,n);
                }
            }
            if(count<5)throw failure(AttachmentText.Problem.INVALID);
            try(ParcelFileDescriptor input=ParcelFileDescriptor.open(temporary,ParcelFileDescriptor.MODE_READ_ONLY)){
                if(!temporary.delete())throw failure(AttachmentText.Problem.INVALID);
                temporary=null;
                return parse(context.getApplicationContext(),input,page,raster);
            }
        }finally{if(temporary!=null)temporary.delete();}
    }
    @TargetApi(35)
    private static void closePixels(Bundle b){
        try{
            b.setClassLoader(SharedMemory.class.getClassLoader());
            SharedMemory m=b.getParcelable("pixels",SharedMemory.class);
            if(m!=null)m.close();
        }catch(Exception ignored){}
    }
    @TargetApi(35)
    private static Bundle parse(Context app,ParcelFileDescriptor input,int page,boolean raster)throws IOException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("PDF read must be off the UI thread");
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<Bundle> result=new AtomicReference<>();
        AtomicBoolean active=new AtomicBoolean(true);
        AtomicBoolean sent=new AtomicBoolean();
        Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),msg->{
            if(msg.what==PdfExtractService.RESULT){
                Bundle data=msg.getData();
                if(active.get()&&result.compareAndSet(null,data))done.countDown();
                else if(raster)closePixels(data);
            }
            return true;
        }));
        ServiceConnection conn=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName component,IBinder service){
                if(!active.get()||!sent.compareAndSet(false,true))return;
                try{
                    Message request=Message.obtain(null,raster?PdfExtractService.RASTER:PdfExtractService.READ);request.replyTo=reply;
                    Bundle data=new Bundle();data.putParcelable("fd",input);data.putInt("page",page);request.setData(data);
                    new Messenger(service).send(request);
                }catch(Exception e){done.countDown();}
            }
            @Override public void onServiceDisconnected(ComponentName component){done.countDown();}
            @Override public void onBindingDied(ComponentName component){done.countDown();}
            @Override public void onNullBinding(ComponentName component){done.countDown();}
        };
        boolean bound=false;
        try{
            bound=app.bindIsolatedService(new Intent(app,PdfExtractService.class),Context.BIND_AUTO_CREATE,
                "pdf"+UUID.randomUUID().toString().replace("-",""),app.getMainExecutor(),conn);
            if(!bound)throw failure(AttachmentText.Problem.INVALID);
            long begin=SystemClock.elapsedRealtime();
            boolean received;
            try{received=done.await(45,TimeUnit.SECONDS);}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}
            Bundle b=result.get();
            if(!received||(b==null&&SystemClock.elapsedRealtime()-begin>=PdfExtractService.WATCHDOG_SECONDS*1000))
                throw failure(AttachmentText.Problem.PDF_TIMEOUT);
            if(b==null)throw failure(AttachmentText.Problem.INVALID);
            String error=b.getString("error");
            if(error!=null){
                AttachmentText.Problem p;
                try{p=AttachmentText.Problem.valueOf(error);}catch(IllegalArgumentException e){p=AttachmentText.Problem.INVALID;}
                throw failure(p);
            }
            return b;
        }catch(SecurityException e){throw failure(AttachmentText.Problem.INVALID);}
        finally{active.set(false);if(bound)try{app.unbindService(conn);}catch(Exception ignored){}}
    }
}
