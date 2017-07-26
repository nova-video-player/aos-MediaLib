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

package com.archos.mediacenter.utils.seekbar;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.SeekBar;

public class OrientableSeekBar extends SeekBar {

    protected boolean isVertical = false;

    float mTouchProgressOffset;
    private int mScaledTouchSlop;
    private float mTouchDownY;
    private boolean mIsDragging;
    private OnSeekBarChangeListener mOnSeekBarChangeListener;

    public OrientableSeekBar(Context context) {
        super(context);
    }

    public OrientableSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public OrientableSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public final void setOnSeekBarChangeListener(final OnSeekBarChangeListener l) {
        mOnSeekBarChangeListener = l;
        super.setOnSeekBarChangeListener(l);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isVertical) {
            if (!isEnabled()) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (isInScrollingContainer()) {
                        mTouchDownY = event.getY();
                    } else {
                        setPressed(true);
                        setSelected(true);
                        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN))
                            if ((getThumb() != null)) {
                                invalidate(getThumb().getBounds()); // This may be within the padding region
                            }
                        onStartTrackingTouchVertical();
                        trackTouchEvent(event);
                        attemptClaimDrag();
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mIsDragging) {
                        trackTouchEvent(event);
                        setPressed(true);
                        setSelected(true);
                    } else {
                        final float y = event.getY();
                        if (Math.abs(y - mTouchDownY) > mScaledTouchSlop) {
                            setPressed(true);
                            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN))
                                if (getThumb() != null) {
                                    invalidate(getThumb().getBounds()); // This may be within the padding region
                                }
                            onStartTrackingTouchVertical();
                            trackTouchEvent(event);
                            attemptClaimDrag();
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (mIsDragging) {
                        trackTouchEvent(event);
                        onStopTrackingTouchVertical();
                        setPressed(false);
                        setSelected(false);
                    } else {
                        // Touch up when we never crossed the touch slop threshold should
                        // be interpreted as a tap-seek to that location.
                        onStartTrackingTouchVertical();
                        trackTouchEvent(event);
                        onStopTrackingTouchVertical();
                    }
                    // ProgressBar doesn't know to repaint the thumb drawable
                    // in its inactive state when the touch stops (because the
                    // value has not apparently changed)
                    invalidate();
                    break;

                case MotionEvent.ACTION_CANCEL:
                    if (mIsDragging) {
                        onStopTrackingTouchVertical();
                        setPressed(false);
                        setSelected(false);
                    }
                    invalidate(); // see above explanation
                    break;
            }
            return true;
        } else
            return super.onTouchEvent(event);

    }

    public boolean isInScrollingContainer() {
        ViewParent p = getParent();
        while (p != null && p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setHotspot(float x, float y) {
        final Drawable bg = getBackground();
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP))
            if (bg != null) {
                bg.setHotspot(x, y);
            }
    }

    private void trackTouchEvent(MotionEvent event) {
        final int height = getHeight();
        final int available = height - getPaddingTop() - getPaddingBottom();
        final int y = (int) event.getY();
        float scale;
        float progress = 0;
         {
            if (y < getPaddingTop()) {
                scale = 1.0f;
            } else if (y > height - getPaddingTop()) {
                scale = 0.0f;
            } else {
                scale = 1.0f - (float)(y - getPaddingTop()) / (float)available;
                progress = mTouchProgressOffset;
            }
        }
        final int max = getMax();
        progress += scale * max;

        setHotspot((int) event.getX(), y);
        setProgress((int) progress);
        if (mOnSeekBarChangeListener != null)
            mOnSeekBarChangeListener.onProgressChanged(this, (int) progress, true);
    }

    /**
     * Tries to claim the user's drag motion, and requests disallowing any
     * ancestors from stealing events in the drag.
     */
    private void attemptClaimDrag() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    /**
     * This is called when the user has started touching this widget.
     */
    void onStartTrackingTouchVertical() {
        if (mOnSeekBarChangeListener != null)
            mOnSeekBarChangeListener.onStartTrackingTouch(this);
        mIsDragging = true;
    }

    /**
     * This is called when the user either releases his touch or the touch is
     * canceled.
     */
    void onStopTrackingTouchVertical() {
        if (mOnSeekBarChangeListener != null)
            mOnSeekBarChangeListener.onStopTrackingTouch(this);
        mIsDragging = false;
    }
}
