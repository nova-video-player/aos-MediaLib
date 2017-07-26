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

import android.graphics.drawable.Drawable;
import android.widget.ImageView;


/**
 * Base class for loading and setting images
 */
public abstract class ImageProcessor {

    /**
     * Implementation's task is to load the image defined in taskItem.loadObject
     * and set taskItem.result.bitmap & taskItem.result.status<br>
     * This is executed on a background thread, don't touch UI.<br>
     * to find out if loading can be aborted call {@link LoadTaskItem#taskStillValid()}
     */
    public abstract void loadBitmap(LoadTaskItem taskItem);

    /**
     * return true if loadObject is something you can handle. Returning false results in
     * {@link #handleLoadError(ImageView, LoadTaskItem)} getting called and if that 
     * returns false {@link #setLoadingDrawable(ImageView, Drawable)} is called.
     */
    public abstract boolean canHandle(Object loadObject);

    /**
     * Called if you can handle that object to get a key uniquely identifying the image to load
     * @return a key or null if there is no key to load, see {@link #canHandle(Object)} for further flow
     */
    public abstract String getKey(Object loadObject);

    /**
     * Called on the UI thread after successfully loading (taskItem.result.status), overwrite
     * in case there are other things to do besides setting taskItem.result.bitmap for imageView
     * @param imageView the target imageView
     * @param taskItem taskItem.result.bitmap is the bitmap to set
     */
    public void setResult(ImageView imageView, LoadTaskItem taskItem) {
        imageView.setImageBitmap(taskItem.result.bitmap);
    }

    /**
     * called on the UI thread to check if {@link #setLoadingDrawable(ImageView, Drawable)} should be executed.
     * Defaults to false, overwrite if you'd like to do other things, basically here for ChainProcessor to work
     * @param imageView target ImageView
     * @param taskItem current state of the task, result.status is != LOAD_OK
     * @return true if you wish to handle things yourself
     */
    public boolean handleLoadError(ImageView imageView, LoadTaskItem taskItem) {
        // default to not handled
        return false;
    }

    /**
     * Called on the UI thread to set the loading drawable, overwrite if you have additional tasks to do
     * @param imageView
     * @param drawable
     */
    public void setLoadingDrawable(ImageView imageView, Drawable drawable) {
        imageView.setImageDrawable(drawable);
    }

}
