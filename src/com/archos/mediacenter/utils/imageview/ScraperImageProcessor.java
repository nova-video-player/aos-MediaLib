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

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.archos.mediacenter.utils.imageview.LoadResult.Status;
import com.archos.mediascraper.ScraperImage;

public class ScraperImageProcessor extends ImageProcessor {
    private final Context mContext;

    public ScraperImageProcessor(Context context) {
        mContext = context;
    }

    @Override
    public void loadBitmap(LoadTaskItem taskItem) {
        if (taskItem.loadObject instanceof ScraperImage) {
            ScraperImage image = (ScraperImage) taskItem.loadObject;
            String file = image.getLargeFile();
            if (file != null) {
                image.download(mContext);
                taskItem.result.bitmap = BitmapFactory.decodeFile(file);
            }
            taskItem.result.status = taskItem.result.bitmap != null ?
                    Status.LOAD_OK : Status.LOAD_ERROR;
        } else {
            taskItem.result.status = Status.LOAD_BAD_OBJECT;
        }
    }

    @Override
    public boolean canHandle(Object loadObject) {
        return loadObject instanceof ScraperImage;
    }

    @Override
    public String getKey(Object loadObject) {
        if (loadObject instanceof ScraperImage) {
            ScraperImage image = (ScraperImage) loadObject;
            // using the url here since images from same url may be used
            // as different files. But for cache reasons urls are a better
            // key
            return image.getLargeUrl();
        }
        return null;
    }
    @Override
    public void setResult(ImageView imageView, LoadTaskItem taskItem) {
        imageView.setScaleType(ScaleType.FIT_CENTER);
        super.setResult(imageView, taskItem);
    }
    @Override
    public void setLoadingDrawable(ImageView imageView, Drawable drawable) {
        imageView.setScaleType(ScaleType.CENTER_INSIDE);
        super.setLoadingDrawable(imageView, drawable);
    }
}
