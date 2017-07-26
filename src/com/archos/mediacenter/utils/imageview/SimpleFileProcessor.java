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

package com.archos.mediacenter.utils.imageview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.archos.mediacenter.utils.BitmapUtils;
import com.archos.mediacenter.utils.imageview.LoadResult.Status;

/** ImageProcessor loading images from files, expects the path to the image as String */
public class SimpleFileProcessor extends ImageProcessor {
    private final boolean mScale;
    private final int mWidth;
    private final int mHeight;

    /**
     * Expects the path to the image as String
     * @param downscale will use {@link BitmapUtils#scaleThumbnailCenterCrop(Bitmap, int, int)} if true
     * @param width for downscaling
     * @param height for downscaling
     */
    public SimpleFileProcessor(boolean downscale, int width, int height) {
        mScale = downscale;
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void loadBitmap(LoadTaskItem taskItem) {
        if (taskItem.loadObject instanceof String) {
            String file = (String) taskItem.loadObject;
            Bitmap bm = BitmapFactory.decodeFile(file);
            if (mScale && bm != null) {
                bm = BitmapUtils.scaleThumbnailCenterCrop(bm, mWidth, mHeight);
            }
            taskItem.result.bitmap = bm;
            taskItem.result.status = (bm != null) ? Status.LOAD_OK : Status.LOAD_ERROR;
        } else {
            taskItem.result.status = Status.LOAD_BAD_OBJECT;
        }
    }

    @Override
    public boolean canHandle(Object loadObject) {
        return loadObject instanceof String;
    }

    @Override
    public String getKey(Object loadObject) {
        return loadObject instanceof String ? (String) loadObject : null;
    }
}
