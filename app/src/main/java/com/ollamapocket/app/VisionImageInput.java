package com.ollamapocket.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Looper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Worker-only visual input preparation. Caller owns the source stream; no URI or bytes persist. */
final class VisionImageInput {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private VisionImageInput() { }

    static VisionImagePolicy.Prepared prepare(InputStream source, BooleanSupplier cancelled) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IOException("Vision input requires a worker");
        if (!RUNNING.compareAndSet(false,true)) throw new IOException("Vision input busy");
        Bitmap bitmap = null, opaque = null;
        try {
            byte[] data = VisionImagePolicy.read(source,cancelled);
            VisionImagePolicy.Header header = VisionImagePolicy.inspect(data);
            VisionImagePolicy.check(cancelled);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            if (!header.matchesDecoder(bounds.outMimeType,bounds.outWidth,bounds.outHeight))
                throw new IOException("Vision decoder metadata mismatch");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = VisionImagePolicy.sample(bounds.outWidth,bounds.outHeight);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inScaled = false;
            VisionImagePolicy.check(cancelled);
            bitmap = BitmapFactory.decodeByteArray(data,0,data.length,options);
            if (bitmap == null || !VisionImagePolicy.validOutput(bitmap.getWidth(),bitmap.getHeight()))
                throw new IOException("Invalid sampled image");
            VisionImagePolicy.check(cancelled);
            int exif = new ExifInterface(new ByteArrayInputStream(data))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = orientation(exif);
            if (!matrix.isIdentity()) {
                Bitmap next = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                if (next != bitmap) { bitmap.recycle(); bitmap = next; }
            }
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            if (!VisionImagePolicy.validOutput(w,h)) throw new IOException("Invalid oriented image");
            VisionImagePolicy.check(cancelled);
            // Fresh pixels, not the source container: flatten transparency onto white.
            opaque = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            opaque.setDensity(Bitmap.DENSITY_NONE);
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap,0,0,null);
            bitmap.recycle(); bitmap = null;
            opaque.setHasAlpha(false);
            VisionImagePolicy.BoundedOutput out = new VisionImagePolicy.BoundedOutput(VisionImagePolicy.MAX_OUTPUT_BYTES);
            VisionImagePolicy.check(cancelled);
            if (!opaque.compress(Bitmap.CompressFormat.JPEG,88,out)) throw new IOException("Vision JPEG encoding failed");
            VisionImagePolicy.check(cancelled);
            VisionImagePolicy.Prepared prepared = VisionImagePolicy.finish(out.bytes(),w,h);
            VisionImagePolicy.check(cancelled);
            return prepared;
        } catch (OutOfMemoryError e) {
            throw new IOException("Vision image allocation failed",e);
        } catch (RuntimeException e) {
            throw new IOException("Vision image preparation failed",e);
        } finally {
            try {
                if (bitmap != null) bitmap.recycle();
            } finally {
                try { if (opaque != null) opaque.recycle(); }
                finally { RUNNING.set(false); }
            }
        }
    }

    private static Matrix orientation(int value) throws IOException {
        Matrix m = new Matrix();
        switch(value) {
            case ExifInterface.ORIENTATION_UNDEFINED:
            case ExifInterface.ORIENTATION_NORMAL: break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.setScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.setScale(1,-1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.setRotate(90); m.postScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: m.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.setRotate(-90); m.postScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.setRotate(-90); break;
            default: throw new IOException("Unsupported EXIF orientation");
        }
        return m;
    }
}
