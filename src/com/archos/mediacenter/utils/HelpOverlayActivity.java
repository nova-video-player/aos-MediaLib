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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.view.WindowManager;


public class HelpOverlayActivity extends Activity {
    private final static String TAG = "HelpOverlayActivity";
    private final static boolean DBG = false; 

    // Intent extras providing the layout information for the activity
    public final static String EXTRA_TARGET_AREA_LEFT = "help_overlay_target_area_left";
    public final static String EXTRA_TARGET_AREA_TOP = "help_overlay_target_area_top";
    public final static String EXTRA_TARGET_AREA_RIGHT = "help_overlay_target_area_right";
    public final static String EXTRA_TARGET_AREA_BOTTOM = "help_overlay_target_area_bottom";
    public final static String EXTRA_POPUP_CONTENT_LAYOUT_ID = "help_overlay_popup_content_layout_id";

    // The area of the screen which must be transparent so that we can see the UI behind
    private Rect mTargetArea;

    // The layout corresponding to what must be displayed inside the popup
    private int mPopupContentLayoutId;

    private HelpOverlayBackground mRootView;
    private HelpOverlayPopup mPopupView;
    private ImageView mFocusView;
    private int mScreenWidth;
    private int mPopupRightOffset;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Extract the layout information from the intent
        Intent intent = getIntent();
        mTargetArea = new Rect();
        Resources res = getResources();

        mTargetArea.left = intent.getIntExtra(EXTRA_TARGET_AREA_LEFT, res.getDimensionPixelSize(R.dimen.help_overlay_default_selected_area_left));
        mTargetArea.top = intent.getIntExtra(EXTRA_TARGET_AREA_TOP, res.getDimensionPixelSize(R.dimen.help_overlay_default_selected_area_top));
        mTargetArea.right = intent.getIntExtra(EXTRA_TARGET_AREA_RIGHT, res.getDimensionPixelSize(R.dimen.help_overlay_default_selected_area_right));
        mTargetArea.bottom = intent.getIntExtra(EXTRA_TARGET_AREA_BOTTOM, res.getDimensionPixelSize(R.dimen.help_overlay_default_selected_area_bottom));
        if (DBG) Log.d(TAG, "onCreate : target area = " + mTargetArea.left + " " + mTargetArea.top + " " + mTargetArea.right + " " + mTargetArea.bottom);

        mPopupContentLayoutId = intent.getIntExtra(EXTRA_POPUP_CONTENT_LAYOUT_ID, -1);

        // Load the activity layout
        setContentView(R.layout.help_overlay);

        mRootView = (HelpOverlayBackground)findViewById(R.id.root);
        mRootView.setTargetArea(mTargetArea);

        mFocusView = (ImageView)findViewById(R.id.help_focus);

        mPopupView = (HelpOverlayPopup)findViewById(R.id.help_popup);
        mPopupView.setContentLayoutId(mPopupContentLayoutId);

        WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        mScreenWidth = wm.getDefaultDisplay().getWidth();
        mPopupRightOffset = res.getDimensionPixelSize(R.dimen.help_overlay_right_offset);

        updateGlobalLayout();
    }

    private void updateGlobalLayout() {
        //----------------------------------------------------
        // Set the popup position
        //----------------------------------------------------
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams)mPopupView.getLayoutParams();

        // Display the popup below the target area
        mlp.topMargin = mTargetArea.bottom + 1;

        // Try to align the right side of the popup with the right side of the target area
        // but don't display it too close from the screen right edge
        int popupWidth = getResources().getDimensionPixelSize(R.dimen.help_overlay_popup_width);
        int maxPopupRight = mScreenWidth - mPopupRightOffset;
        if (mTargetArea.right > maxPopupRight) {
            mlp.leftMargin = maxPopupRight - popupWidth;
        }
        else {
            mlp.leftMargin = mTargetArea.right - popupWidth;
        }

        if (mlp.leftMargin < 0) {
            // Oops, the left part of the popup will not be visible
            if (popupWidth < mScreenWidth) {
                // Let's center the popup horizontally inside the screen
                mlp.leftMargin = (mScreenWidth - popupWidth) / 2;
            }
            else {
                // The popup is wider than the screen => align the left edge with the screen left edge
                mlp.leftMargin = 0;
            }
        }

        //----------------------------------------------------
        // Set the focus position and size
        //----------------------------------------------------
         mlp = (ViewGroup.MarginLayoutParams)mFocusView.getLayoutParams();
         mlp.leftMargin = mTargetArea.left;
         mlp.topMargin = mTargetArea.top;

        // Make the focus bitmap fill the target area
        ViewGroup.LayoutParams vlp = (ViewGroup.LayoutParams)mFocusView.getLayoutParams();
        vlp.width = mTargetArea.width();
        vlp.height = mTargetArea.height();
        mFocusView.setLayoutParams(vlp);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // When the device is rotated or the screen resolution changes the activity is normally destroyed
        // and created again, but in that case getIntent() returns the same intent which was used to create
        // the activity initially. The target area is therefore wrong so let's just close the help overlay
        // rather than implementing some tricky behaviour with the video browser.
        finish();
    }
}
