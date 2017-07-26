// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediascraper;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Scales Files to a certain size, if image is already inside max bounds no further scaling happens */
public class ImageScaler {
    private static final boolean DBG = false;
    private static final String TAG = "ImageScaler";

    public enum Type {
        /** No scaling happens, just copies the file if it is a decodeable image */
        SCALE_NONE,
        /** At least 1 bound is <= the max bound, image may be wider or taller */
        SCALE_OUTSIDE,
        /** coarse outside fit by downscaling in 2^n steps */
        SCALE_OUTSIDE_COARSE,
        /** downscaled {@link #SCALE_OUTSIDE}, overlap cropped away */
        SCALE_CENTER_CROP,
        /** Both bounds are <= the max bound */
        SCALE_INSIDE,
        /** coarse inside fit by downscaling in 2^n steps */
        SCALE_INSIDE_COARSE,
    }

    public static boolean scale(Uri rawFile, String targetName, int maxWidth, int maxHeight, Type scaling) {
        if (DBG) Log.d(TAG, "scale " + scaling.name() + " to (" + maxWidth + "," + maxHeight + ")");
        DebugTimer dbgTimer = null;
        if (DBG) dbgTimer = new DebugTimer();

        boolean inside;
        boolean crop;
        boolean coarse;
        boolean noscale;
        switch (scaling) {
            case SCALE_INSIDE_COARSE:
                inside = true;
                crop = false;
                coarse = true;
                noscale = false;
                break;
            case SCALE_INSIDE:
                inside = true;
                crop = false;
                coarse = false;
                noscale = false;
                break;
            case SCALE_CENTER_CROP:
                inside = false;
                crop = true;
                coarse = false;
                noscale = false;
                break;
            case SCALE_OUTSIDE:
                inside = false;
                crop = false;
                coarse = false;
                noscale = false;
                break;
            case SCALE_OUTSIDE_COARSE:
                inside = false;
                crop = false;
                coarse = true;
                noscale = false;
                break;
            case SCALE_NONE:
            default:
                inside = false;
                crop = false;
                coarse = false;
                noscale = true;
        }

        // the usual path, decode bounds and check size
        Options opts = new Options();
        opts.inJustDecodeBounds = true;
        Bitmap tmp;
        FileEditor fileEditor = FileEditorFactoryWithUpnp.getFileEditorForUrl(rawFile, null);
        InputStream stream = null;
        try {
            stream = fileEditor.getInputStream();
            tmp = BitmapFactory.decodeStream(stream, null, opts);
        } catch (IOException e1) {
            Log.e(TAG, "Could not decode Bitmap " + rawFile.toString());
            tmp = null;
        } catch (Exception e) {
            Log.e(TAG, "Could not decode Bitmap " + rawFile.toString());
            tmp = null;
        }finally {
            if(stream!=null)
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        if (tmp != null) {
            tmp.recycle();
            tmp = null;
        }

        int height = opts.outHeight;
        int width = opts.outWidth;
        if (height <= 0 || width <= 0) {
            Log.e(TAG, "JustDecodeBounds failed for " + rawFile.toString());
            return false;
        }
        if (DBG) Log.d(TAG, dbgTimer.step() + " JustDecodeBounds, size is (" + width + "," + height + ")");

        // if image smaller than bounds, just copy it
        if (noscale || (height <= maxHeight && width <= maxWidth)) {
            if (DBG) Log.d(TAG, "Image smaller than bounds, copy it instead of resampling.");
            boolean success = copyFile(rawFile, targetName);
            if (DBG) Log.d(TAG, dbgTimer.step() + " copyFile, result is " + success);
            if (DBG) Log.d(TAG, dbgTimer.total() + " scale() in total");
            return success;
        }

        opts.inSampleSize = getSampleSize(width, height, maxWidth, maxHeight, inside);
        opts.inJustDecodeBounds = false;

        try {
            Bitmap sampled;
            try {
                stream = fileEditor.getInputStream();
                sampled = BitmapFactory.decodeStream(stream, null, opts);
            } catch (Exception e) {
                Log.e(TAG, "Could not decode Bitmap " + rawFile.toString());
                sampled = null;
            }finally {
                if(stream!=null)
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
            if (sampled == null) {
                Log.e(TAG, "decode SampleSize failed for " + rawFile.toString());
                return false;
            }

            height = sampled.getHeight();
            width = sampled.getWidth();
            if (DBG) Log.d(TAG, dbgTimer.step() + " decode SampleSize(" + opts.inSampleSize + "), size is (" + width + "," + height + ")");

            if (coarse) {
                // save result
                boolean saveOk = saveToFile(targetName, sampled);
                if (DBG) Log.d(TAG, dbgTimer.step() + " saving file, result is:" + saveOk);
                if (DBG) Log.d(TAG, dbgTimer.total() + " scale() in total");
                return saveOk;
            }

            // finally scale & crop to size
            boolean needsScale = false;
            boolean needsCrop = false;
            int scaledWidth = width;
            int scaledHeight = height;
            float scale = 1;
            // scale down
            if (inside) {
                if (height > maxHeight || width > maxWidth) {
                    float scaleX = maxHeight / (float) height;
                    float scaleY = maxWidth / (float) width;
                    scale = Math.min(scaleX, scaleY);
                    scaledWidth = (int) (width * scale);
                    scaledHeight = (int) (height * scale);
                    needsScale = true;
                }
            } else {
                if (height > maxHeight && width > maxWidth) {
                    float scaleX = maxHeight / (float) height;
                    float scaleY = maxWidth / (float) width;
                    scale = Math.max(scaleX, scaleY);
                    scaledWidth = (int) (width * scale);
                    scaledHeight = (int) (height * scale);
                    needsScale = true;
                }
            }
            // crop overlap
            int xOffs = 0;
            int decodeWidth = width;
            int yOffs = 0;
            int decodeHeight = height;
            if (crop) {
                if (scaledWidth > maxWidth || scaledHeight > maxHeight) {
                    // compute resulting scaled size
                    int resultWidth = Math.min(scaledWidth, maxWidth);
                    int resultHeight = Math.min(scaledHeight, maxHeight);
                    // compute in source size - rounded
                    decodeWidth = (int) (resultWidth / scale + 0.5f);
                    decodeHeight = (int) (resultHeight / scale + 0.5f);
                    // compute offset in source size
                    xOffs = (width - decodeWidth) / 2;
                    yOffs = (height - decodeHeight) / 2;
                    needsCrop = true;
                }
            }
            Bitmap scaled = null;
            if (needsScale || needsCrop) {
                // scale matrix if scaling is required
                Matrix m = null;
                if (needsScale) {
                    m = new Matrix();
                    m.setScale(scale, scale);
                }
                if (DBG) Log.d(TAG, "#Scale:" + scale + " #Crop xOffs:" + xOffs + " w:" + decodeWidth + ", yOffs:" + yOffs + " h:" + decodeHeight);
                scaled = Bitmap.createBitmap(sampled, xOffs, yOffs, decodeWidth, decodeHeight, m, true);
                sampled.recycle();
                sampled = null;
                height = scaled.getHeight();
                width = scaled.getWidth();
                if (DBG) Log.d(TAG, dbgTimer.step() + " Scale/Crop, size is (" + width + "," + height + ")");
            } else {
                // no need to scale / crop, just use image from previous step
                scaled = sampled;
                sampled = null;
            }

            // save result
            boolean saveOk = saveToFile(targetName, scaled);
            if (DBG) Log.d(TAG, dbgTimer.step() + " saving file, result is:" + saveOk);
            if (DBG) Log.d(TAG, dbgTimer.total() + " scale() in total");
            return saveOk;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "scale() failed due to OutOfMemoryError");
            return false;
        }
    }

    private static boolean saveToFile(String targetName, Bitmap image) {
        boolean saveOk = false;
        File saveFile = createFile(targetName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(saveFile);
            image.compress(CompressFormat.JPEG, 90, fos);
            fos.close();
            fos = null;
            saveOk = true;
        } catch (FileNotFoundException e) {
            Log.w(TAG, e);
            // delete failed attempts
            saveFile.delete();
        } catch (IOException e) {
            Log.w(TAG, e);
            // delete failed attempts
            saveFile.delete();
        } finally {
            IOUtils.closeSilently(fos);
        }
        image.recycle();
        return saveOk;
    }

    private static File createFile(String path) {
        if (DBG) Log.d(TAG, "createFile: " + path);
        File f = new File(path);
        if (f.exists())
            return f;
        try {
            f.createNewFile();
            f.setReadable(true, false);
        } catch (IOException e) {
            Log.e(TAG, "could not create file:" + path, e);
        }
        return f;
    }

    private static boolean copyFile(Uri src, String dest) {
        byte[] buf = new byte[8192];
        FileOutputStream fos = null;
        InputStream fis = null;
        try {

            FileEditor fileEditor = FileEditorFactoryWithUpnp.getFileEditorForUrl(src,null);
            fos = new FileOutputStream(dest);
            fis = fileEditor.getInputStream();
            int read;
            while ((read = fis.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
            fos.close();
            //Log.d(TAG, "copying file for : " + dest);
            new File(dest).setReadable(true, false);
            fos = null;
        } catch (Exception e) {
            return false;
        } finally {
            IOUtils.closeSilently(fos);
            IOUtils.closeSilently(fis);
        }
        return true;
    }

    private static int getSampleSize(int originalWidth, int originalHeight, int targetWidth, int targetHeight, boolean inside) {
        if (inside) return getSampleSizeInside(originalWidth, originalHeight, targetWidth, targetHeight);
        // else
        return getSampleSizeOutside(originalWidth, originalHeight, targetWidth, targetHeight);
    }

    private static int getSampleSizeOutside(int originalWidth, int originalHeight, int targetWidth, int targetHeight) {
        int sampleSize = 1;
        while ((originalWidth / sampleSize >= targetWidth * 2)
                && (originalHeight / sampleSize >= targetHeight * 2)) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static int getSampleSizeInside(int originalWidth, int originalHeight, int targetWidth, int targetHeight) {
        int sampleSize = 1;
        while ((originalWidth / sampleSize >= targetWidth * 2)
                || (originalHeight / sampleSize >= targetHeight * 2)) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
