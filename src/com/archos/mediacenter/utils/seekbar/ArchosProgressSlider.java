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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.archos.medialib.R;

/**
 * @author developer
 *
 * @hide
 */
public class ArchosProgressSlider extends OrientableSeekBar {
	
	static final String TAG = "ArchosProgressSlider";
	
    private final static boolean DEBUG = false;

    /**
     * The horizontal orientation.
     */
    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;

    /**
     * The vertical orientation.
     */
    public static final int VERTICAL = LinearLayout.VERTICAL;

    private static final int DEFAULT_ORIENTATION = HORIZONTAL;

    private static final int ORIENTATION = R.styleable.ArchosProgressSlider_android_orientation;

	private int mOrientation;

	public ArchosProgressSlider(Context context) {
		this(context, null);
	}
	
	public ArchosProgressSlider(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.attr.progressBarStyle);
	}
	
	public ArchosProgressSlider(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		/**
		 * get orientation in the attributes.
		 * hack: we're using the "orientation" keyword from LinearLayout instead of adding our own...
		 */
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ArchosProgressSlider);
		try {
			mOrientation = a.getInt(ORIENTATION, DEFAULT_ORIENTATION);
            isVertical = mOrientation == VERTICAL;
		} finally {
			a.recycle();
		}
	}
}
