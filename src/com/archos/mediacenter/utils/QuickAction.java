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

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.archos.medialib.R;

import java.util.ArrayList;


/**
 * Popup window, shows action list as icon and text like the one in Gallery3D app.
 *
 */
public class QuickAction extends CustomPopupWindow {
    private static final String TAG = "QuickAction";

    private final View root;
    private final ImageView mArrowUp;
    private final ImageView mArrowDown;
    private final LayoutInflater inflater;
    private final Context context;

    public static final int ANIM_GROW_FROM_LEFT = 1;
    public static final int ANIM_GROW_FROM_RIGHT = 2;
    public static final int ANIM_GROW_FROM_CENTER = 3;
    public static final int ANIM_REFLECT = 4;
    public static final int ANIM_AUTO = 5;

    private int animStyle;
    private ViewGroup mTrack;
    private ArrayList<ActionItem> actionList;


    class TouchpadSettingsObserver extends ContentObserver {
        TouchpadSettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            dismiss();
        }
    }

    /**
        * Constructor
        *
        * @param anchor {@link View} on where the popup window should be displayed
        */
    public QuickAction(View anchor) {
        super(anchor);

        actionList	= new ArrayList<ActionItem>();
        context		= anchor.getContext();
        inflater 	= (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        root		= inflater.inflate(R.layout.popup, null);

        mArrowDown 	= (ImageView) root.findViewById(R.id.arrow_down);
        mArrowUp 	= (ImageView) root.findViewById(R.id.arrow_up);

        setContentView(root);

        mTrack 			= (ViewGroup) root.findViewById(R.id.tracks);
        animStyle		= ANIM_AUTO;
    }

    public void onClose() {
        // This function must be called each time the QuickAction popup is closed
        // context.getContentResolver().unregisterContentObserver(mTouchpadObserver);
    }

    /**
        * Set animation style
        *
        * @param animStyle animation style, default is set to ANIM_AUTO
        */
    public void setAnimStyle(int animStyle) {
        this.animStyle = animStyle;
    }

    /**
        * Add action item
        *
        * @param action  {@link ActionItem} object
        */
    public void addActionItem(ActionItem action) {
        actionList.add(action);
    }

    /**
        * Show popup window. Popup is automatically positioned, on top or bottom of anchor view.
        *
        */
    public void show() {
        preShow();

        int xPos, yPos;
        int[] location 		= new int[2];

        //----------------------------------------------------------------------
        // Retrieve the position of the (+) symbol view
        //----------------------------------------------------------------------
        anchor.getLocationOnScreen(location);
        Rect anchorRect = new Rect(location[0] + anchor.getPaddingLeft(), location[1],
                                   location[0] + anchor.getWidth() - anchor.getPaddingRight(), location[1] + anchor.getHeight());

        //----------------------------------------------------------------------
        // Compute the size of the usable area in the screen
        //----------------------------------------------------------------------
        // Retrieve the full screen size
        DisplayMetrics metrics = anchor.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        //----------------------------------------------------------------------
        // Check if the popup must be displayed above or below the (+) symbol
        //----------------------------------------------------------------------
        int dyTop           = anchorRect.top;                       // Distance of the (+) symbol from the top of the screen
        int dyBottom        = screenHeight - anchorRect.bottom;     // Distance of the (+) symbol from the bottom of the screen
        boolean onTop       = (dyTop > dyBottom) ? true : false;

        //---------------------------------------------------------------------------
        // Get the size of the quick action window
        //---------------------------------------------------------------------------
        // Build the popup items
        createActionList();

        // Force a measure of the corresponding window
        // (only used to retrieve the popup width, the height beeing not reliable at all)
        root.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        root.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        // Compute the height of the popup window
        int itemsCount = actionList.size();
        int rowHeight = context.getResources().getDimensionPixelSize(R.dimen.quick_action_row_height);

        int upArrowHeight = mArrowUp.getMeasuredHeight();
        int downArrowHeight = mArrowDown.getMeasuredHeight();
        int upArrowMargin = context.getResources().getDimensionPixelSize(R.dimen.single_quick_action_arrow_up_margin);
        int downArrowMargin = context.getResources().getDimensionPixelSize(R.dimen.single_quick_action_arrow_down_margin);

        int popupWidth = root.getMeasuredWidth();
        int popupItemsHeight = rowHeight * itemsCount;
        int popupHeight = popupItemsHeight + upArrowHeight + upArrowMargin + downArrowHeight + downArrowMargin;

        //----------------------------------------------------------------------
        // Compute the horizontal position of the quick action window
        //----------------------------------------------------------------------

        //        <---> = popupArrowOffset
        //            (+)
        //            / \
        //        ---/   \--------------
        //        |                    |
        //        |<--- popupWidth --->|
        //        ----------------------

        // The arrow must be centered horizontally with the anchor => compute an offset corresponding to
        // the horizontal distance between the center of the arrow and the nearest edge of the popup
        int arrowWidth = onTop ? mArrowDown.getMeasuredWidth() : mArrowUp.getMeasuredWidth();
        int popupArrowOffset = context.getResources().getDimensionPixelSize(R.dimen.popup_horizontal_offset) + arrowWidth / 2;

        // Check the best position for the popup depending on the anchor position on the screen
        if (anchorRect.centerX() + popupWidth - popupArrowOffset < screenWidth) {
            // There is enough room to display the popup to the right of the (+) symbol
            xPos = anchorRect.centerX() - popupArrowOffset;
        }
        else {
            // Not enough room to display the popup to the right of the (+) symbol
            // => try then to display it to the left of the symbol
            xPos = anchorRect.centerX() - popupWidth + popupArrowOffset;
            if (xPos < 0) {
                // We can't display the popup nor to the left neither to the right of the symbol
                // => align the popup with the left side of the screen
                xPos = 0;
            }
        }

        //----------------------------------------------------------------------
        // Compute the vertical position of the quick action window
        //----------------------------------------------------------------------
        int anchorHeight = context.getResources().getDimensionPixelSize(R.dimen.quick_action_anchor_size);

        if (onTop) {
            // Display the popup above the (+) symbol
            yPos = anchorRect.centerY() - anchorHeight / 2 - popupHeight;
        } else {
            // Display the popup below the (+) symbol (extraVerticalOffset is used for a fine adjustement)
            int extraVerticalOffset = context.getResources().getDimensionPixelSize(R.dimen.extra_vertical_offset);
            yPos = anchorRect.centerY() + anchorHeight / 2 - extraVerticalOffset;
        }

        //----------------------------------------------------------------------
        // Select which arrow must be shown and set its horizontal position
        //----------------------------------------------------------------------
        showArrow(((onTop) ? R.id.arrow_down : R.id.arrow_up), anchorRect.centerX() - xPos);

        //----------------------------------------------------------------------
        // Set the final position of the quick action window
        //----------------------------------------------------------------------
        setAnimationStyle(screenWidth, anchorRect.centerX(), onTop);
        window.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos);
    }

    /**
        * Set animation style
        *
        * @param screenWidth screen width
        * @param requestedX distance from left edge
        * @param onTop flag to indicate where the popup should be displayed. Set TRUE if displayed on top of anchor view
        * 		  and vice versa
        */
    private void setAnimationStyle(int screenWidth, int requestedX, boolean onTop) {
        int arrowPos = requestedX - mArrowUp.getMeasuredWidth()/2;

        switch (animStyle) {
        case ANIM_GROW_FROM_LEFT:
            window.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Left : R.style.Animations_PopDownMenu_Left);
            break;

        case ANIM_GROW_FROM_RIGHT:
            window.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Right : R.style.Animations_PopDownMenu_Right);
            break;

        case ANIM_GROW_FROM_CENTER:
            window.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Center : R.style.Animations_PopDownMenu_Center);
        break;

        case ANIM_REFLECT:
            window.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Reflect : R.style.Animations_PopDownMenu_Reflect);
        break;

        case ANIM_AUTO:
            if (arrowPos <= screenWidth/4) {
                window.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Left : R.style.Animations_PopDownMenu_Left);
            } else if (arrowPos > screenWidth/4 && arrowPos < 3 * (screenWidth/4)) {
                window.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Center : R.style.Animations_PopDownMenu_Center);
            } else {
                window.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Right : R.style.Animations_PopDownMenu_Right);
            }

            break;
        }
    }

    /**
        * Create action list
        */
    private void createActionList() {
        View view;
        String title;
        Drawable icon;
        OnClickListener listener;

        // Add all the items to the popup
        for (int i = 0; i < actionList.size(); i++) {
            title 		= actionList.get(i).getTitle();
            icon        = actionList.get(i).getIcon();
            listener	= actionList.get(i).getListener();

            view 		= getActionItem(title, icon, listener);

            view.setFocusable(true);
            view.setClickable(true);

            mTrack.addView(view);

            // Separators between items are directly included at the bottom of the layout
            // of each item so the separator of the last item must be hidden
            if (i == actionList.size() - 1) {
                View bottomSeparator = view.findViewById(R.id.bottom_separator);
                bottomSeparator.setVisibility(View.GONE);
            }
        }
    }

    /**
        * Get action item {@link View}
        *
        * @param title action item title
        * @param icon {@link Drawable} action item icon
        * @param listener {@link View.OnClickListener} action item listener
        * @return action item {@link View}
        */
    private View getActionItem(String title, Drawable icon, OnClickListener listener) {
        LinearLayout container	= (LinearLayout) inflater.inflate(R.layout.quick_action_item, null);

        ImageView img = (ImageView) container.findViewById(R.id.icon);
        TextView text = (TextView) container.findViewById(R.id.title);

        if (icon != null) {
            img.setImageDrawable(icon);
        }

        if (title != null) {
            text.setText(title);
        }

        if (listener != null) {
            container.setOnClickListener(listener);
        }

        return container;
    }

    /**
        * Show the requested arrow and hide the other
        *
        * @param whichArrow arrow type resource id
        * @param requestedX distance from left screen
        */
    private void showArrow(int whichArrow, int requestedX) {
        int arrowMarginTop;
        int arrowWidth;
        int resId;

        if (whichArrow == R.id.arrow_up) {
            ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams)mArrowUp.getLayoutParams();

            // Set the horizontal position of the arrow
            arrowWidth = mArrowUp.getMeasuredWidth();
            param.leftMargin = requestedX - arrowWidth / 2;

            // Set the vertical position of the arrow
            resId = (actionList.size() > 1) ? R.dimen.multi_quick_action_arrow_up_margin : R.dimen.single_quick_action_arrow_up_margin;
            arrowMarginTop = context.getResources().getDimensionPixelSize(resId);
            param.topMargin = arrowMarginTop;

            mArrowUp.setVisibility(View.VISIBLE);
            mArrowDown.setVisibility(View.INVISIBLE);
        }
        else {
            ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams)mArrowDown.getLayoutParams();

            // Set the horizontal position of the arrow
            arrowWidth = mArrowDown.getMeasuredWidth();
            param.leftMargin = requestedX - arrowWidth / 2;

            // Set the vertical position of the arrow
            resId = (actionList.size() > 1) ? R.dimen.multi_quick_action_arrow_down_margin : R.dimen.single_quick_action_arrow_down_margin;
            arrowMarginTop = context.getResources().getDimensionPixelSize(resId);
            param.topMargin = arrowMarginTop;

            mArrowUp.setVisibility(View.INVISIBLE);
            mArrowDown.setVisibility(View.VISIBLE);
        }
    }
}
