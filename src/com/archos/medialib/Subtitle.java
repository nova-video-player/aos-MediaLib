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
import android.graphics.Rect;

public abstract class Subtitle {
    private final int type;
    private static final int TYPE_TEXT_SUBTITLE = 1;
    private static final int TYPE_TIMED_TEXT_SUBTITLE = 2;
    private static final int TYPE_TIMED_BITMAP_SUBTITLE = 3;

    public Subtitle(int type) {
        this.type = type;
    }

    public boolean isTimed() {
        return this.type == TYPE_TIMED_TEXT_SUBTITLE || this.type == TYPE_TIMED_BITMAP_SUBTITLE;
    }
    
    public boolean isText() {
        return this.type == TYPE_TIMED_TEXT_SUBTITLE || this.type == TYPE_TEXT_SUBTITLE;
    }
    
    public boolean isBitmap() {
        return this.type == TYPE_TIMED_BITMAP_SUBTITLE;
    }
    
    public abstract String getText();
    public abstract Bitmap getBitmap();
    public abstract Rect getBounds();
    public abstract int getPosition();
    public abstract int getDuration();

    public static class TextSubtitle extends Subtitle {
        private final String text;
        public TextSubtitle(String text) {
            super(TYPE_TEXT_SUBTITLE);
            this.text = text;
        }

        public String getText() {
            return this.text;
        }
        public Bitmap getBitmap() {
            return null;
        }
        public Rect getBounds() {
            return null;
        }
        public int getPosition() {
            return -1;
        }
        public int getDuration() {
            return -1;
        }
    }

    public abstract static class Timed extends Subtitle {
        private final int position;
        private final int duration;

        public Timed(int type, int position, int duration) {
            super(type);
            this.position = position;
            this.duration = duration;
        }

        public int getPosition() {
            return this.position;
        }
        public int getDuration() {
            return this.duration;
        }
    }

    public static class TimedTextSubtitle extends Timed {
        private final String text;

        public TimedTextSubtitle(int position, int duration, String text) {
            super(TYPE_TIMED_TEXT_SUBTITLE, position, duration);
            this.text = text;
        }

        public String getText() {
            return this.text;
        }
        public Bitmap getBitmap() {
            return null;
        }
        public Rect getBounds() {
            return null;
        }
    }

    public static class TimedBitmapSubtitle extends Timed {
        private final Bitmap bitmap;
        private final Rect bounds;

        public TimedBitmapSubtitle(int position, int duration, int originalWidth, int originalHeight, Bitmap bitmap) {
            super(TYPE_TIMED_BITMAP_SUBTITLE, position, duration);
            this.bitmap = bitmap;
            this.bounds = new Rect(0, 0, originalWidth, originalHeight);
        }
        public String getText() {
            return null;
        }
        public Bitmap getBitmap() {
            return this.bitmap;
        }
        public Rect getBounds() {
            return bounds;
        }
    }

    // from jni
    public static Object createTimedTextSubtitle(int position, int duration, String text) {
        return new Subtitle.TimedTextSubtitle(position, duration, text);
    }
    public static Object createTimedBitmapSubtitle(int position, int duration, int originalWidth, int originalHeight, Bitmap bitmap) {
        return new Subtitle.TimedBitmapSubtitle(position, duration, originalWidth, originalHeight, bitmap);
    }
}