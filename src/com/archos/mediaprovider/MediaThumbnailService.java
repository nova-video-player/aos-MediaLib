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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import com.archos.environment.ArchosUtils;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class MediaThumbnailService extends Service {

    private static final Logger log = LoggerFactory.getLogger(MediaThumbnailService.class);

    private static boolean sFirst = true;

    static class ServiceStub extends IMediaThumbnailService.Stub {
        MediaThumbnailService mService;

        ServiceStub(MediaThumbnailService service) {
            mService = service;
        }
        public Bitmap getThumbnail(String path, int timeUs)
        {
            return mService.getThumbnail(path, timeUs);
        }
    }

    private static final int TIMEOUT_MSG = 0;

    private static final int TIMEOUT_MS = 10000;

    private static final String TAG = "MediaThumbnailService";

    private final IBinder mBinder = new ServiceStub(this);

    private static class ThumbnailHandler extends Handler {
        private final WeakReference<MediaThumbnailService> mServiceRef;

        ThumbnailHandler(MediaThumbnailService service) {
            super(Looper.getMainLooper());
            mServiceRef = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaThumbnailService service = mServiceRef.get();
            if (service != null) {
                if (msg.what == TIMEOUT_MSG) {
                    Runtime.getRuntime().exit(-1);
                }
            }
        }
    }

    private Handler mHandler = new ThumbnailHandler(this);

    private static final Object sLock = new Object();
    
    public static IMediaThumbnailService sMediaThumbnailService = null;

    static private final ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            if (log.isDebugEnabled()) log.debug("onServiceDisconnected");
            sMediaThumbnailService = null;
        }
        
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (log.isDebugEnabled()) log.debug("onServiceConnected: {}", name);
            synchronized (sLock) {
                sMediaThumbnailService = IMediaThumbnailService.Stub.asInterface(service);
                sLock.notifyAll();
            }
        }
    };

    public static void bind(Context context) {
        Intent intent = new Intent(context, MediaThumbnailService.class);
        context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public static void release(Context ctx){
        try {
            ctx.unbindService(mServiceConnection);
        }catch (java.lang.IllegalArgumentException e){}
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    public static IMediaThumbnailService bind_sync(Context ctx) {
        synchronized (sLock) {
            bind(ctx);
            if (sMediaThumbnailService == null) {
                try {
                    if (log.isDebugEnabled()) log.debug("sMediaThumbnailService == null");
                    sLock.wait(3000);
                    if(sMediaThumbnailService == null&&sFirst) {
                        Toast.makeText(ArchosUtils.getGlobalContext(), "timeout: sMediaThumbnailService == null", Toast.LENGTH_LONG).show();
                        sFirst = false;
                    }
                    if (log.isDebugEnabled()) log.debug("bind_sync end of wait : sMediaThumbnailService == null {}", (sMediaThumbnailService == null));

                } catch (InterruptedException e) {
                    if(sFirst)
                        Toast.makeText(ArchosUtils.getGlobalContext(), "bind_sync interrupted", Toast.LENGTH_LONG).show();
                    sFirst = false;
                    log.error("bind_sync interrupted", e);
                }
            }
            return sMediaThumbnailService;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (log.isDebugEnabled()) log.debug("onCreate");
    }

    @Override
    public void onDestroy() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null); // Clear any pending messages from the handler
        }
        // Unbind from the service connection if it's still bound
        release(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public Bitmap getThumbnail(String path, int timeUs) {
        Bitmap bitmap = null;

        mHandler.sendEmptyMessageDelayed(TIMEOUT_MSG, TIMEOUT_MS);

        IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(this);
        try {

            retriever.setDataSource(path);
            bitmap = retriever.getFrameAtTime(timeUs);

        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {

            // Assume this is a corrupt video file.
        } finally {
            mHandler.removeMessages(TIMEOUT_MSG);
            try {
                retriever.release();
            } catch (RuntimeException | IOException ex) {
                // Ignore failures while cleaning up.
            }
        }
        return bitmap;
    }
}
