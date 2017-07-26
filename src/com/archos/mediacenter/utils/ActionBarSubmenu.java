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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;


public class ActionBarSubmenu implements OnMenuItemClickListener, OnItemClickListener {
    private final static String TAG = "ActionBarSubmenu";

    private Context mContext;
    private MenuItem mMenuItem = null;
    private List<SubmenuItemData> mItemList;
    private int mSelectedPosition;
    private LayoutInflater mLayoutInflater;
    private ListPopupWindow mPopupWindow;
    private ActionBarSubmenuAdapter mAdapter;
    private ActionBarSubmenuListener mSubmenuListener = null;

    private int mSubmenuItemTitleMaxWidth;
    private int mSubmenuFontSize;
    private int mIconMaxWidth = 0;
    private int mRadioButtonWidth = 0;

    public interface ActionBarSubmenuListener {
        public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemid);
    }

    private class SubmenuItemData {
        private int mIconId;
        private int mTitleId;
        private long mItemId;

        public SubmenuItemData(int iconId, int titleId, long itemId) {
            super();
            mIconId = iconId;
            mTitleId = titleId;
            mItemId = itemId;
        }

        public int getIconId() {
            return mIconId;
        }

        public int getTitleId() {
            return mTitleId;
        }

        public long getItemId() {
            return mItemId;
        }
    }


    /*******************************************************************
    ** ActionBarSubmenu API
    *******************************************************************/

    public ActionBarSubmenu(Context context, LayoutInflater inflater, View anchor) {
        mContext = context;

        mItemList = new ArrayList<SubmenuItemData>();
        mSelectedPosition = 0;   // default value
        mSubmenuItemTitleMaxWidth = 0;

        mAdapter = new ActionBarSubmenuAdapter(inflater);

        mPopupWindow = new ListPopupWindow(context, null);
        mPopupWindow.setAdapter(mAdapter);
        mPopupWindow.setModal(true);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(context,R.color.primary_material_dark)));
        mPopupWindow.setAnchorView(anchor);
        mPopupWindow.setOnItemClickListener(this);

        // Get the size of the font used to display the submenu items
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textAppearanceLarge, typedValue, true);
        int[] attribute = new int[] { android.R.attr.textSize };
        TypedArray array = context.obtainStyledAttributes(typedValue.resourceId, attribute);
        mSubmenuFontSize = array.getDimensionPixelSize(0, -1);
        array.recycle();

        // Get the size of the radio button bitmap
        mRadioButtonWidth = context.getResources().getDimensionPixelSize(R.dimen.radio_button_width);
    }

    public void attachMenuItem(MenuItem menuItem) {
        mMenuItem = menuItem;

        mMenuItem.setOnMenuItemClickListener(this);
        mMenuItem.setVisible(true);
    }

    public void setListener(ActionBarSubmenuListener listener) {
        mSubmenuListener = listener;
    }

    public void addSubmenuItem(int iconId, int titleId, long itemId) {
        // Add the item to the internal list
        mItemList.add(new SubmenuItemData(iconId, titleId, itemId));

        // Compute the width of the title 
        Paint paint = new Paint();
        Rect bounds = new Rect();
        int textWidth;

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(mSubmenuFontSize);

        String title = mContext.getString(titleId);
        paint.getTextBounds(title, 0, title.length(), bounds);
        textWidth =  bounds.width();

        // Check if this is the longuest title of the submenu
        if (textWidth > mSubmenuItemTitleMaxWidth) {
            mSubmenuItemTitleMaxWidth = textWidth;
        }

        // Check if this is the widest icon of the submenu
        if (iconId>0) {
            int iconWidth = getBitmapWidth(iconId);
            if (iconWidth > mIconMaxWidth) {
                mIconMaxWidth = iconWidth;
            }
        }
    }

    public void clear() {
        mItemList.clear();
    }

    public void selectSubmenuItem(int position) {
        mSelectedPosition = position;
    }

    private int getBitmapWidth(int resId) {
        InputStream is = mContext.getResources().openRawResource(resId);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        try {
            is.close();
        }
        catch (IOException e) {
        }

        return options.outWidth;
    }


    /*******************************************************************
    ** Events management
    *******************************************************************/

    /*
     * The user clicked on the menu item in the action bar
     */
    public boolean onMenuItemClick(MenuItem item) {
        if (mAdapter != null && mPopupWindow != null) {
            // We are going to display the submenu => we must set its size
            // so that it is wide enough to display the title of all items
            int padding = mContext.getResources().getDimensionPixelSize(R.dimen.track_menu_padding);
            int popupWidth = padding + mIconMaxWidth + padding + mSubmenuItemTitleMaxWidth + mRadioButtonWidth + padding;
            // Sanity check: popup doesn't show if larger than screen
            final int maxWidth = mContext.getResources().getDisplayMetrics().widthPixels - 40; // not proud of this hard-coded 40 but one need to remove some pixels here...
            if (popupWidth>maxWidth) {
                popupWidth = maxWidth;
            }
            mPopupWindow.setContentWidth(popupWidth);

            // Display the submenu
            mPopupWindow.show();
            return true;
        }
        return false;
    }

    /*
     * The user clicked on a submenu item
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mSubmenuListener != null) {
            // Call the client callback
            mSubmenuListener.onSubmenuItemSelected(this, position, id);
        }

        mAdapter.setChecked(view);
        mSelectedPosition = position;

        mPopupWindow.dismiss();
    }

    /**
     * Get the position of the item with itemId.
     * -1 if not found.
     * @param itemId
     * @return
     */
    public int getPosition(int itemId) {
        for (int i=0; i<mItemList.size(); i++) {
            if (mItemList.get(i).getItemId()==itemId) {
                return i;
            }
        }
        return -1;
    }


    /*******************************************************************
    ** Adapter for ActionBarSubmenu
    *******************************************************************/

    public class ActionBarSubmenuAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private CheckedHolder mCheckedHolder = new CheckedHolder();

        private class CheckedHolder {
            private View mView = null;

            public void setView(View view, boolean update) {
                if (update) {
                    setChecked(false);
                }
                mView = view;
                if (update) {
                    setChecked(true);
                }
            }
            private void setChecked(boolean checked) {
                if (mView != null) {
                    RadioButton radio = (RadioButton)mView.findViewById(R.id.radio_button);
                    if (radio != null) {
                        radio.setChecked(checked);
                    }
                }
            }
        }

        public ActionBarSubmenuAdapter(LayoutInflater inflater) {
            super();
            mInflater = inflater;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = null;

            // Re-use the provided view or build a new view otherwise
            if (convertView != null) {
                v = convertView;
            } else {
                v = mInflater.inflate(R.layout.action_bar_submenu_item, parent, false);
            }

            // Fill the view
            SubmenuItemData itemData = mItemList.get(position);

            ImageView iconView = (ImageView)v.findViewById(R.id.icon);
            if (iconView != null) {
                int iconId = itemData.getIconId();
                if (iconId > 0) {
                    iconView.setImageResource(itemData.getIconId());
                    iconView.setVisibility(View.VISIBLE);
                }
                else {
                    iconView.setVisibility(View.GONE);
                }
            }

            TextView titleView = (TextView)v.findViewById(R.id.title);
            if (titleView != null) {
                titleView.setText(itemData.getTitleId());
            }

            RadioButton radioButtonView = (RadioButton)v.findViewById(R.id.radio_button);
            if (radioButtonView != null) {
                radioButtonView.setChecked(position == mSelectedPosition);
            }

            return v;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        public int getCount() {
            return mItemList.size();
        }

        public Object getItem(int position) {
            return mItemList.get(position);
        }

        public long getItemId(int position) {
            return mItemList.get(position).getItemId();
        }

        public void setChecked(View view) {
            mCheckedHolder.setView((ViewGroup)view, true);
        }
    }
}
