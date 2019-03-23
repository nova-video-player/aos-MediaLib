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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.model.message.header.UDADeviceTypeHeader;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceId;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created by vapillon on 06/05/15.
 */
public class UpnpServiceManager extends BroadcastReceiver {

    private boolean mHasStarted;
    private boolean mStopLock;
    /*
    we want upnpservice to stop when the app goes in background BUT
    do not stop service when, for example, the app goes in background while rawlister is used by indexing
    */

    @Override
    public void onReceive(Context context, Intent intent) {

        //we need to restart upnp service on network state change
        if (mAndroidUpnpService!=null&&mState==State.RUNNING&&mHasStarted) {
            if(DBG) Log.d(TAG, "restarting");
            mDevices.clear();
            informListenersOfDeviceListUpdate(mListeners);
            mAndroidUpnpService.getRegistry().removeListener(mRegistryListener);
            try {
                    mContext.unbindService(mServiceConnection);
            } catch (java.lang.IllegalArgumentException e) {
                //this is bad, but I haven't found any other way to avoid "java.lang.IllegalArgumentException: Service not registered"
            }
            mState = State.NOT_RUNNING;
            start();
        }

    }

    private static final String TAG = "UpnpServiceManager";
    private static final boolean DBG = false;
    /**
     * NOTE: this does not work like the SMB discovery.
     * We get server updates (found, removed, renamed, etc)  1 minute
     instantaneously as long as the search is on-going,
     * but the search need to "revived" from time to time =>  1 minute
     * (Maybe can be less often, need to test)
     */
    private static final int SERVER_SEARCH_PERIOD_MS = 60*1000; //
    private static UpnpServiceManager singleton = null;

    private enum State {
        NOT_RUNNING,
        STARTING,
        RUNNING,
        ERROR,
    }

    private final Context mContext;
    private State mState;

    private AndroidUpnpService mAndroidUpnpService;
    private List<Listener> mListeners = new LinkedList<>();

    /**
     * Handler running on main UI thread, used to post listeners callbacks to the UI thread
     */
    final private Handler mUiHandler = new Handler(Looper.getMainLooper());

    /**
     * Devices indexed by hash code
     */
    final private HashMap<Integer, Device> mDevices = new HashMap<>();

    /**
     * Pool of blocking requests
     */
    final private Set<BlockingDeviceRequest> mDeviceRequests = new HashSet<BlockingDeviceRequest>();

    public interface Listener {
        void onDeviceListUpdate(List<Device> devices);
    }

    /**
     * Singleton instance is created if needed, UPnP service is started.
     * @param applicationContext
     * @return the singleton
     */
    public static synchronized UpnpServiceManager getSingleton(Context applicationContext) {
        if (singleton==null) {
            singleton = new UpnpServiceManager(applicationContext);
        }
        singleton.startUpnpServiceIfNotStartedYet();
        return singleton;
    }

    private void startUpnpServiceIfNotStartedYet() {
        if(!mHasStarted&&mContext!=null) { // just a test to ensure context has been set

            mContext.registerReceiver(
                    this,
                    new IntentFilter(
                            ConnectivityManager.CONNECTIVITY_ACTION));
            mHasStarted = true;
            mState = State.NOT_RUNNING;
            if (DBG) Log.d(TAG, "State NOT_RUNNING");
        }
    }

    /**
     * Convenient method to create the singleton (if needed) and start the service (if needed)
     * @param context
     * @return the singleton
     */
    static public synchronized UpnpServiceManager startServiceIfNeeded(Context context) {
        getSingleton(context.getApplicationContext()).start();
        return singleton;
    }

    /**
     * will only start upnp service if service manager has already been started
     * this is used to restart upnp service when coming back in foreground
     */
    static public synchronized void restartUpnpServiceIfWasStartedBefore() {
        if(singleton!=null)
            singleton.startUpnpServiceIfNotStartedYet();
    }

    static public synchronized void stopServiceIfLaunched(){
        if(singleton!=null)
            singleton.stop();

    }
    private UpnpServiceManager(Context context) {
        mContext = context;

    }

