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

package com.archos.medialib;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Matrix;
import android.util.Log;

public class AvosBitmapHelper {

    public static Bitmap createRGBBitmap(int[] inData,
            int inWidth, int inHeight, int inLinestep,
            int inRotation,
            int outWidth, int outHeight) {

        Log.d("AvosBitmapHelper", "createRGBBitmap("+inData.length+","+inWidth+","+inHeight+","+inLinestep+","+inRotation+","+outWidth+","+outHeight+")");
        if (inWidth == 0 || inHeight == 0)
            return null;
        if (outWidth == 0 || outHeight == 0) {
            outWidth = inWidth;
            outHeight = inHeight;
        }

        Config config = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = Bitmap.createBitmap(inData, 0, inLinestep, inWidth, inHeight, config);
        if (bitmap == null)
            return null;

        if (inRotation != 0) {
            if (inRotation == 90 || inRotation == 270) {
                int tmp = outWidth;
                outWidth = outHeight;
                outHeight = tmp;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(inRotation);
            Bitmap rotatedBitmap =  Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotatedBitmap != bitmap)
                bitmap.recycle();
            bitmap = rotatedBitmap;
        }

        if (outWidth != inWidth || outHeight != inHeight) {
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true);
            if (scaledBitmap != bitmap)
                bitmap.recycle();
            bitmap = scaledBitmap;
        }

        return bitmap;
    }
}
