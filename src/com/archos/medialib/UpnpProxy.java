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

package com.archos.medialib;

import android.net.Uri;
import android.os.Looper;

import com.archos.filecorelibrary.StreamOverHttp;
import com.archos.mediacenter.filecoreextension.upnp2.StreamUriFinder;

import java.io.IOException;
import java.util.Map;
/*
    retrieve http uri from upnp uri
 */
public class UpnpProxy extends Proxy{
    private StreamOverHttp mStream;
    private Uri mNewUri;

    protected UpnpProxy(Uri uri) {
        super(uri);
    }
    public static boolean needToStream(String scheme){
        return "upnp".equalsIgnoreCase(scheme);
    }
    protected Uri start() {
        final Object lock = new Object();
        new Thread() {
            public void run() {
                Looper.prepare();
                StreamUriFinder StreamUriFinder = new StreamUriFinder(mUri,null, Looper.myLooper());

                StreamUriFinder.setListener(new StreamUriFinder.Listener() {
                    @Override
                    public void onUriFound(Uri uri) {
                        mNewUri = uri;

                        synchronized (lock){
                            lock.notify();
                        }
                    }

                    @Override
                    public void onError() {

                        synchronized (lock){
                            lock.notify();
                        }
                    }
                });
                StreamUriFinder.start();
                Looper.loop();
            }
        }.start();
        synchronized (lock){
            try {
                if(mNewUri==null)
                    lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return mNewUri;
    }

    public void stop() {

    }

    public static UpnpProxy setDataSource(Uri uri, IMediaPlayer mp, Map<String, String> headers) throws IOException {
        UpnpProxy upnpProxy = new UpnpProxy(uri);
        Uri newUri = upnpProxy.start();
        if (newUri != null) {
            mp.setDataSource2(newUri.toString(), headers);
            return upnpProxy;
        } else {
            throw new IOException();
        }
    }

    public static UpnpProxy setDataSource(Uri uri, IMediaMetadataRetriever mr, Map<String, String> headers) throws IllegalArgumentException {
        UpnpProxy upnpProxy = new UpnpProxy(uri);
        Uri newUri = upnpProxy.start();
        if (newUri != null) {
            mr.setDataSource(newUri.toString(), headers);
            return upnpProxy;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
