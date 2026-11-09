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

package com.archos.mediaprovider;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaFactory;
import com.archos.medialib.MediaMetadata;

import java.lang.ref.WeakReference;

public class MediaRetrieverService extends Service {

    private static final String TAG = "MediaRetrieverService";
    private static final boolean DBG = false;

    private static final int TIMEOUT_MSG = 0;
    private static final int TIMEOUT_MS = 6000;

    private static class MediaRetreiverHandler extends Handler {
        private final WeakReference<MediaRetrieverService> mServiceRef;

        MediaRetreiverHandler(MediaRetrieverService service) {
            super(Looper.getMainLooper());
            mServiceRef = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaRetrieverService service = mServiceRef.get();
            if (service != null) {
                if (msg.what == TIMEOUT_MSG) {
                    Runtime.getRuntime().exit(-1);
                }
            }
        }
    }

    private Handler mHandler = new MediaRetreiverHandler(this);

    private final IBinder mBinder = new IMediaRetrieverService.Stub() {
        public MediaMetadata getMetadata(String path) {
            return MediaRetrieverService.this.getMetadata(path);
        }

        public MediaMetadata getMetadataWithNameResolution(String path, String shareName, String shareIp) {
            // This process does not run Samba discovery (see CustomApplication). Register the
            // mapping provided by the main process so the original share-name URI can still be
            // resolved to an IP internally, while credentials keep matching that same URI.
            SambaDiscovery.registerShareNameIP(shareName, shareIp);
            return MediaRetrieverService.this.getMetadata(path);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public MediaMetadata getMetadata(String path) {
        mHandler.sendEmptyMessageDelayed(0, TIMEOUT_MS);
        IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(this);
        try {
            retriever.setDataSource(path);
            return retriever.getMediaMetadata();
        } catch (Throwable t) {
            // something failed, return null instead
            return null;
        } finally {
            mHandler.removeMessages(0);
            try {
                retriever.release();
            } catch (Throwable t) {
                // Ignore failures while cleaning up.
            }
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }
}
