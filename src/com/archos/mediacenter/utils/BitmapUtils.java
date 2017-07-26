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

package com.archos.mediacenter.utils;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.Log;


public class BitmapUtils {
    private final static String TAG = "BitmapUtils";
    private final static boolean DBG = false;

    // Used to create bigger thumbnails in poster format (1.0f is the default value)
    private final static float THUMBNAIL_INCREASE_FACTOR = 1.5f;


    /**
    * Scale the original image uniformly so that both dimensions of the resized image will be
    * equal or less than the display area. Empty areas of the destination image are filled with black.
    * Use THUMBNAIL_INCREASE_FACTOR to create bigger thumbnails (left and right parts are then cropped)
    */
    public static Bitmap scaleThumbnailCenterInside(Bitmap original, int displayWidth, int displayHeight) {
        Bitmap thumb;
        float scale;
        Matrix matrix = new Matrix();

        final int originalWidth = original.getWidth();
        final int originalHeight = original.getHeight();
        if (DBG) Log.d(TAG, "Source size =" + originalWidth + "x" + originalHeight);

        // Resize the source thumbnail to its final size
        scale = (float)displayWidth * THUMBNAIL_INCREASE_FACTOR / (float)originalWidth;
        matrix.setScale(scale, scale);
        final Bitmap resized = Bitmap.createBitmap(original, 0, 0, originalWidth, originalHeight, matrix, true);
        original.recycle();

        // Get the size of the resized thumbnail
        final int resizedWidth = resized.getWidth();
        final int resizedHeight = resized.getHeight();
        if (DBG) Log.d(TAG, "resized thumbnail size=" + resizedWidth + "x" + resizedHeight);

        // Create the destination bitmap and fill it with black
        thumb = Bitmap.createBitmap(displayWidth, displayHeight, Config.RGB_565);
        thumb.eraseColor(Color.BLACK);

        // Compute the offset of the resized thumbnail so that it is centered inside the destination bitmap
        int xOffset = (int) ((displayWidth - resizedWidth) / 2);
        int yOffset = (int) ((displayHeight - resizedHeight) / 2);
        if (DBG) Log.d(TAG, "resized thumbnail position in destination bitmap : xOffset=" + xOffset + " yOffset=" + yOffset);

        // Copy the resized thumbnail to the destination bitmap
        Canvas canvas = new Canvas(thumb);
        canvas.drawBitmap(resized, xOffset, yOffset, null);
        resized.recycle();

        return thumb;
    }

    /**
    * Scale the original image uniformly so that both dimensions of the resized image will be
    * equal or larger than the display area, then crop the parts outside the destination image.
    */
    public static Bitmap scaleThumbnailCenterCrop(Bitmap original, int displayWidth, int displayHeight) {
        Bitmap thumb;
        float scale;
        Matrix matrix = new Matrix();

        // Compute the destination bitmap aspect ratio
        final float displayAspectRatio = (float) displayWidth / displayHeight;

        // Check if the source bitmap is wider or higher than the expected aspect ratio
        final int originalWidth = original.getWidth();
        final int originalHeight = original.getHeight();
        if (DBG) Log.d(TAG, "Source size =" + originalWidth + "x" + originalHeight);
    
        final float ratio = (float) originalWidth / originalHeight;
        if (ratio < displayAspectRatio) {
            // The source bitmap is higher than the display area => resize the bitmap
            // to match the width of the display area and crop the top and bottom areas
            scale = (float) displayWidth / originalWidth;
        }
        else {
            // The source bitmap is wider than the display area => resize the bitmap
            // to match the height of the display area and crop the left and right parts
            scale = (float) displayHeight / originalHeight;
        }
        matrix.setScale(scale, scale);
        final Bitmap resized = Bitmap.createBitmap(original, 0, 0, originalWidth, originalHeight, matrix, true);
        // Free the original bitmap ASAP if we made a copy (Caution!!! createBitmap() doesn't always make a copy)
        if (resized != original) {
            original.recycle();
        }

        // Get the size of the resized thumbnail
        final int resizedWidth = resized.getWidth();
        final int resizedHeight = resized.getHeight();
        if (DBG) Log.d(TAG, "resized thumbnail size=" + resizedWidth + "x" + resizedHeight);

        // Compute the offset of the resized thumbnail so that it is centered with the the destination bitmap
        int xOffset = 0;
        int yOffset = 0;
        if (resizedWidth > displayWidth) {
            xOffset = (int) ((resizedWidth - displayWidth) / 2);
        }
        if (resizedHeight > displayHeight) {
            yOffset = (int) ((resizedHeight - displayHeight) / 2);
        }
        if (DBG) Log.d(TAG, "resized thumbnail position in destination bitmap : xOffset=" + xOffset + " yOffset=" + yOffset);

        // Copy the resized thumbnail cropped to the destination bitmap
        thumb = Bitmap.createBitmap(resized, xOffset, yOffset, displayWidth, displayHeight);
        // Free the original bitmap ASAP if we made a copy (Caution!!! createBitmap() doesn't always make a copy)
        if (thumb != resized) {
            resized.recycle();
        }

        return thumb;
    }
}
 
