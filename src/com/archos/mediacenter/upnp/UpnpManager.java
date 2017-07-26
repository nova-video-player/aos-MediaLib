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

package com.archos.mediacenter.upnp;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.archos.filecorelibrary.FileManagerCore;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.mediacenter.utils.UpnpItemData;
import com.archos.mediacenter.utils.Utils;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.header.UDADeviceTypeHeader;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceId;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.seamless.util.MimeType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;

public class UpnpManager extends Observable {
    private static volatile UpnpManager instance = null;
    public final static UDN udn = UDN.uniqueSystemIdentifier("Archos Media MediaRenderer "+Build.DEVICE);
    private BrowseRegistryListener mRegistryListener;
    private AndroidUpnpService mUpnpService;
    private ServiceConnection mServiceConnection;
    private Service mService;
    private String mContainerID;
    private String mFilter;
    ArrayList<UpnpItemData> mFilesList;
    ArrayList<UpnpItemData> mDevicesList;

    public static final int LISTING_ACTION = FileManagerCore.UPNP_LISTING;
    public static final int INIT_ACTION = FileManagerCore.INIT_ACTION;
    public static final int PASTING_ACTION = FileManagerCore.PASTING_ACTION;

    public static final int LISTING_DEVICES = 0;
    public static final int LISTING_ITEMS = 1;

    public static final int LISTING_ERROR = 0;
    public static final int LISTING_SUCCESS = 1;
    public static final int LISTING_CANNOT_LIST = 2;

    private UpnpManager(){
        super();
    }

    public final static UpnpManager getInstance(){
        if (UpnpManager.instance == null){
            synchronized(UpnpManager.class){
                if (UpnpManager.instance == null)
                    UpnpManager.instance = new UpnpManager();
            }
        }
        return instance;
    }

