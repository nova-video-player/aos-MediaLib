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

package com.archos.mediacenter.filecoreextension.upnp2;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.FileUtils;

import org.fourthline.cling.model.meta.Device;

import java.util.List;

/**
 * Created by alexandre on 26/05/15.
 * use this class to get stream uri from indexed uri
 */
public class StreamUriFinder {
    private static final long TIMEOUT = 3000;
    private final Uri mUri;
    private final Handler mUiHandler;
    private final Context mContext;
    private final String mDeviceID;
    private Listener mListener;
    private boolean abort;
    private UpnpServiceManager.Listener mDiscoveryListener = new UpnpServiceManager.Listener() {
        @Override
        public void onDeviceListUpdate(List<Device> devices) {
            for (Device device : devices) {
                if (device.hashCode() == (int) Integer.valueOf(mDeviceID) && !abort) { //when we have the correct device
                    UpnpServiceManager.startServiceIfNeeded(mContext).removeListener(this);
                }
            }
        }



    };
    public StreamUriFinder(Uri uri, Context context){
        this(uri, context, Looper.getMainLooper());
    }
    public StreamUriFinder(Uri uri, Context context, Looper looper){
        mUri = uri;
        mDeviceID = uri.getHost();
        abort = false;
        mContext = context;
        mUiHandler = new Handler(looper);
    }

    public void setListener(Listener listener){
        mListener = listener;
    }

    public void start(){
        new Thread(){
            public void run(){
                // Start UPnP
                if(UpnpServiceManager
                        .startServiceIfNeeded(mContext).getDeviceByKey_blocking(Integer.valueOf(mDeviceID), 3000)!=null)
                    startListing();
                else
                    mListener.onError();
            }
        }.start();

    }

    public Uri start_blocking(){
        if(UpnpServiceManager
                .startServiceIfNeeded(mContext).getDeviceByKey_blocking(Integer.valueOf(mDeviceID), 3000)!=null)
           return startListing();
        else
            return null;
    }

    private Uri startListing() {
        String containerPath = FileUtils.getParentUrl(mUri.toString());
        containerPath = containerPath.substring(0,containerPath.length()-1);
        RawLister lister = RawListerFactoryWithUpnp.getRawListerForUrl(Uri.parse(containerPath));
        Uri uri = null;
        try {
            List<MetaFile2> files = lister.getFileList();
            for (MetaFile2 metafile : files) {
                if (metafile.getUri().equals(mUri)) {
                    uri = metafile.getStreamingUri();
                    break;
                }
            }
            if (uri != null) {
                final Uri finalUri = uri;
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null)
                            mListener.onUriFound(finalUri);
                    }
                });
            } else {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null)
                            mListener.onError();
                    }
                });
            }
            return uri;
        } catch (Exception e) {
            e.printStackTrace();
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null)
                        mListener.onError();
                }
            });
        }
        return null;
    }
    public interface Listener{
        void onUriFound(Uri uri);
        void onError();
    }
}
