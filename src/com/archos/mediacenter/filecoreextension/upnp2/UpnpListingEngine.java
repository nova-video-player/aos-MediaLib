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
import android.util.Log;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MimeUtils;

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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vapillon on 06/05/15.
 */
public class UpnpListingEngine extends ListingEngine {

    private final static String TAG = "UpnpListingEngine";

    /**
     * The Cling device to browser
     */
    final private Uri mUri;

    private boolean mAbort;

    public UpnpListingEngine(Context context, Uri uri) {
        super(context);
        mUri = uri;
        Log.d(TAG, "UpnpListingEngine() uri=" + mUri);
    }

    @Override
    public void start() {
        mAbort = false;

        mUiHandler.post(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onListingStart();
                }
            }
        });

        Device device = UpnpServiceManager.startServiceIfNeeded(mContext).getDeviceByKey(Integer.parseInt(mUri.getHost()));
        if (device==null) {
            mListener.onListingFatalError(null, ErrorEnum.ERROR_UPNP_DEVICE_NOT_FOUND);
            return;
        }
        String containerId = mUri.getLastPathSegment();
        listFiles(device, containerId);
    }

    @Override
    public void abort() {
        mAbort = true;
    }

    public void listFiles(Device device, String containerId) {
        Log.d(TAG, "listFiles " + device + "  containerId=" + containerId);
        Service service = device.findService(new UDAServiceId("ContentDirectory"));
        if(device==null) { //return when device is null
            mUiHandler.post(new Runnable() {
                public void run() {
                    if (mListener != null) {
                        mListener.onListingFatalError(null, ErrorEnum.ERROR_UPNP_BROWSE_ERROR);
                    }
                }
            });
            return;
        }
        String decodedContainerId = null;
        try {
            decodedContainerId = URLDecoder.decode(containerId, "UTF-8");
        } catch (UnsupportedEncodingException e) {/* does not happen, UTF-8 always available... */}

        UpnpServiceManager.startServiceIfNeeded(mContext).execute(new Browse(service, decodedContainerId, BrowseFlag.DIRECT_CHILDREN) {
            @Override
            public void failure(ActionInvocation arg0, UpnpResponse arg1, String arg2) {
                Log.d(TAG, "failure on " + arg0 + "\nresponse " + arg1 + ", " + arg2);
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null) {
                            mListener.onListingFatalError(null, ErrorEnum.ERROR_UPNP_BROWSE_ERROR);
                        }
                    }
                });
            }
            @Override
            public void updateStatus(Status status) {}

            @Override
            public void received(ActionInvocation action, final DIDLContent content) {
                if (mAbort) {
                    return;
                }

                final List<UpnpFile2> mFiles = new ArrayList<>();

                // Add all the directories
                for (Container container : content.getContainers()){
                    String encodedId = null;
                    try {
                        encodedId = URLEncoder.encode(container.getId(), "UTF-8");
                    } catch (UnsupportedEncodingException e) {/* does not happen, UTF-8 always available... */}
                    mFiles.add(new UpnpFile2(container.getTitle(), encodedId, mUri));
                }

                // All files matching the filter
                for (Item item : content.getItems()){
                    boolean match = true;
                    String mimeType = item.getFirstResource().getProtocolInfo().getContentFormatMimeType().toString();
                    String path = item.getFirstResource().getValue();
                    String extension = null;
                    if (path != null && path.lastIndexOf('.') != -1) {
                        extension = path.substring(path.lastIndexOf('.') + 1);
                    }

                    if (mExtensionFilter != null && mExtensionFilter.length > 0) {
                        if (extension != null) {
                            match = false;
                            for (String filt : mExtensionFilter) {
                                if (extension.equals(filt) && !filt.isEmpty()) {
                                    match = true;
                                }
                            }
                        }
                    }

                    if (mMimeTypeFilter != null && mMimeTypeFilter.length > 0) {
                        // If we don't have the mimeType, we must guess it from the file extension
                        if (mimeType == null) {
                            item.getFirstResource().getValue();
                            if (extension != null) {
                                extension = path.substring(path.lastIndexOf('.') + 1);
                                mimeType = MimeUtils.guessMimeTypeFromExtension(MimeUtils.getExtension(path));
                            }
                        }

                        if (mimeType != null) {
                            match = false;
                            for (String filt : mMimeTypeFilter) {
                                if (mimeType.startsWith(filt) && !filt.isEmpty()) {
                                    match = true;
                                }
                            }
                        }
                    }

                    if (match){
                        // try to get the thumbnail (or poster)
                        Uri thumbUri = null;
                        DIDLObject.Property<URI> albumArtURI = item.getFirstProperty(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
                        if (albumArtURI!=null) {
                            thumbUri = Uri.parse(albumArtURI.getValue().toString());
                        }
                        // Add Upnp meta file
                        mFiles.add(new UpnpFile2(item, mimeType, mUri, path, thumbUri));
                    }
                    if (mAbort) {
                        break;
                    }
                }

                // Send list to the the world
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null && !mAbort) {
                            mListener.onListingUpdate(mFiles);
                            mListener.onListingEnd();
                        }
                    }
                });
            }
        });
    }
}
