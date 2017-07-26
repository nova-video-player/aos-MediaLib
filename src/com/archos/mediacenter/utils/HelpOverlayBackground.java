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

import com.archos.medialib.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;


public class HelpOverlayBackground extends FrameLayout {
    private static final String TAG = "HelpOverlayBackground";

    private static final int BACKGROUND_COLOR = 0xCC000000;     // Black with alpha=80%

    private Context mContext;
    private Paint mErasePaint;

    private Rect mTargetArea;


    public HelpOverlayBackground(Context context) {
        this(context, null, 0);
    }

    public HelpOverlayBackground(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HelpOverlayBackground(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;

        // Create a painter which will be used to clear areas of the background
        mErasePaint = new Paint();
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        mErasePaint.setColor(0xFFFFFF);
        mErasePaint.setAlpha(0);
    }

    public void setTargetArea(Rect rect) {
        mTargetArea = new Rect(rect);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        if (width > 0 && height > 0) {
            // Create a bitmap with the same size as the view
            Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);

            // Fill the whole bitmap with the background color
            c.drawColor(BACKGROUND_COLOR);

            // Cut a "hole" by clearing the target area so that we can see the UI behind
            c.drawRect(mTargetArea, mErasePaint);

            // Draw the resulting bitmap into the provided canvas
            canvas.drawBitmap(b, 0, 0, null);

            // Cleaning
            c.setBitmap(null);
            b = null;
        }

        // Draw the subviews defined in the layout
        super.dispatchDraw(canvas);
    }
}
