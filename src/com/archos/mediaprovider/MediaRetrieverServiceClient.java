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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.archos.medialib.MediaMetadata;

import java.util.concurrent.TimeUnit;

/**
 * Client for MediaRetrieverService that handles (dis/re)connection of the service.
 */
public class MediaRetrieverServiceClient {

    private static final String TAG = "MediaRetrieverServiceClient";
    private static final boolean DBG = false;

    private volatile IMediaRetrieverService mDelegate;
    private final ThreadGate mThreadGate = new ThreadGate(true) {
        @Override
        protected void closeAction() {
            mDelegate = null;
        }

        @Override
        protected void breakAction() {
            mDelegate = null;
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DBG) Log.d(TAG, "onServiceConnected");
            mDelegate = IMediaRetrieverService.Stub.asInterface(service);
            mThreadGate.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DBG) Log.d(TAG, "onServiceDisconnected");
            mThreadGate.close(); // nulls mDelegate via close action
        }
    };

    /**
     * non-null exactly until destroyed. Volatile so {@link #getMetadata(String)} can check
     * unsynchronized in a different Thread.
     */
    private volatile Context mBindContext;

    /**
     * Auto-binds to service
     */
    public MediaRetrieverServiceClient(Context context) {
        if (!context.bindService(new Intent(context, MediaRetrieverService.class), mConnection, Context.BIND_AUTO_CREATE)) {
            // should only ever happen when binding causes a RemoteException, so there is probably something
            // wrong in our service onBind method or so.
            Log.e(TAG, "MediaRetrieverServiceClient failed to connect to it's service");
            throw new IllegalStateException("Failed to bind to MediaRetrieverService");
        } else {
            if (DBG) Log.d(TAG, "MediaRetrieverServiceClient<CTOR> connecting to service..");
        }
        mBindContext = context;
    }

    /**
     * unbinds from Service and destroys resources
     */
    public void unbindAndDestroy() {
        if (mBindContext != null) {
            if (DBG) Log.d(TAG, "unbindAndDestroy");
            mBindContext.unbindService(mConnection);
            mBindContext = null;

            // unblocks all waiting threads
            mThreadGate.breakGate();
        }
    }

    public static class ServiceManagementException extends Exception {
        public ServiceManagementException(String detailMessage) {
            super(detailMessage);
        }
    }

    /**
     * Has 3 basic outcomes:
     * <ul>
     * <li>Metadata - everything is fine,
     * <li>null - the file has no metadata or the scanner doesn't understand it but it's not crashing
     * <li>ServiceManagementException - failed to communicate with the service but it's not the
     * file's fault. We may want to abort scanning and try again later.
     * <li>RemoteException - the file just crashed the Service. Better blacklist it.
     * </ul>
     * Also InterruptedException - when thread gets interrupted. Won't happen unless someone calls interrupt.
     *
     * @return Metadata or null when extracting metadata failed.
     * @throws RemoteException      when Service dies / throws exception (probably because retrieval failed as well)
     * @throws InterruptedException when we're waiting for the service to connect but thread gets interrupted
     */
    public MediaMetadata getMetadata(String path) throws InterruptedException,
            ServiceManagementException, RemoteException {

        // this method can not be called from the main thread or it would ANR
        assertWorkerThread();

        // context is nulled once this instance is destroyed
        while (mBindContext != null) {

            // wait at most a minute for gate to become open
            int result = mThreadGate.pass(1, TimeUnit.MINUTES);

            if (result == ThreadGate.CLOSED) {
                if (DBG) Log.d(TAG, "getMetadata TIMEOUT");
                // this is a timeout
                throw new ServiceManagementException("Service did not connect & respond in time.");
            }

            // check after passing since that can involve waiting
            if (result == ThreadGate.BROKEN || mBindContext == null) {
                break;
            }

            // Copy reference so it doesn't change while we're checking for null
            IMediaRetrieverService delegate = mDelegate;
            if (delegate == null) {
                // rare corner case:
                // gate must have been closed immediately after we passed => pass the gate again
                if (DBG) Log.d(TAG, "getMetadata NULL DELEGATE");
                continue;
            }

            // the following code does not run synchronized, since that would block the ui thread
            // there is still a chance that we're calling the method while we unbind from the
            // service. No idea what exactly happens then.
            try {
                return delegate.getMetadata(path);
            } catch (RemoteException e) {
                if (DBG) Log.d(TAG, "getMetadata KILLED service");
                // we just killed the service => close gate so next call waits
                mThreadGate.close();
                throw e;
            }
        }
        // this place can only be reached by unbinding from the service
        if (DBG) Log.d(TAG, "getMetadata ABORT, unbound");
        throw new ServiceManagementException("Unbound from Service.");
    }

    private static final Thread MAIN_THREAD = Looper.getMainLooper().getThread();

    private void assertWorkerThread() {
        if (Thread.currentThread() == MAIN_THREAD)
            throw new IllegalThreadStateException("Cannot be called from MainThread.");
    }

    private void assertMainThread() {
        if (Thread.currentThread() != MAIN_THREAD)
            throw new IllegalThreadStateException("Must be called from MainThread.");
    }
}
