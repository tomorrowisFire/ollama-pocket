package com.ollamapocket.app;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.system.OsConstants;
import java.nio.ByteBuffer;
import android.graphics.pdf.content.PdfPageTextContent;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-exported isolated PDF worker. Receives only a read-only FD and returns a bounded excerpt. */
@TargetApi(35)
public final class PdfExtractService extends Service {
    static final int READ=1,RESULT=2,RASTER=3;
    static final long WATCHDOG_SECONDS=35;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean used=new AtomicBoolean();
    private final Messenger incoming=new Messenger(new Handler(Looper.getMainLooper(),this::receive));

    private boolean receive(Message msg){
        if(msg.what!=READ&&msg.what!=RASTER)return false;
        Messenger reply=msg.replyTo;
        ParcelFileDescriptor fd=null;
        try{
            if(Build.VERSION.SDK_INT<35){sendError(reply,AttachmentText.Problem.PDF_VERSION);return true;}
            Bundle b=msg.getData();b.setClassLoader(ParcelFileDescriptor.class.getClassLoader());
            fd=b.getParcelable("fd",ParcelFileDescriptor.class);
            if(reply==null||fd==null||!used.compareAndSet(false,true)){
                if(fd!=null)fd.close();
                sendError(reply,AttachmentText.Problem.INVALID);return true;
            }
            final ParcelFileDescriptor input=fd;
            final int page=b.getInt("page",0);
            final boolean raster=msg.what==RASTER;
            worker.execute(()->extract(input,reply,raster,page));
        }catch(Exception e){if(fd!=null)try{fd.close();}catch(Exception ignored){}sendError(reply,AttachmentText.Problem.INVALID);}
        return true;
    }
    private void extract(ParcelFileDescriptor input,Messenger reply,boolean raster,int pageNumber){
        ScheduledFuture<?> deadline=watchdog.schedule(()->{
            // Manifest guarantees a separate isolated worker, not the UI process.
            android.os.Process.killProcess(android.os.Process.myPid());
        },WATCHDOG_SECONDS,TimeUnit.SECONDS);
        try{
            long size=input.getStatSize();
            if(size>PdfExcerpt.MAX_BYTES){sendError(reply,AttachmentText.Problem.TOO_LARGE);return;}
            if(size<=0){sendError(reply,AttachmentText.Problem.INVALID);return;}
            // PdfRenderer takes ownership after successful construction.
            try(PdfRenderer renderer=new PdfRenderer(input)){
                if(raster){renderPage(renderer,reply,pageNumber);return;}
                if(!OcrPolicy.validPage(pageNumber)||pageNumber>renderer.getPageCount()){
                    sendError(reply,AttachmentText.Problem.RANGE);return;
                }
                PdfExcerpt.Builder excerpt=new PdfExcerpt.Builder(renderer.getPageCount(),pageNumber);
                for(int i=pageNumber-1;excerpt.canReadPage();i++){
                    if(Thread.currentThread().isInterrupted())return;
                    excerpt.startPage(i+1);
                    try(PdfRenderer.Page page=renderer.openPage(i)){
                        for(PdfPageTextContent block:page.getTextContents()){
                            if(Thread.currentThread().isInterrupted())return;
                            excerpt.append(block.getText());
                            if(excerpt.full()){excerpt.markPartial();break;}
                        }
                    }
                    excerpt.endPage();
                }
                AttachmentText.Result r=excerpt.result("file.pdf");
                Bundle b=new Bundle();
                b.putString("text",r.text);b.putBoolean("partial",r.partial);
                b.putInt("pages",r.pdfPages);b.putInt("examined",r.pdfExamined);b.putInt("textPages",r.pdfTextPages);b.putInt("first",r.pdfFirst);
                send(reply,b);
            }
        }catch(SecurityException e){sendError(reply,AttachmentText.Problem.PDF_PROTECTED);}
        catch(AttachmentText.Failure e){sendError(reply,e.problem);}
        catch(Exception e){sendError(reply,AttachmentText.Problem.INVALID);}
        finally{deadline.cancel(false);try{input.close();}catch(Exception ignored){}}
    }
    /** Pixel payload travels via a bounded read-only shared-memory descriptor, never Binder byte arrays. */
    private static void renderPage(PdfRenderer renderer,Messenger reply,int number)throws Exception{
        int pages=renderer.getPageCount();
        if(!OcrPolicy.validPage(number)||number>pages){sendError(reply,AttachmentText.Problem.OCR_PAGE);return;}
        try(PdfRenderer.Page page=renderer.openPage(number-1)){
            int width=page.getWidth(),height=page.getHeight();
            if(width<=0||height<=0){sendError(reply,AttachmentText.Problem.INVALID);return;}
            float scale=Math.min(3f,(float)OcrPolicy.MAX_SIDE/Math.max(width,height));
            int w=Math.max(1,Math.min(OcrPolicy.MAX_SIDE,Math.round(width*scale)));
            int h=Math.max(1,Math.min(OcrPolicy.MAX_SIDE,Math.round(height*scale)));
            if(!OcrPolicy.validBitmap(w,h))throw new IllegalArgumentException("Bitmap bounds");
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            try{
                bitmap.eraseColor(android.graphics.Color.WHITE);
                Matrix matrix=new Matrix();matrix.setScale((float)w/width,(float)h/height);
                page.render(bitmap,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                int bytes=bitmap.getByteCount(),stride=bitmap.getRowBytes();
                if(stride!=w*4||bytes!=w*h*4||bytes>OcrPolicy.MAX_PIXELS*4)throw new IllegalArgumentException("Unexpected bitmap stride");
                try(SharedMemory memory=SharedMemory.create("onhand-pdf-page",bytes)){
                    ByteBuffer buffer=memory.mapReadWrite();
                    try{bitmap.copyPixelsToBuffer(buffer);}finally{SharedMemory.unmap(buffer);}
                    if(!memory.setProtect(OsConstants.PROT_READ))throw new IllegalStateException("Read-only mapping failed");
                    Bundle result=new Bundle();result.putParcelable("pixels",memory);
                    result.putInt("width",w);result.putInt("height",h);result.putInt("page",number);result.putInt("pages",pages);
                    send(reply,result);
                }
            }finally{bitmap.recycle();}
        }
    }

    private static void sendError(Messenger reply,AttachmentText.Problem p){
        Bundle b=new Bundle();b.putString("error",p.name());send(reply,b);
    }
    private static void send(Messenger reply,Bundle b){
        if(reply==null)return;
        Message msg=Message.obtain(null,RESULT);msg.setData(b);
        try{reply.send(msg);}catch(RemoteException ignored){}
    }
    @Override public IBinder onBind(Intent intent){return incoming.getBinder();}
    @Override public boolean onUnbind(Intent intent){
        // A unique instance is created per parse. Do not leave an abandoned native call running.
        android.os.Process.killProcess(android.os.Process.myPid());
        return false;
    }
    @Override public void onDestroy(){worker.shutdownNow();watchdog.shutdownNow();super.onDestroy();}
}