    /**
     * Calling notifyObserver in a thread causes issues, so I use a handler.
     * Besides, I am sure to call setChanged.
     */
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            setChanged();
            notifyObservers(msg);
        }
    };
    /*
     * UPNP browsing part
     */
    public void list(String ID, String filter){
        if (ID == null)
            init();
        else {
            if (ID.equals(mContainerID) && mFilesList != null && !mFilesList.isEmpty()){
                mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_ITEMS, mFilesList).sendToTarget();
                return;
            }
            mContainerID = ID;
            mFilter = filter;
            listFiles();
        }
    }

    public ServiceConnection getServiceConnection(){
        return mServiceConnection;
    }

    public void setService(Service service){
        mService = service;
    }

    public void pause(){
        if (mUpnpService != null)
            mUpnpService.getRegistry().pause();
    }

    public void resume(){
        if (mUpnpService != null)
            mUpnpService.getRegistry().resume();
    }
    private void init() {
        if (mUpnpService != null && mRegistryListener != null){
            mUpnpService.getRegistry().removeAllRemoteDevices();
            UDADeviceType udaType = new UDADeviceType("MediaServer");
            mUpnpService.getControlPoint().search(new UDADeviceTypeHeader(udaType));
            for (Device device : mUpnpService.getRegistry().getDevices())
                mRegistryListener.deviceAdded(device);
        }else {
            mService = null;
            mDevicesList = new ArrayList<UpnpItemData>();
            mRegistryListener = new BrowseRegistryListener();
            mServiceConnection = new ServiceConnection() {
                @SuppressWarnings("rawtypes")
                public void onServiceConnected(ComponentName className, IBinder service) {
                    mUpnpService = (AndroidUpnpService) service;

                    // Refresh the list with all known devices
                    //                listAdapter.clear();
                    for (Device device : mUpnpService.getRegistry().getDevices())
                        mRegistryListener.deviceAdded(device);

                    // Getting ready for future device advertisements
                    mUpnpService.getRegistry().addListener((RegistryListener) mRegistryListener);

                    // Search asynchronously for all devices
                    UDADeviceType udaType = new UDADeviceType("MediaServer");
                    mUpnpService.getControlPoint().search(new UDADeviceTypeHeader(udaType));
                }

                public void onServiceDisconnected(ComponentName className) {
                    mUpnpService = null;
                }
            };
        }
        mHandler.obtainMessage(INIT_ACTION).sendToTarget();
    }

    private void listFiles() {
        mFilesList = new ArrayList<UpnpItemData>();
        if (mService==null) return;
        Action action = mService.getAction("Browse");
        if (action == null) return;
        mUpnpService.getControlPoint().execute(new Browse(mService, mContainerID, BrowseFlag.DIRECT_CHILDREN) {

            @Override
            public void failure(ActionInvocation arg0, UpnpResponse arg1, String arg2) {
                Log.d("cling", "failure on "+arg0+"\nresponse "+arg1+", "+arg2);
            }

            @Override
            public void updateStatus(Status arg0) {
                // TODO Auto-generated method stub

            }

            @SuppressWarnings({
                    "unchecked", "rawtypes"
            })
            @Override
            public void received(ActionInvocation arg0, final DIDLContent arg1) {
                for (Container container : arg1.getContainers()){
                    UpnpItemData itemData = new UpnpItemData(UpnpItemData.UPNP_CONTAINER, container, container.getTitle());
                    mFilesList.add(itemData);
                }
                for (Item item : arg1.getItems()){
                    UpnpItemData itemData = new UpnpItemData(UpnpItemData.UPNP_ITEM, item, item.getTitle());
                    boolean match = mFilter == null;
                    String mimeType = item.getFirstResource().getProtocolInfo().getContentFormatMimeType().toString();
                    if (mimeType == null && mFilter != null) {
                        String path = itemData.getPath();
                        String extension = null;
                        if (path != null && path.lastIndexOf('.') != -1) {
                            extension = path.substring(path.lastIndexOf('.') + 1);
                            mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
                        }
                    }
                    if (mimeType != null && mFilter != null)
                        match = mimeType.startsWith(mFilter);
                    if (!match){
                        continue;
                    }
                    mFilesList.add(itemData);
                }
                mHandler.obtainMessage(LISTING_ACTION, mFilesList.isEmpty() ? LISTING_ERROR : LISTING_SUCCESS, LISTING_ITEMS, mFilesList).sendToTarget();
            }
        });
    }

    public void fetchSubs(String name, File destination){
        HashMap<String, String> availableRemoteSubtitlesFiles = new HashMap<String, String>();
        String  fileName;
        MimeType mimetype;
        for (UpnpItemData item : mFilesList){
            if (item.getUpnpType() == UpnpItemData.UPNP_ITEM){
                fileName = item.getName();
                mimetype = ((Item)item.getData()).getFirstResource().getProtocolInfo().getContentFormatMimeType();
                if (mimetype != null && mimetype.getType().startsWith("text") && fileName.startsWith(name))
                    availableRemoteSubtitlesFiles.put(fileName+"."+mimetype.getSubtype(), item.getPath());
            }
        }
        if (availableRemoteSubtitlesFiles.size() > 0)
            downloadSubs(availableRemoteSubtitlesFiles, destination);
        else
            mHandler.obtainMessage(PASTING_ACTION).sendToTarget();
    }

    private void downloadSubs(final HashMap<String, String> subsUrls, final File destination) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                URL url;
                HttpURLConnection urlConnection = null;
                InputStream in = null;
                FileOutputStream fos = null;
                int l;
                byte[] buffer;
                for (String fileName : subsUrls.keySet()){
                    String subUrl = subsUrls.get(fileName);
                    try {
                        url  = new URL(subUrl);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        in = new BufferedInputStream(urlConnection.getInputStream());
                        fos = new FileOutputStream(new File(destination, fileName));
                        l = 0;
                        buffer = new byte[1024];
                        while ((l = in.read(buffer)) != -1) {
                            fos.write(buffer, 0, l);
                        }

                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        if (urlConnection != null)
                            urlConnection.disconnect();
                        Utils.closeSilently(in);
                        Utils.closeSilently(fos);
                        mHandler.obtainMessage(PASTING_ACTION).sendToTarget();
                    }
                }
            }
        }).start();
    }

    protected class BrowseRegistryListener extends DefaultRegistryListener {
        /* Discovery performance optimization for very slow Android devices! */

        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, final RemoteDevice device, final Exception ex) {
            Log.d("cling", "Discovery failed of '" + device.getDisplayString() + "': " +
                    (ex != null ? ex.toString() : "Couldn't retrieve device/service descriptors"));
            deviceRemoved(device);
        }
        /* End of optimization, you can remove the whole block if your Android handset is fast (>= 600 Mhz) */

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            deviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            deviceAdded(device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            deviceRemoved(device);
        }

        @SuppressWarnings("rawtypes")
        public void deviceAdded(final Device device) {
            Service service = device.findService(new UDAServiceId("ContentDirectory"));
            if (service == null) return;
            Action action = service.getAction("Browse");
            if (action == null) return;
            String deviceName = device.getDisplayString();
            if (deviceName == null) return;
            for (UpnpItemData item : mDevicesList){
                String itemName = ((Device)(item.getData())).getDisplayString();
                if (deviceName.equalsIgnoreCase(itemName)){
                    return;
                }
            }
            String name =
                    (device.getDetails() != null && device.getDetails().getFriendlyName() != null)
                            ? device.getDetails().getFriendlyName()
                            : device.getDisplayString();
            UpnpItemData itemData = new UpnpItemData(UpnpItemData.UPNP_DEVICE, device, name);
            mDevicesList.add(0,itemData);
            mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_DEVICES, mDevicesList).sendToTarget();
        }

        @SuppressWarnings("rawtypes")
        public void deviceRemoved(final Device device) {
            String deviceName = device.getDisplayString();
            for (UpnpItemData item : mDevicesList){
                String itemName = ((Device)(item.getData())).getDisplayString();
                if (deviceName != null && deviceName.equalsIgnoreCase(itemName)){
                    mDevicesList.remove(item);
                    mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_DEVICES, mDevicesList).sendToTarget();
                    return;
                }
            }
        }
    }
}
