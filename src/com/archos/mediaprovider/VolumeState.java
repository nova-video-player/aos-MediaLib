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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;

import com.archos.filecorelibrary.ExtStorageManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** volume mount state */
public class VolumeState {
    protected static final String TAG = ArchosMediaCommon.TAG_PREFIX + VolumeState.class.getSimpleName();
    protected static final boolean DBG = false;

    /**
     * Typically non-removable primary external storage. At least for recent devices
     */
    public static final int STORAGE_ID_PRIMARY_VOLUME = Volume.getStorageId(0);

    public static class Volume {

        private final String mMountPoint;
        private final int mStorageId;
        private int mMounted = -1;

        public Volume(String mountPoint, int volumeIndex) {
            mMountPoint = mountPoint;
            mStorageId = getStorageId(volumeIndex);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                mMounted = 1;
        }

        /* copy from android.mtp.MtpStorage */
        public static int getStorageId(int index) {
            // storage ID is 0x00010001 for primary storage,
            // then 0x00020001, 0x00030001, etc. for secondary storages
            return ((index + 1) << 16) + 1;
        }

        public boolean setMountState(boolean mounted) {
            int newMounted = mounted ? 1 : 0;
            if (newMounted != mMounted) {
                mMounted = newMounted;
                if (DBG) Log.d(TAG, "Mount state changed for " + mMountPoint + " : " + mMounted);
                return true;
            }
            return false;
        }

        public boolean getMountState() {
            // unknown = false
            return mMounted == 1;
        }

        public int getStorageId() {
            return mStorageId;
        }

        public String getMountPoint() {
            return mMountPoint;
        }
    }

    // ------------------------------ PUBLIC API ---------------------------- //
    private final Context mContext;
    private final StorageManager mStorageManager;
    public VolumeState(Context context) {
        Context applicationContext = context.getApplicationContext();
        mContext = applicationContext != null ? applicationContext : context;
        mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
    }

    // ---------------------------- OBSERVER STUFF ------------------------- //
    /** implement & register for observing hasLocalConnection() */
    public interface Observer {
        public void onMountStateChanged(Volume... volumes);
    }

    // abusing WeakHashMap to have a list of WeakReferences to Observers
    private final WeakHashMap<Observer, Void> mObservers = new WeakHashMap<Observer, Void>();

    public void addObserver(Observer observer) {
        if (observer == null) {
            throw new NullPointerException();
        }
        synchronized (this) {
            if (!mObservers.containsKey(observer))
                mObservers.put(observer, null);
        }
    }

    public synchronized void removeObserver(Observer observer) {
        mObservers.remove(observer);
    }

    protected synchronized void handleChange(Volume... volumes) {
        for(Observer observer : mObservers.keySet()) {
            if (observer != null)
                observer.onMountStateChanged(volumes);
        }
    }

    // -------------------------BROADCAST RECEIVER STUFF ------------------- //
    private static final IntentFilter INTENT_FILTER_UNMOUNT = new IntentFilter();
    static {
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_CHECKING);
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_EJECT);
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_NOFS);
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_REMOVED);
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_SHARED);
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
        INTENT_FILTER_UNMOUNT.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        INTENT_FILTER_UNMOUNT.addDataScheme("file");
    }

    private static final IntentFilter INTENT_FILTER_MOUNT = new IntentFilter();
    static {
        INTENT_FILTER_MOUNT.addAction(Intent.ACTION_MEDIA_MOUNTED);
        INTENT_FILTER_MOUNT.addDataScheme("file");
    }

    private boolean mMountReceiversRegistered;
    private final BroadcastReceiver mUnMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String path = intent.getData().getPath();
            ensureVolumes();
            for (Volume volume : mVolumes) {
                if (volume.getMountPoint().equals(path)
                        && volume.setMountState(false))
                    handleChange(volume);
            }
        }
    };
    private final BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String path = intent.getData().getPath();
            ensureVolumes();
            for (Volume volume : mVolumes) {
                if (volume.getMountPoint().equals(path)
                        && volume.setMountState(true))
                    handleChange(volume);
            }
        }
    };

    public void registerReceiver() {
        if (!mMountReceiversRegistered) {
            mContext.registerReceiver(mMountReceiver, INTENT_FILTER_MOUNT);
            mContext.registerReceiver(mUnMountReceiver, INTENT_FILTER_UNMOUNT);
            mMountReceiversRegistered = true;
        }
    }
    public void unregisterReceiver() {
        if (mMountReceiversRegistered) {
            mContext.unregisterReceiver(mMountReceiver);
            mContext.unregisterReceiver(mUnMountReceiver);
            mMountReceiversRegistered = false;
        }
    }

    protected Volume[] mVolumes;
    /** @return false: nothing to do, true: set mVolumes */
    protected boolean ensureVolumes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return ensureVolumesV15();
        else
            return ensureVolumesV23();
    }

    private boolean ensureVolumesV15() {
        if (mVolumes != null) return false;
        String[] volumes = getVolumePaths(mStorageManager);
        if (volumes != null && volumes.length > 0) {
            mVolumes = new Volume[volumes.length];
            for (int i = 0; i < volumes.length; i++) {
                mVolumes[i] = new Volume(volumes[i], i);
            }
        } else {
            mVolumes = new Volume[1];
            mVolumes[0] = new Volume(Environment.getExternalStorageDirectory().getPath(), 0);
        }
        return true;
    }

    private boolean ensureVolumesV23() {
        if (mVolumes != null) return false;

        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        int i = 0;
        ArrayList<Volume> vols = new ArrayList<>();
        for(String s : storageManager.getExtSdcards()) {
            vols.add(new Volume(s, i));
            i++;
        }
        for(String s : storageManager.getExtUsbStorages()) {
            vols.add(new Volume(s, i));
            i++;
        }
        for(String s : storageManager.getExtOtherStorages()) {
            vols.add(new Volume(s, i));
            i++;
        }
        if (vols.size() > 0) {
            mVolumes = new Volume[vols.size()];
            mVolumes = vols.toArray(mVolumes);
        }
        else {
            mVolumes = new Volume[1];
            mVolumes[0] = new Volume(Environment.getExternalStorageDirectory().getPath(), 0);
        }
        return true;
    }

    public void updateState() {
        ensureVolumes();
        ArrayList<Volume> updates = new ArrayList<Volume>();
        for (Volume volume : mVolumes) {
            boolean update;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                update = volume.setMountState(evaluateMountState(ExtStorageManager.getVolumeState(volume.getMountPoint())));
            else
                update = volume.setMountState(true);

            if (update)
                updates.add(volume);
        }
        if (updates.size() > 0) {
            Volume[] updateArray = updates.toArray(new Volume[updates.size()]);
            handleChange(updateArray);
        }
    }

    private static boolean evaluateMountState(String state) {
        return Environment.MEDIA_MOUNTED.equals(state)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    private static String[] getVolumePaths(StorageManager storageManager) {
        try {
            Method getVolumePaths = StorageManager.class.getMethod("getVolumePaths");
            return (String[]) getVolumePaths.invoke(storageManager);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }
}
