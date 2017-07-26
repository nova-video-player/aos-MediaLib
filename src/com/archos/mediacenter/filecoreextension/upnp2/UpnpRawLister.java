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

import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.archos.filecorelibrary.ftp.Session;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceId;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vapillon on 06/05/15.
 */
public class UpnpRawLister extends RawLister  {

    private final static String TAG = "UpnpRawLister";
    private final Object mLock;

    /**
     * The static part of our Upnp stuff
     */
    private UpnpServiceManager mUpnpServiceManager;


    /**
     * The Cling device to browser
     */
    final private Uri mUri;

    /**
     * The Cling device to browser
     */
    final private Device mDevice;

    /**
     * The container ID to browse, default is "0"
     */
    private String mContainerId = "0";


    private ArrayList<MetaFile2> mFiles;


    public UpnpRawLister(Uri uri) {
        super(uri);
        UpnpServiceManager.restartUpnpServiceIfWasStartedBefore();
        mUpnpServiceManager = UpnpServiceManager.getSingleton(null); //won't create, so we need to be sure it has already been created before
        mUri = uri;
        mLock = new Object();
        Log.d(TAG, "UpnpRawLister() uri="+mUri);
        Log.d(TAG, "UpnpRawLister() lastPath="+mUri.getLastPathSegment());
        // Get Device from its hash key that is in the Uri
        mDevice = mUpnpServiceManager.getDeviceByKey_blocking(Integer.valueOf(mUri.getHost()), 500);

        // Container ID is encoded at the end of the Uri, need to decode it here
        try {
            if(mUri.getLastPathSegment()!=null)
                mContainerId = URLDecoder.decode(mUri.getLastPathSegment(), "UTF-8");
        } catch (UnsupportedEncodingException e) { /* does not happen, UTF-8 always available... */ }
    }

    //some device specific restrictions
    public boolean shouldIAddContainer(Container container, Device device, String parentID){

        if(device.getDetails().getManufacturerDetails().getManufacturer().equals("Plex, Inc.")){
            return !container.getId().startsWith(parentID+"_");

        }
        return true;
    }

    public void listFiles(final Device device, final String containerId) {
        Log.d(TAG, "listFiles "+device+"  containerId="+containerId);
        Service service = device.findService(new UDAServiceId("ContentDirectory"));
        int ready = mUpnpServiceManager.execute(new Browse(service, containerId, BrowseFlag.DIRECT_CHILDREN) {
            @Override
            public void failure(ActionInvocation arg0, UpnpResponse arg1, String arg2) {
                Log.d(TAG, "failure on " + arg0 + "\nresponse " + arg1 + ", " + arg2);
                synchronized (mLock) {
                    mLock.notify();
                }
            }
            @Override
            public void updateStatus(Status status) {}

            @Override
            public void received(ActionInvocation action, final DIDLContent content) {
                mFiles = new ArrayList<>();

                // Add all the directories
                for (Container container : content.getContainers()){
                    if(shouldIAddContainer(container, device, containerId)) {
                        String encodedId = null;
                        try {
                            encodedId = URLEncoder.encode(container.getId(), "UTF-8");
                        } catch (UnsupportedEncodingException e) {/* does not happen, UTF-8 always available... */}
                        mFiles.add(new UpnpFile2(container.getTitle(), encodedId, mUri));
                    }
                }

                // All files matching the filter
                for (Item item : content.getItems()){
                    boolean match = true;
                    String mimeType = item.getFirstResource().getProtocolInfo().getContentFormatMimeType().toString();
                    String path = item.getFirstResource().getValue();
                    if (match){
                        Uri thumbUri = null;
                        DIDLObject.Property<URI> albumArtURI = item.getFirstProperty(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
                        if (albumArtURI!=null) {
                            thumbUri = Uri.parse(albumArtURI.getValue().toString());
                        }
                        mFiles.add(new  UpnpFile2(item, mimeType, mUri, path, thumbUri));
                    }

                }
                synchronized (mLock) {
                    mLock.notify();
                }
            }
        });
        if (ready == -1)
            synchronized (mLock) {
                mLock.notify();
            }
    }

    @Override
    public List<MetaFile2> getFileList() throws IOException, AuthenticationException, SftpException, JSchException {
        if(mDevice!=null) {
            listFiles(mDevice, mContainerId);
            synchronized (mLock) {
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return mFiles;
    }
}