    /**
     * Start the service if needed. Does nothing if the service is already started.
     */
    public void start() {
        if (mState == State.NOT_RUNNING || mState == State.ERROR) {
            boolean result = mContext.bindService(new Intent(mContext, AndroidUpnpServiceImpl.class), mServiceConnection, Context.BIND_AUTO_CREATE);
            if (result) {
                mState = State.STARTING;
                if(DBG) Log.d(TAG, "State STARTING");
            } else {
                mState = State.ERROR;
                if(DBG) Log.e(TAG, "State ERROR - bindService returned false!");
            }
        }
    }
    /**
     * forbid the service manager to kill the upnp service.
     * Please, do not use this anywhere, it has to be in the same thread as NetworkScanner. It hasn't been written with a stack.
     */
    public void lockStop(){
        mStopLock = true;
    }

    /**
     * allows the service manager to kill the upnp service.
     * Please, do not use this anywhere, it has to be in the same thread as NetworkScanner. It hasn't been written with a stack.
     */
    public void releaseStopLock(){
        mStopLock = false;}
    /**
     * Stop the UPnP service
     */
    public void stop() {
        if(mStopLock) { //do not stop when discovery is used
            return;
        }
        if (mAndroidUpnpService!=null) {
            mAndroidUpnpService.getRegistry().removeListener(mRegistryListener);
        }
        if(mHasStarted) {
            try {
            mContext.unregisterReceiver(this);
            mDevices.clear();
                if (mAndroidUpnpService != null)
                    mContext.unbindService(mServiceConnection);
            } catch (java.lang.IllegalArgumentException e) {
                //this is bad, but I haven't found any other way to avoid "java.lang.IllegalArgumentException: Service not registered"
            }
            mUiHandler.removeCallbacks(mPeriodicSearchRunnable);
        }
        mHasStarted= false;
    }

    public void addListener(Listener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
        // If there are already some devices found we notify ths new listener right away
        // (well actually not right away in this call since it's using a post to the UI handler)
        informListenersOfDeviceListUpdate(Collections.singletonList(listener));

    }

    public void removeListener(Listener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * Service connection callbacks
     */
    final ServiceConnection mServiceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mAndroidUpnpService = (AndroidUpnpService) service;
            mState = State.RUNNING;
            if(DBG) Log.d(TAG, "State RUNNING");

            // Listen for discovery stuff
            mAndroidUpnpService.getRegistry().addListener(mRegistryListener);

            // Start searching for servers periodically
            mUiHandler.removeCallbacks(mPeriodicSearchRunnable); // better safe than sorry
            mUiHandler.post(mPeriodicSearchRunnable); // probably does not have to be on UI thread but it makes no harm and avoid having yet another handler
        }

