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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.FrameLayout;


public class HelpOverlayPopupContent extends FrameLayout {
    private static final String TAG = "HelpOverlayPopupContent";

    private Context mContext;
    private LayoutInflater mInflater;


    public HelpOverlayPopupContent(Context context) {
        this(context, null, 0);
    }

    public HelpOverlayPopupContent(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HelpOverlayPopupContent(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    public void setLayoutId(int layoutId) {
        if (layoutId >= 0) {
            mInflater.inflate(layoutId, this, true);
        }
    }
}
