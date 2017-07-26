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

package com.archos.mediaprovider.music;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.ArchosMediaIntent;

public class MusicStoreImportService extends Service implements Handler.Callback {
    private static final String TAG =  ArchosMediaCommon.TAG_PREFIX + "MusicStoreImportService";
    private static final boolean LOCAL_DBG = false;
    private static final boolean DBG = ArchosMediaCommon.PACKAGE_DBG & LOCAL_DBG;

    // handler message ids
    private static final int MESSAGE_KILL = 1;
    private static final int MESSAGE_IMPORT_FULL = 2;
    private static final int MESSAGE_IMPORT_INCR = 3;
    private static final int MESSAGE_UPDATE_METADATA = 4;
    private static final int MESSAGE_REMOVE_FILE = 5;

    // handler.arg1 contains startId or this
    private static final int DONT_KILL_SELF = -1;

    // true until shutdown.
    private static boolean sActive = true;

    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private MusicStoreImportImpl mImporter;
    private ContentObserver mContentObserver;

    public MusicStoreImportService() {
        if (DBG) Log.d(TAG, "MusicStoreImportService CTOR");
    }

    @Override
    protected void finalize() throws Throwable {
        if (DBG) Log.d(TAG, "MusicStoreImportService DTOR");
        super.finalize();
    }

    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "onCreate");
        // importer logic
        mImporter = new MusicStoreImportImpl(this);
        // setup background worker thread
        mHandlerThread = new HandlerThread("ImportWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        // associate a handler with the new thread
        mHandler = new Handler(looper, this);
        // associate content observer that reports in background thread
        mContentObserver = new ContentChangeObserver(mHandler);
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        // stop handler thread
        mHandlerThread.quit();
        mImporter.destroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intents are delivered here.
        if (DBG) Log.d(TAG, "onStartCommand:" + intent + " flags:" + flags + " startId:" + startId);

        if (intent == null || intent.getAction() == null)
            return START_NOT_STICKY;

        // forward startId to handler thread
        String action = intent.getAction();
        if (Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)) {
            removeAllMessages(mHandler);
            Message m = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, startId, flags);
            mHandler.sendMessageDelayed(m, 1000);
        } else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
            removeAllMessages(mHandler);
            Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, startId, flags);
            mHandler.sendMessageDelayed(m, 1000);
        } else if (/*MediaStore.ACTION_MTP_SESSION_END*/"android.provider.action.MTP_SESSION_END".equals(action)) {
            removeAllMessages(mHandler);
            Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, startId, flags);
            mHandler.sendMessageDelayed(m, 1000);
        } else if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
            removeAllMessages(mHandler);
        } else if (ArchosMediaIntent.ACTION_MUSIC_SCANNER_METADATA_UPDATE.equals(action)) {
            // requests to update metadata are processed directly and don't impact importing
            Message m = mHandler.obtainMessage(MESSAGE_UPDATE_METADATA, startId, flags, intent.getData());
            m.sendToTarget();
        } else if (ArchosMediaIntent.isMusicRemoveIntent(action)) {
            // requests to remove files are processed directly and don't impact importing
            Message m = mHandler.obtainMessage(MESSAGE_REMOVE_FILE, startId, flags, intent.getData());
            m.sendToTarget();
        } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
            Log.d(TAG, "Import disabled due to shutdown");
            sActive = false;
        }
        return Service.START_REDELIVER_INTENT;
    }

    /**
     * allows to "bind" to this service, will cause the service to be listening to
     * content changed events while bound
     * */
    public static void bind(Context context) {
        Intent intent = new Intent(context, MusicStoreImportService.class);
        context.bindService(intent, new LoggingConnection(), Context.BIND_AUTO_CREATE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) Log.d(TAG, "onBind:" + intent);

        // register contentobserver for files and videos, on change we import them
        getContentResolver().registerContentObserver(MediaStore.Files.getContentUri("external"),
                true, mContentObserver);
        getContentResolver().registerContentObserver(MediaStore.Video.Media.getContentUri("external"),
                true, mContentObserver);
        // do a full import here to make sure that we have initial data
        Message m = mHandler.obtainMessage(MESSAGE_IMPORT_FULL, DONT_KILL_SELF, 0);
        mHandler.sendMessageDelayed(m, 1000);
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (DBG) Log.d(TAG, "onUnbind:" + intent);
        // unregister content observer
        getContentResolver().unregisterContentObserver(mContentObserver);
        return super.onUnbind(intent);
    }

    /** handler implementation, called in background thread */
    public boolean handleMessage(Message msg) {
        if (DBG) Log.d(TAG, "handleMessage:" + msg + " what:" + msg.what + " startid:" + msg.arg1);
        switch (msg.what) {
            case MESSAGE_KILL:
                if (msg.arg1 != DONT_KILL_SELF)
                    stopSelf(msg.arg1);
                break;
            case MESSAGE_IMPORT_INCR:
                doImport(false);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_IMPORT_FULL:
                doImport(true);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_UPDATE_METADATA:
                mImporter.doScan((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
            case MESSAGE_REMOVE_FILE:
                mImporter.doRemove((Uri)msg.obj);
                mHandler.obtainMessage(MESSAGE_KILL, msg.arg1, msg.arg2).sendToTarget();
                break;
        }
        return true;
    }

    /** starts import, fullMode decides which import implementation is used */
    private void doImport(boolean fullMode) {
        // TODO determine when / if we need both import implementations

        if (!sActive) {
            Log.d(TAG, "Import request ignored due to device shutdown.");
            return;
        }

        if (DBG) Log.d(TAG, "doImport");

        long start = System.currentTimeMillis();
        if (fullMode)
            mImporter.doFullImport();
        else
            mImporter.doIncrementalImport();
        long end = System.currentTimeMillis();
        if (DBG) Log.d(TAG, "doImport took:" + (end - start) + "ms full:" + fullMode);
    }

    /** removes all messages from handler */
    protected static void removeAllMessages(Handler handler) {
        handler.removeMessages(MESSAGE_KILL);
        handler.removeMessages(MESSAGE_IMPORT_FULL);
        handler.removeMessages(MESSAGE_IMPORT_INCR);
    }

    /** ContentObserver that triggers import when data changed. */
    private static class ContentChangeObserver extends ContentObserver {

        private final Handler mHandler;
        public ContentChangeObserver(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DBG) Log.d(TAG, "onChange");
            removeAllMessages(mHandler);
            Message msg = mHandler.obtainMessage(MESSAGE_IMPORT_INCR, DONT_KILL_SELF, 0);
            mHandler.sendMessageDelayed(msg, 1000);
        }
    }

    /** ServiceConnection that will only do logging */
    private static class LoggingConnection implements ServiceConnection {

        public LoggingConnection() {
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DBG) Log.d(TAG, "onServiceConnected");
        }

        public void onServiceDisconnected(ComponentName name) {
            if (DBG) Log.d(TAG, "onServiceDisconnected");
        }
    }

}