        public void onServiceDisconnected(ComponentName className) {
            // Stop periodic search
            mUiHandler.removeCallbacks(mPeriodicSearchRunnable);

            // no more service
            mAndroidUpnpService = null;
            mState = State.NOT_RUNNING;
            if(DBG) Log.d(TAG, "State NOT_RUNNING");
        }
    };

    public Collection<Device> getDevices() {
        synchronized (this) {
            return mDevices.values();
        }
    }

    public Device getDeviceByKey(int key) {
        if (mState != State.RUNNING) {
            return null;
        }
        synchronized (this) {
            return mDevices.get(Integer.valueOf(key));
        }
    }

    /**
     * This method must not be called from the main thread because it may blocks until the UPnP service is started,
     * and starting the UPnP service involves the main thread...
     * Anyway it is a bad idea to call a blocking method like this one on the main thread... even with  a timeout
     * @param key
     * @param timeOutInMs
     * @return
     */
    public Device getDeviceByKey_blocking(int key, int timeOutInMs) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Must not call getDeviceByKey_blocking on the main thread!");
        }

        // Reply asap if the device is already found
        Device d;
        synchronized (this) {
            d = mDevices.get(Integer.valueOf(key));
        }
        if (d!=null) {
            return d;
        }

        // Else we start a blocking request
        BlockingDeviceRequest request = new BlockingDeviceRequest(key);
        mUiHandler.postDelayed(new RequestTimeOutRunnable(request), timeOutInMs);
        mDeviceRequests.add(request);
        return request.block();
    }

    private class RequestTimeOutRunnable implements Runnable {
        final BlockingDeviceRequest mRequest;
        public RequestTimeOutRunnable(BlockingDeviceRequest request) {
            mRequest = request;
        }

        @Override
        public void run() {
            mRequest.timeOut();
            mDeviceRequests.remove(mRequest);
        }
    }

    final private Runnable timeOutRunnable = new Runnable() {
        @Override
        public void run() {

        }
    };

    /**
     * The listener to the Cling service
     */
    final RegistryListener mRegistryListener = new DefaultRegistryListener() {

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device)  { deviceAdded(device); }
        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device){ deviceRemoved(device); }
        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device)    { deviceAdded(device); }
        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device)  {  deviceRemoved(device); }

        private void deviceAdded(final Device device) {
            // Do not list devices with no mane, or with no content service
            String deviceName = device.getDisplayString();
            if (deviceName == null) return;
            Service service = device.findService(new UDAServiceId("ContentDirectory"));
            if (service == null) return;
            Action action = service.getAction("Browse");
            if (action == null) return;

            Log.d(TAG, "addDevice with hash code " + device.hashCode());
            synchronized (this) {
                // Add to list
                mDevices.put(Integer.valueOf(device.hashCode()), device);
                // Each time a new device is found, we check if there is an on-going blocking request for this device
                Iterator<BlockingDeviceRequest> iterator = mDeviceRequests.iterator();
                while (iterator.hasNext()) {
                    boolean found = iterator.next().checkIfDeviceIsFound(mDevices);
                    if (found) {
                        iterator.remove();
                    }
                }
            }
            informListenersOfDeviceListUpdate(mListeners);

        }

        private void deviceRemoved(Device device) {
            synchronized (this) {
                mDevices.remove(device);
            }
            informListenersOfDeviceListUpdate(mListeners);

        }
    };

    private void informListenersOfDeviceListUpdate(final List<Listener> listeners) {
        // Make a copy of the list to avoid concurrency issues
        final List<Device> copy = new ArrayList<Device>(mDevices.size());
        synchronized (this) {
            copy.addAll(getDevices());
        }

        // Tell the listener the discovery is over
        mUiHandler.post(new Runnable() {
            public void run() {
                synchronized (mListeners) {
                    for (Listener listener : listeners) {
                        listener.onDeviceListUpdate(copy);
                    }
                }
            }
        });
    }

    /**
     * returns -1 if not ready
     * @param callback
     * @return
     */
    protected int execute(ActionCallback callback) {
        if (mState!=State.RUNNING) {
            return -1;
        }

        mAndroidUpnpService.getControlPoint().execute(callback);
        return 0;
    }

    private Runnable  mPeriodicSearchRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAndroidUpnpService!=null) {
                if(DBG) Log.d(TAG, "mPeriodicSearchRunnable search");
                mAndroidUpnpService.getControlPoint().search(new UDADeviceTypeHeader(new UDADeviceType("MediaServer")));
            }
            // program next search
            mUiHandler.postDelayed(mPeriodicSearchRunnable, SERVER_SEARCH_PERIOD_MS); // probably does not have to be on UI thread but it makes no harm and avoid having yet another handler
        }
    };
    public String getDeviceFriendlyName(String key){
        Device device;
        synchronized (this) {
            device = mDevices.get(Integer.valueOf(key));
        }
        if (device!=null) {
            return getDeviceFriendlyName(device);
        }
        return null;
    }
    public static String getDeviceFriendlyName(Device device) {
        // Get friendly name if it exists
        String name = device.getDisplayString();
        if (device.getDetails()!=null) {
            String friendlyName = device.getDetails().getFriendlyName();
            if (friendlyName!=null && !friendlyName.isEmpty()) {
                name = friendlyName;
            }
        }
        return name;
    }

    public static Uri getDeviceUri(Device device) {
        // Root directory is always "0"
        return Uri.parse("upnp://" + device.hashCode() + "/0");
    }
}
