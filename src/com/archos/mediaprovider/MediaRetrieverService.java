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
import android.os.Message;

import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaFactory;
import com.archos.medialib.MediaMetadata;

public class MediaRetrieverService extends Service {

    private static final String TAG = "MediaRetrieverService";

    private static final int TIMEOUT_MS = 6000;
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            Runtime.getRuntime().exit(-1);
        }
    };

    private final IBinder mBinder = new IMediaRetrieverService.Stub() {
        public MediaMetadata getMetadata(String path) {
            return MediaRetrieverService.this.getMetadata(path);
        }
    };

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
