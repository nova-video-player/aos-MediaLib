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

import com.archos.filecorelibrary.MetaFile;
import com.archos.medialib.R;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class InfoDialog extends Dialog {

    final private static String TAG = "InfoDialog";
    final private static boolean DBG = false;

    final private static int MSG_SIZE_COMPUTED = 1;

    Context mC;

    private int mMinWidth = 0;
    private int mMinHeight = 0;

    private FileSelectionSizeThread mFileSelectionSizeThread;   // async file selection size computer

    // Values that can be stored before onCreate is called
    private Drawable mIconDrawable = null;  // Two ways to set icon : either a drawable
    private int mIconResId = 0;             //                        or a resource ID
    private int mIconBackgroundResource = -1;
    private int mIconColorFilterColor = -1;
    private CharSequence mTitle = null;
    private CharSequence mSubtitle = null;
    private CharSequence mName = null;

    private TextView mTitleView = null;
    private TextView mSubtitleView = null;
    private ImageView mIconView = null;
    private View mDetailsProcessingView = null;

    private ArrayList<File> mFileSelection = null;


    public InfoDialog(Context context) {
        super(context, R.style.ArchosInfoDialog);
        mC = context;
    }

    /**
     * Set the minimum width of the dialog
     * Must be called before onCreate()
     * @param minWidth
     */
    public void setMinWidth(int minWidth) {
    	mMinWidth = minWidth;
    }

    /**
     * Set the minimum height of the dialog
     * Must be called before onCreate()
     * @param minHeight
     */
    public void setMinHeight(int minHeight) {
    	mMinHeight = minHeight;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.archos_info_dialog);
        setCancelable(true);
        setCanceledOnTouchOutside(true);

        View root = findViewById(R.id.dialog_root_layout);
        if (mMinWidth!=0) {
        	root.setMinimumWidth(mMinWidth);
        }
        if (mMinHeight!=0) {
        	root.setMinimumHeight(mMinHeight);
        }

        mTitleView = (TextView) findViewById(R.id.archos_info_title);
        if (mTitle != null) {
            mTitleView.setText(mTitle);
        }
        mSubtitleView = (TextView) findViewById(R.id.archos_info_subtitle);
        if (mSubtitle != null) {
            mSubtitleView.setText(mSubtitle);
        }
        mIconView = (ImageView) findViewById(R.id.archos_info_icon);
        if (mIconDrawable != null) {
            mIconView.setImageDrawable(mIconDrawable);
            setIconSize(mIconDrawable);
        } else if (mIconResId != 0) {
            mIconView.setImageResource(mIconResId);
            setIconSize(mIconResId);
        }

        // Display file info if already available
        if (mFileSelection != null) {
            updateFileSelectionInfo();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        cancel();
        return true;
    }

    public void onStop() {
        if ((mFileSelectionSizeThread != null) && mFileSelectionSizeThread.isAlive()) {
            mFileSelectionSizeThread.stopThread();
        }
    }

    public void setFileInfo(File file) {
        // Build a selection containing only the provided file and process it like a standard selection
        mFileSelection = new ArrayList<File>();
        mFileSelection.add(file);
        updateFileSelectionInfo();
    }

    public void setFileSelectionInfo(ArrayList<File> fileSelection) {
        mFileSelection = fileSelection;
        updateFileSelectionInfo();
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        if (mTitleView != null) {
            mTitleView.setText(mTitle);
        } else {
            if(DBG) Log.v(TAG, "setTitle: mTitleView is null");
        }
    }

    public void setSubtitle(CharSequence subtitle) {
        mSubtitle = subtitle;
        if (mSubtitleView != null) {
            mSubtitleView.setText(subtitle);
        } else {
        	if(DBG) Log.v(TAG, "setSubtitle: mSubtitleView is null");
        }
    }

    public void setFileSelectionName(CharSequence name) {
        mName = name;
    }

    public void setIcon(int resId) {
        mIconResId = resId;
        if (mIconView != null) {
            mIconView.setImageResource(mIconResId);
            setIconSize(mIconResId);
        } else {
        	if(DBG) Log.v(TAG, "setIcon(int): mIconView is null");
        }
    }

    /**
     * @param drawable resource id (can be a color)
     */
    public void setIconBackgroundResource(int resid) {
    	mIconBackgroundResource = resid;
    	if (mIconView != null) {
    		mIconView.setBackgroundResource(mIconBackgroundResource);
        } else {
        	if(DBG) Log.v(TAG, "setIconBackgroundResource(int): mIconView is null");
        }
    }
    
    /**
     * @param color code (i.e #FFAAFF00), -1 = null
     */
    public void setIconColorFilter(int color) {
    	mIconColorFilterColor = color;
        if (mIconView != null) {
        	if (color==-1) {
        		mIconView.clearColorFilter();
        	} else {
        		mIconView.setColorFilter(mIconColorFilterColor);
        	}
        } else {
        	if(DBG) Log.v(TAG, "setIconColorFilter(int): mIconView is null");
        }
    }

    public void setIcon(Drawable icon) {
        mIconDrawable = icon;
        if (mIconView != null) {
            mIconView.setImageDrawable(mIconDrawable);
            setIconSize(mIconDrawable);
        } else {
        	if(DBG) Log.v(TAG, "setIcon(Drawable): mIconView is null");
        }
    }

    private void setIconSize(int ResId) {
        Bitmap b = BitmapFactory.decodeResource(mC.getResources(), ResId);
        setIconSize(b.getWidth(), b.getHeight());
    }

    private void setIconSize(Drawable icon) {
        setIconSize(icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
    }

    private void setIconSize(int bitmapWidth, int bitmapHeight) {
        // Compute the size of the icon so that it fills the top bar vertically
        // and maintains the aspect ratio of the source bitmap
        int iconHeight = mC.getResources().getDimensionPixelSize(R.dimen.archos_info_dialog_header_height);
        float scale = (float)iconHeight / (float)bitmapHeight;
        int iconWidth = (int)(scale * bitmapWidth);

        ViewGroup.LayoutParams lp = mIconView.getLayoutParams();
        lp.width = iconWidth;
        lp.height = iconHeight;
    }

    // Display the given layout, which goal is to show that detailled info is
    // currently being processed
    public void setDetailsProcessingLayout(int detailsProcessingLayoutId) {
        ViewStub detailsProcessingStub = (ViewStub) findViewById(R.id.info_details_processing_stub);
        if ((detailsProcessingStub != null) && (detailsProcessingLayoutId != 0)) {
            detailsProcessingStub.setLayoutResource(detailsProcessingLayoutId);
            mDetailsProcessingView = detailsProcessingStub.inflate();
        } else {
        	if(DBG) Log.w(TAG, "setDetailsProcessingLayout: detailsProcessingStub="
                    + detailsProcessingStub + " / " + "detailsProcessingLayoutId="
                    + detailsProcessingLayoutId);
        }
    }

    public void setCommonDetailsVisibility(boolean visibility) {
        View commonDetails = (View) findViewById(R.id.archos_info_common_details);
        commonDetails.setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    public void setDetailsVisibility(boolean visibility) {
        View details = (View)findViewById(R.id.info_details);
        if(details != null)
            details.setVisibility(visibility ? View.VISIBLE : View.GONE);
        details = (View)findViewById(R.id.info_details_stub);
        if(details != null)
            details.setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    // Display the given layout, which goal is to show detailled info
    // DetailsProcessing view is hidden, if it exists
    // Returns false if the ViewStub doesn't exist anymore because it has
    // already been inflated
    public boolean setDetailsLayout(int detailsLayoutId) {
        // Log.d(TAG,"setDetailsLayout "+detailsLayoutId);

        ViewStub detailsStub = (ViewStub) findViewById(R.id.info_details_stub);
        if (detailsStub == null) {
        	if(DBG) Log.e(TAG, "setDetailsLayout: detailsStub is null, returning false");
            return false;
        }
        if (detailsLayoutId == 0) {
        	if(DBG) Log.e(TAG, "setDetailsLayout: detailsLayoutId is zero, returning false");
            return false;
        }

        // First hide the "processing..." view
        if (mDetailsProcessingView != null) {
            mDetailsProcessingView.setVisibility(View.GONE);
        }
        // Then inflate the detailled infos
        if ((detailsStub != null) && (detailsLayoutId != 0)) {
            detailsStub.setLayoutResource(detailsLayoutId);
            detailsStub.inflate();
        } else {
        	if(DBG) Log.w(TAG, "setDetailsLayout: detailsStub=" + detailsStub + " / "
                    + "detailsLayoutId=" + detailsLayoutId);
        }
        return true;
    }

    // Hide the "processing..." message (if it exists)
    // Should be used when the processing is over but there is nothing to
    // display
    // (in the usual it is hidden by setDetailsLayout())
    public void hideDetailsProcessing() {
        // First hide the "processing..." view
        if (mDetailsProcessingView != null) {
            mDetailsProcessingView.setVisibility(View.GONE);
        }
    }

    private void updateFileSelectionInfo() {
        if (mFileSelection == null) {
        	if(DBG) Log.w(TAG, "updateFileSelectionInfo: selection is empty");
            return;
        }

        int fileCount = mFileSelection.size();

        if (fileCount == 1) {
            //----------------------------------------
            // Infos for a single file or folder
            //----------------------------------------
            File file = mFileSelection.get(0);

            // filename (short)
            TextView nameTv = (TextView) findViewById(R.id.archos_info_name);
            nameTv.setMaxLines(2);
            nameTv.setText(file.getName());

            // permissions
            int read = file.canRead() ? R.string.file_info_label_can_read
                    : R.string.file_info_label_cannot_read;
            int write = file.canWrite() ? R.string.file_info_label_can_write
                    : R.string.file_info_label_cannot_write;
            ((TextView) findViewById(R.id.archos_info_permission)).setText(mC.getText(read) + ", "
                    + mC.getText(write));

            // Modification date
            SimpleDateFormat sdf = new SimpleDateFormat();
            Date date = new Date(file.lastModified());
            sdf = new SimpleDateFormat("d MMMM yyyy, HH:mm:ss");
            String res = sdf.format(date);
            ((TextView) findViewById(R.id.archos_info_last_modified)).setText(res);

            // Mime type
            TextView mimeTypeTv = (TextView) findViewById(R.id.archos_info_mime_type);
            TextView mimeTypeLabelTv = (TextView) findViewById(R.id.archos_info_mime_type_label);

            TextView sizeTv = (TextView) findViewById(R.id.archos_info_size);
            ProgressBar pb = (ProgressBar) findViewById(R.id.archos_info_progress);
            TextView numberFilesTv = (TextView) findViewById(R.id.archos_info_number_files);
            if (file.isDirectory()) {
                // Single folder => start the thread which will compute recursively the total size
                mimeTypeTv.setVisibility(View.GONE);
                mimeTypeLabelTv.setVisibility(View.GONE);
                pb.setVisibility(View.VISIBLE);
                numberFilesTv.setVisibility(View.VISIBLE);
                mFileSelectionSizeThread = new FileSelectionSizeThread(mFileSelection, sizeTv, pb, numberFilesTv);
                mFileSelectionSizeThread.start();
            } else if (file.isFile()) {
                // Single file => just display the size of the file
                mimeTypeTv.setVisibility(View.VISIBLE);
                mimeTypeLabelTv.setVisibility(View.VISIBLE);
                pb.setVisibility(View.GONE);
                numberFilesTv.setVisibility(View.GONE);
                sizeTv.setText(Formatter.formatFileSize(mC, file.length()));
                mimeTypeTv.setText(InfoDialog.getMimeType(file));
            }
            TextView fullpath = (TextView) findViewById(R.id.archos_info_fullpath);
            fullpath.setText(file.getAbsolutePath());
        }
        else {
            //-----------------------------------------------------------
            // Infos for a selection containing several files or folders
            //-----------------------------------------------------------
            // Hide the fields which are meaningless for a selection of files/folders
            ((TextView) findViewById(R.id.archos_info_mime_type)).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.archos_info_mime_type_label)).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.archos_info_permission)).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.archos_info_permission_label)).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.archos_info_last_modified)).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.archos_info_last_modified_label)).setVisibility(View.GONE);

            // Fields to show
            TextView sizeTv = (TextView) findViewById(R.id.archos_info_size);
            sizeTv.setVisibility(View.VISIBLE);
            ProgressBar pb = (ProgressBar) findViewById(R.id.archos_info_progress);
            pb.setVisibility(View.VISIBLE);
            TextView numberFilesTv = (TextView) findViewById(R.id.archos_info_number_files);
            numberFilesTv.setVisibility(View.VISIBLE);

            if (mName != null) {
                TextView nameTv = (TextView) findViewById(R.id.archos_info_name);
                nameTv.setMaxLines(4);
                nameTv.setText(mName);
            }

            // Display the path corresponding to the current folder (we can use the first item 
            // of the selection because all files/folders belong to the same folder anyway)
            TextView fullpath = (TextView) findViewById(R.id.archos_info_fullpath);
            fullpath.setText(mFileSelection.get(0).getParent());

            // Start the thread which will compute recursively the total size
            mFileSelectionSizeThread = new FileSelectionSizeThread(mFileSelection, sizeTv, pb, numberFilesTv);
            mFileSelectionSizeThread.start();
        }
    }

    // Handler to get some asynchronous info
    private final Handler mHandler = new Handler() {
        // @SuppressWarnings("unchecked")
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SIZE_COMPUTED) {
                ArrayList<Object> list = (ArrayList<Object>) msg.obj;
                if (msg.arg1 == -1 && msg.arg2 == -1) {
                    // Size is computed, hide the progressbar
                    ProgressBar pb = (ProgressBar) list.get(1);
                    pb.setVisibility(View.GONE);
                } else {
                    // Size is beeing computed => display the current total
                    TextView sizeTv = (TextView) list.get(0);
                    TextView numberFilesTv = (TextView) list.get(2);
                    sizeTv.setText(Formatter.formatFileSize(mC, (Long) list.get(3)));
                    if (msg.arg1 == 0 && msg.arg2 == 0) {
                        numberFilesTv.setText(R.string.file_info_directory_empty);
                    } else {
                        numberFilesTv.setText(InfoDialog.formatDirectoryInfo(mC, msg.arg1,
                                msg.arg2));
                    }
                }
            }
        }
    };

    // Nested Class to compute a selection size asynchronously
    private final class FileSelectionSizeThread extends Thread {
        private final ArrayList<File> fileSelection;
        private final ArrayList<Object> list;
        private long size = 0;
        private int nbFiles = 0;
        private int nbDirectories = 0;
        private boolean stopThread = false;

        public FileSelectionSizeThread(ArrayList<File> fileSelection, TextView sizeTv, ProgressBar pb, TextView numberFilesTv) {
            super();
            this.fileSelection = fileSelection;

            list = new ArrayList<Object>();
            list.add(sizeTv);
            list.add(pb);
            list.add(numberFilesTv);
            list.add(size);

            // Initial display of the size
            updateSizeDisplay(0, 0, 0);
        }
 
        public void run() {
            int i;

            // Process recursively each item of the selection
            int fileCount = fileSelection.size();
            for (i = 0; i < fileCount; i++) {
                File file = fileSelection.get(i);
                if (file.isDirectory()) {
                    addDirectorySize(file);
                }
                else if (file.isFile()) {
                    addFileSize(file);
                }
            }

            // Final display of the size
            updateSizeDisplay(size, -1, -1);
        }

        private void addDirectorySize(File directory) {
            // Add a single folder (with all its contents)
            nbDirectories++;

            // Get the contents of this folder
            File[] files;
            try {
                files = directory.listFiles();
            } catch (SecurityException e) {
                // May occur if the folder is read-only
                files = null;
            }

            // Get the size of each item contained in this folder
            if (files != null) {
                try {
                    for (File f : files) {
                        hasToStop();
                        if (f.isDirectory()) {
                            addDirectorySize(f);
                        } else if (f.isFile()) {
                            addFileSize(f);
                        }
                    }
                } catch (InterruptedException e) {
                }
            }

            // Display of the updated total size
            updateSizeDisplay(this.size, nbDirectories, nbFiles);
        }

        private void addFileSize(File file) {
            // Add a single file 
            nbFiles++;
            this.size += file.length();

            // Display of the updated total size
            updateSizeDisplay(this.size, nbDirectories, nbFiles);
        }

        private void updateSizeDisplay(long size, int nbDirectories, int nbFiles) {
            list.set(3, size);
            Message msg = mHandler.obtainMessage(MSG_SIZE_COMPUTED, nbDirectories, nbFiles, list);
            mHandler.sendMessage(msg);
        }

        private synchronized void hasToStop() throws InterruptedException {
            if (stopThread) {
                throw new InterruptedException();
            }
        }

        public synchronized void stopThread() {
            stopThread = true;
        }
   }

    static public String formatDirectoryInfo(Context context, int directories, int files) {
        String res = null;

        if (directories == 1) {
            res = context.getText(R.string.file_info_one_directory).toString();
        } else if (directories > 1) {
            res = directories + " " + context.getText(R.string.file_info_directories).toString();
        }

        if (res == null) {
            res = "";
        } else if (directories != 0 && files != 0) {
            res = res + ", ";
        }

        if (files == 1) {
            res += context.getText(R.string.file_info_one_file).toString();
        } else if (files > 1) {
            res += files + " " + context.getText(R.string.file_info_files).toString();
        }

        return res;
    }

    static public String getFilenameWithoutExtension(MetaFile file) {
        final String fullName = file.getName();
        int dotPos = fullName.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < fullName.length()) {
            return fullName.substring(0, dotPos);
        } else {
            return fullName;
        }
    }

    static public String getMimeType(File file) {
        String mimeType;
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        final String fullName = file.getName();
        int dotPos = fullName.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < fullName.length()) {
            mimeType = mimeTypeMap.getMimeTypeFromExtension(fullName.substring(dotPos + 1));
            if (mimeType == null) {
                mimeType = "";
            }
        } else {
            mimeType = "";
        }
        return mimeType;
    }
}
