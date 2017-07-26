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


import java.util.ArrayList;

/**
 * Class that allows to chain other ImageProcessors where they are tasked to load
 * their images and if one fails the next one is tasked and so on.
 * <pre>
 * <code>
 *     // example will try to set loadObject1 via p1 if that fails from p2, then
 *     // something from p1 again, if all fails the default image is used
 *     ImageViewSetter setter = new ImageViewSetter(context, null);
 *     ChainProcessor cp = new ChainProcessor(setter);
 *     ProcessorX p1 = new ProcessorX();
 *     ProcessorY p2 = new ProcessorY()
 *     ...
 *     setter.set(imgView, cp, ChainProcessor
 *             .newChain(p1, loadObject1)
 *             .nextIs(p2, loadObject2)
 *             .nextIs(p1, loadObject3)
 *               );
 * </code></pre>
 *
 */
public class ChainProcessor extends ImageProcessor {

    public static class ChainItem {
        public final ImageProcessor processor;
        public final Object object;
        public ChainItem(ImageProcessor subProcessor, Object loadItem) {
            processor = subProcessor;
            object = loadItem;
        }
    }

    private final ImageViewSetter mSetter;

    public static class LoaderChain {
        public final ArrayList<ChainItem> list;
        public int currentLoader;
        /* default */ LoaderChain() {
            list = new ArrayList<ChainProcessor.ChainItem>();
            currentLoader = 0;
        }

        /** specify the next thing to load, can add several */
        public LoaderChain nextIs(ImageProcessor processor, Object loadObject) {
            list.add(new ChainItem(processor, loadObject));
            return this;
        }
    }


    public ChainProcessor(ImageViewSetter setter) {
        mSetter = setter;
    }

    /** creates a new {@link LoaderChain}, specifying the first item to try to load */
    public static LoaderChain newChain(ImageProcessor processor, Object loadObject) {
        LoaderChain chain = new LoaderChain();
        chain.nextIs(processor, loadObject);
        return chain;
    }

    private static ChainItem getCurrentItem(Object loadObject) {
        if (loadObject instanceof LoaderChain) {
            LoaderChain chain = (LoaderChain) loadObject;
            if (chain.list.size() > chain.currentLoader) {
                return chain.list.get(chain.currentLoader);
            }
        }
        return null;
    }

    @Override
    public void loadBitmap(LoadTaskItem taskItem) {
        ChainItem chainItem = getCurrentItem(taskItem.loadObject);
        if (chainItem != null) {
            LoadTaskItem subItem = taskItem.duplicate();
            subItem.loadObject = chainItem.object;
            subItem.imageProcessor = chainItem.processor;

            chainItem.processor.loadBitmap(subItem);

            taskItem.result.bitmap = subItem.result.bitmap;
            taskItem.result.status = subItem.result.status;
        }
    }

    @Override
    public String getKey(Object loadObject) {
        ChainItem chainItem = getCurrentItem(loadObject);
        String key = null;
        if (chainItem != null) {
            key = chainItem.processor.getKey(chainItem.object);
        }
        return key;
    }

    @Override
    public boolean handleLoadError(ImageView imageView, LoadTaskItem taskItem) {
        if (taskItem.loadObject instanceof LoaderChain) {
            LoaderChain chain = (LoaderChain) taskItem.loadObject;
            if (chain.list.size() > chain.currentLoader + 1) {
                // load with next loader if exists
                chain.currentLoader++;
                mSetter.set(imageView, this, chain);
                return true;
            } else if (chain.list.size() > chain.currentLoader) {
                // this is the last loader, use it's handleError
                return chain.list.get(chain.currentLoader).processor.handleLoadError(imageView, taskItem);
            }
        }
        // should not happen
        return false;
    }

    @Override
    public void setLoadingDrawable(ImageView imageView, Drawable drawable) {
        super.setLoadingDrawable(imageView, drawable);
    }

    @Override
    public void setResult(ImageView imageView, LoadTaskItem taskItem) {
        ChainItem chainItem = getCurrentItem(taskItem.loadObject);
        if (chainItem != null) {
            chainItem.processor.setResult(imageView, taskItem);
        } else {
            super.setResult(imageView, taskItem);
        }
    }

    @Override
    public boolean canHandle(Object loadObject) {
        ChainItem chainItem = getCurrentItem(loadObject);
        // we can handle it if we have a loader chained up
        if (chainItem != null) {
            return true;
        }
        return false;
    }

}

