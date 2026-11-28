package com.ollamapocket.app;

import android.graphics.*;
import android.media.ExifInterface;
import android.os.Looper;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bundled on-device OCR. Only one OCR task may own a bitmap at once, including after timeout. */
final class ImageOcr {
    private static final AtomicBoolean running=new AtomicBoolean();
    private ImageOcr(){}
    static AttachmentText.Result read(String name,InputStream source,OcrPolicy.Script script)throws IOException{
        if(!OcrPolicy.image(name)||source==null)throw new AttachmentText.Failure(AttachmentText.Problem.UNSUPPORTED);
        if(!running.compareAndSet(false,true))throw new AttachmentText.Failure(AttachmentText.Problem.OCR_BUSY);
        Bitmap bitmap=null;boolean handedOff=false;
        try{
            byte[] data=bounded(source);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            if(!"image/jpeg".equals(bounds.outMimeType)&&!"image/png".equals(bounds.outMimeType)&&!"image/webp".equals(bounds.outMimeType))
                throw new AttachmentText.Failure(AttachmentText.Problem.INVALID);
            BitmapFactory.Options opt=new BitmapFactory.Options();opt.inPreferredConfig=Bitmap.Config.ARGB_8888;
            try{opt.inSampleSize=OcrPolicy.sample(bounds.outWidth,bounds.outHeight);}
            catch(IllegalArgumentException e){throw new AttachmentText.Failure(AttachmentText.Problem.TOO_LARGE);}
            bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opt);
            if(bitmap==null||!OcrPolicy.validBitmap(bitmap.getWidth(),bitmap.getHeight()))
                throw new AttachmentText.Failure(AttachmentText.Problem.INVALID);
            int orientation=ExifInterface.ORIENTATION_NORMAL;
            try{orientation=new ExifInterface(new ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}
            catch(IOException ignored){}
            Matrix transform=orientation(orientation);
            if(!transform.isIdentity()){
                Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),transform,true);
                if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}
            }
            Bitmap owned=bitmap;bitmap=null;handedOff=true;
            return recognizeOwned(name,owned,script,0,0);
        }catch(OutOfMemoryError e){throw new AttachmentText.Failure(AttachmentText.Problem.TOO_LARGE);}
        finally{
            if(bitmap!=null)bitmap.recycle();
            if(!handedOff)running.set(false);
        }
    }
    static AttachmentText.Result readPage(String name,Bitmap bitmap,OcrPolicy.Script script,int page,int pages)throws IOException{
        if(!running.compareAndSet(false,true)){bitmap.recycle();throw new AttachmentText.Failure(AttachmentText.Problem.OCR_BUSY);}
        return recognizeOwned(name,bitmap,script,page,pages);
    }
    private static AttachmentText.Result recognizeOwned(String name,Bitmap bitmap,OcrPolicy.Script script,int page,int pages)throws IOException{
        TextRecognizer recognizer=null;boolean ownedByTask=false;
        try{
            if(Looper.myLooper()==Looper.getMainLooper()||!OcrPolicy.validBitmap(bitmap.getWidth(),bitmap.getHeight()))
                throw new AttachmentText.Failure(AttachmentText.Problem.INVALID);
            switch(script){
                case KOREAN:recognizer=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());break;
                case CHINESE:recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());break;
                case JAPANESE:recognizer=TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());break;
                default:recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            }
            Task<Text> task=recognizer.process(InputImage.fromBitmap(bitmap,0));
            final TextRecognizer close=recognizer;
            task.addOnCompleteListener(Runnable::run,done->{
                try{close.close();}finally{bitmap.recycle();running.set(false);}
            });
            ownedByTask=true;
            Text result=Tasks.await(task,30,TimeUnit.SECONDS);
            return OcrPolicy.result(name,result.getText(),page,pages);
        }catch(TimeoutException e){throw new AttachmentText.Failure(AttachmentText.Problem.OCR_TIMEOUT);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}
        catch(AttachmentText.Failure e){throw e;}
        catch(Exception e){throw new AttachmentText.Failure(AttachmentText.Problem.OCR_FAILED);}
        finally{
            // On timeout, do not recycle an input still in use by the SDK. Its completion listener owns cleanup.
            if(!ownedByTask){
                try{if(recognizer!=null)recognizer.close();}finally{bitmap.recycle();running.set(false);}
            }
        }
    }
    private static byte[] bounded(InputStream source)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        AttachmentText.BoundedSource bounded=new AttachmentText.BoundedSource(source,OcrPolicy.MAX_BYTES);
        while((n=bounded.read(buf))!=-1)out.write(buf,0,n);
        return out.toByteArray();
    }
    private static Matrix orientation(int value){
        Matrix m=new Matrix();
        switch(value){
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:m.setScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_180:m.setRotate(180);break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:m.setScale(1,-1);break;
            case ExifInterface.ORIENTATION_TRANSPOSE:m.setRotate(90);m.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_90:m.setRotate(90);break;
            case ExifInterface.ORIENTATION_TRANSVERSE:m.setRotate(-90);m.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_270:m.setRotate(-90);break;
            default:break;
        }
        return m;
    }
}
