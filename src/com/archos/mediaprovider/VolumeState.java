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
import android.os.Handler;
import android.os.storage.StorageManager;

import com.archos.filecorelibrary.ExtStorageManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** volume mount state */
public class VolumeState {
    private static final Logger log = LoggerFactory.getLogger(VolumeState.class);

    /**
     * Typically non-removable primary external storage. At least for recent devices
     */
    public static final int STORAGE_ID_PRIMARY_VOLUME = Volume.getStorageId(0);

    public static class Volume {

        private final String mMountPoint;
        private final int mStorageId;
        private int mMounted = -1;
        private boolean mMissingFromScan;

        public Volume(String mountPoint, int volumeIndex) {
            ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
            mMountPoint = mountPoint;
            Integer storageId = storageManager.getStorageId(mountPoint);
            if (storageId == null) {
                mStorageId = getStorageId(volumeIndex);
            } else {
                mStorageId = storageId;
            }
            if (log.isDebugEnabled()) log.debug("Volume: mMountPoint={}, volumeIndex={}, mStorageId={}", mMountPoint, volumeIndex, mStorageId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                mMounted = 1;
        }

        // This cannot be used anymore cf. https://cs.android.com/android/platform/superproject/+/master:frameworks/base/media/java/android/mtp/MtpStorage.java?q=MtpStorage.java
        /* copy from android.mtp.MtpStorage */
        public static int getStorageId(int index) {
            // storage ID is 0x00010001 for primary storage,
            // then 0x00020001, 0x00030001, etc. for secondary storages
            return ((index + 1) << 16) + 1;
        }

        public boolean setMountState(boolean mounted) {
            int newMounted = mounted ? 1 : 0;
            boolean rediscovered = mounted && mMissingFromScan;
            mMissingFromScan = false;
            if (newMounted != mMounted || rediscovered) {
                mMounted = newMounted;
                if (log.isDebugEnabled()) log.debug("setMountState: mount state changed for {} : {}", mMountPoint, mMounted);
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
        if (log.isDebugEnabled()) log.debug("handleChange: notifying {} observers about {} volumes", mObservers.size(), volumes.length);
        for (Volume v : volumes) {
            if (log.isDebugEnabled()) log.debug("handleChange: volume {} mounted={}", v.getMountPoint(), v.getMountState());
        }
        for(Observer observer : mObservers.keySet()) {
            if (observer != null) {
                if (log.isDebugEnabled()) log.debug("handleChange: notifying observer");
                observer.onMountStateChanged(volumes);
            }
        }
    }

    // -------------------------BROADCAST RECEIVER STUFF ------------------- //
    private static final IntentFilter MOUNT_EVENTS = new IntentFilter();
    static {
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_MOUNTED);
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_EJECT);
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_NOFS);
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_REMOVED);
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_SHARED);
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
        MOUNT_EVENTS.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        MOUNT_EVENTS.addDataScheme("file");
    }

    private BroadcastReceiver mMountReceiver;

    private void handleMountBroadcast(String path, boolean mounted) {
        if (path == null || path.isEmpty()) return;
        ensureVolumes();
        for (Volume volume : mVolumes) {
            if (path.equals(volume.getMountPoint())) {
                volume.setMountState(mounted);
                // A missed eject can leave the cache mounted while the database is hidden.
                // Forward an explicit mount even when the cached state already matches.
                handleChange(volume);
                return;
            }
        }

        Volume volume = new Volume(path, mVolumes.length);
        volume.setMountState(mounted);
        if (mounted) {
            mVolumes = Arrays.copyOf(mVolumes, mVolumes.length + 1);
            mVolumes[mVolumes.length - 1] = volume;
        }
        handleChange(volume);
    }

    public void registerReceiver(Handler worker) {
        if (mMountReceiver != null) return;
        mMountReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getData() == null) return;
                String path = intent.getData().getPath();
                boolean mounted = Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction());
                // Finish broadcast delivery promptly even if the worker is busy importing.
                worker.post(() -> handleMountBroadcast(path, mounted));
            }
        };
        mContext.registerReceiver(mMountReceiver, MOUNT_EVENTS);
    }

    public void unregisterReceiver() {
        if (mMountReceiver != null) {
            mContext.unregisterReceiver(mMountReceiver);
            mMountReceiver = null;
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

        // Use the same ordered snapshot for initialization and subsequent refreshes.
        // Primary storage occupies index zero even when USB is attached at startup.
        Set<String> mounted = currentMountedVolumePaths();
        mVolumes = new Volume[mounted.size()];
        int index = 0;
        for (String path : mounted) {
            mVolumes[index] = new Volume(path, index);
            index++;
        }
        return true;
    }

    public void updateState() {
        if (ensureVolumes() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return;
        List<Volume> tracked = new ArrayList<>(Arrays.asList(mVolumes));
        ArrayList<Volume> updates = new ArrayList<Volume>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            for (Volume volume : tracked) {
                if (volume.setMountState(evaluateMountState(ExtStorageManager.getVolumeState(volume.getMountPoint()))))
                    updates.add(volume);
            }
        } else {
            Set<String> newPaths = new LinkedHashSet<>(currentMountedVolumePaths());
            for (Volume volume : tracked) {
                String path = volume.getMountPoint();
                if (newPaths.remove(path)) {
                    if (volume.setMountState(true)) updates.add(volume);
                } else if (isConfirmedOffline(path)) {
                    if (volume.setMountState(false)) updates.add(volume);
                } else {
                    // A slow disk can disappear from enumeration while another one is ready.
                    // Keep its visibility, but refresh it once it appears again.
                    volume.mMissingFromScan = true;
                }
            }
            for (String path : newPaths) {
                Volume volume = new Volume(path, tracked.size());
                tracked.add(volume);
                updates.add(volume);
            }
        }
        mVolumes = tracked.toArray(new Volume[0]);
        if (updates.size() > 0) {
            Volume[] updateArray = updates.toArray(new Volume[updates.size()]);
            handleChange(updateArray);
        }
    }

    /**
     * @return the set of volume paths that are currently mounted: the primary external
     * storage plus every volume currently listed by ExtStorageManager (the lists are
     * refreshed from the system on each call). Overridable for tests.
     */
    protected Set<String> currentMountedVolumePaths() {
        Set<String> mounted = new LinkedHashSet<>();
        mounted.add(Environment.getExternalStorageDirectory().getPath());
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager(); // refreshes the volume lists
        addAllTo(mounted, storageManager.getExtSdcards());
        addAllTo(mounted, storageManager.getExtUsbStorages());
        addAllTo(mounted, storageManager.getExtOtherStorages());
        return mounted;
    }

    private static void addAllTo(Set<String> target, List<String> paths) {
        if (paths == null) return;
        for (String path : paths) {
            if (path != null && !path.isEmpty()) target.add(path);
        }
    }

    /** An unknown or transient state during wake is insufficient evidence to hide library rows. */
    public static boolean isConfirmedOffline(String path) {
        try {
            String state = Environment.getExternalStorageState(new File(path));
            return Environment.MEDIA_UNMOUNTED.equals(state) || Environment.MEDIA_REMOVED.equals(state)
                    || Environment.MEDIA_BAD_REMOVAL.equals(state) || Environment.MEDIA_UNMOUNTABLE.equals(state)
                    || Environment.MEDIA_EJECTING.equals(state) || Environment.MEDIA_SHARED.equals(state)
                    || Environment.MEDIA_NOFS.equals(state);
        } catch (IllegalArgumentException | SecurityException e) {
            log.debug("Unable to determine volume state for {}", path, e);
            return false;
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
            log.error("getVolumePaths: {}", e.getMessage(), e);
        } catch (IllegalAccessException e) {
            log.error("getVolumePaths: {}", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("getVolumePaths: {}", e.getMessage(), e);
        } catch (InvocationTargetException e) {
            log.error("getVolumePaths: {}", e.getMessage(), e);
        }
        return null;
    }
}
