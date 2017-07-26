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
import android.widget.ImageView;

/** 
 * runnable executed on background thread that loads an image via a processor
 * and sends a message back to the UI thread handler
 */
/* default */ class LoadTaskExecutor implements Runnable {
    private final LoadTaskItem mTaskItem;

    public LoadTaskExecutor(LoadTaskItem task) {
        mTaskItem = task;
    }

    public void run() {
        ImageView view = mTaskItem.getViewIfValid();
        if (view != null) {
            mTaskItem.putThreadMapping();
            // get bitmap
            mTaskItem.imageProcessor.loadBitmap(mTaskItem);
            Bitmap bitmap = mTaskItem.result.bitmap;

            // debug sleep
            if (mTaskItem.sleep) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // after loading check again if task is valid
            boolean useBitmap = false;
            if (!Thread.interrupted() && mTaskItem.getViewIfValid() != null) {
                useBitmap = true;
                if (mTaskItem.cache != null && bitmap != null) {
                    // cache does not like null, so don't put that there
                    mTaskItem.cache.put(mTaskItem.key, bitmap);
                }
                mTaskItem.reply.obj = mTaskItem;
                mTaskItem.reply.sendToTarget();
            }
            if (!useBitmap && bitmap != null) {
                bitmap.recycle();
            }

            mTaskItem.removeThreadMapping();
        }
    }
}