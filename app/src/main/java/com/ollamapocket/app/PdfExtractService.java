package com.ollamapocket.app;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.content.PdfPageTextContent;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-exported isolated PDF worker. Receives only a read-only FD and returns a bounded excerpt. */
@TargetApi(35)
public final class PdfExtractService extends Service {
    static final int READ=1,RESULT=2;
    static final long WATCHDOG_SECONDS=35;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean used=new AtomicBoolean();
    private final Messenger incoming=new Messenger(new Handler(Looper.getMainLooper(),this::receive));

    private boolean receive(Message msg){
        if(msg.what!=READ)return false;
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
            worker.execute(()->extract(input,reply));
        }catch(Exception e){if(fd!=null)try{fd.close();}catch(Exception ignored){}sendError(reply,AttachmentText.Problem.INVALID);}
        return true;
    }
    private void extract(ParcelFileDescriptor input,Messenger reply){
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
                PdfExcerpt.Builder excerpt=new PdfExcerpt.Builder(renderer.getPageCount());
                for(int i=0;excerpt.canReadPage();i++){
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
                b.putInt("pages",r.pdfPages);b.putInt("examined",r.pdfExamined);b.putInt("textPages",r.pdfTextPages);
                send(reply,b);
            }
        }catch(SecurityException e){sendError(reply,AttachmentText.Problem.PDF_PROTECTED);}
        catch(AttachmentText.Failure e){sendError(reply,e.problem);}
        catch(Exception e){sendError(reply,AttachmentText.Problem.INVALID);}
        finally{deadline.cancel(false);try{input.close();}catch(Exception ignored){}}
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
