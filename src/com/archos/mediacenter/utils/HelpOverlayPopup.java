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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;


public class HelpOverlayPopup extends LinearLayout {
    private static final String TAG = "HelpOverlayPopup";

    private Context mContext;
    private LayoutInflater mInflater;

    private Button mCloseButton;
    private HelpOverlayPopupContent mPopupContentView;
    private View mPopupRootView;


    public HelpOverlayPopup(Context context) {
        this(context, null, 0);
    }

    public HelpOverlayPopup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HelpOverlayPopup(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);

        mPopupRootView = mInflater.inflate(R.layout.help_overlay_popup, this, true);

        mPopupContentView = (HelpOverlayPopupContent)mPopupRootView.findViewById(R.id.help_popup_content);

        mCloseButton = (Button)mPopupRootView.findViewById(R.id.close_button);
        mCloseButton.setOnClickListener(mCloseButtonOnClickListener);
    }

    public void setContentLayoutId(int layoutId) {
        mPopupContentView.setLayoutId(layoutId);
    }

    private View.OnClickListener mCloseButtonOnClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            // Exit the activity
            ((HelpOverlayActivity)mContext).finish();
        }
    };
}
