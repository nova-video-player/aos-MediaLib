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

import com.archos.filecorelibrary.ExtStorageManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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

        public Volume(String mountPoint, int volumeIndex) {
            ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
            mMountPoint = mountPoint;
            Integer storageId = storageManager.getStorageId(mountPoint);
            if (storageId == null) {
                mStorageId = getStorageId(volumeIndex);;
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
            if (newMounted != mMounted) {
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
            if (log.isDebugEnabled()) log.debug("mUnMountReceiver: path={}", path);
            handleMountBroadcast(path, false);
        }
    };
    private final BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String path = intent.getData().getPath();
            if (log.isDebugEnabled()) log.debug("mMountReceiver: path={}", path);
            handleMountBroadcast(path, true);
        }
    };

    /**
     * Handles a mount/unmount broadcast for a given path: refreshes the tracked volume
     * list from the live ExtStorageManager lists, applies the broadcast state to the
     * matching volume (even if it was not tracked yet, e.g. a USB drive that mounted
     * while the app was in background during device sleep) and notifies observers of
     * every volume whose mount state changed.
     */
    private void handleMountBroadcast(String path, boolean mounted) {
        if (path == null || path.isEmpty()) return;
        ensureVolumes();
        List<Volume> tracked = new ArrayList<>(Arrays.asList(mVolumes));
        Set<String> mountedNow = currentMountedVolumePaths();
        // the broadcast is ground truth for this path even if the volume lists are not up to date yet
        if (mounted) mountedNow.add(path); else mountedNow.remove(path);
        List<Volume> changed = reconcileVolumeStates(tracked, mountedNow, true);
        mVolumes = tracked.toArray(new Volume[0]);
        // keep the old fallback: a broadcast path that is still not tracked (e.g. unmounted
        // while the app was in background) must still notify observers
        boolean pathTracked = false;
        for (Volume volume : tracked) {
            if (volume != null && volume.getMountPoint().equals(path)) {
                pathTracked = true;
                break;
            }
        }
        if (!pathTracked) {
            if (log.isDebugEnabled()) log.debug("handleMountBroadcast: {} not in tracked list, using temporary volume", path);
            Volume tempVolume = new Volume(path, 1000);
            tempVolume.mMounted = -1; // force the state change to be reported
            tempVolume.setMountState(mounted);
            changed.add(tempVolume);
        }
        if (!changed.isEmpty()) {
            if (log.isDebugEnabled()) log.debug("handleMountBroadcast: path={} mounted={} -> {} changed volumes", path, mounted, changed.size());
            handleChange(changed.toArray(new Volume[0]));
        }
    }

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
            if (log.isDebugEnabled()) log.debug("ensureVolumesV23: add volume {}", s);
            i++;
        }
        for(String s : storageManager.getExtUsbStorages()) {
            vols.add(new Volume(s, i));
            if (log.isDebugEnabled()) log.debug("ensureVolumesV23: add volume {}", s);
            i++;
        }
        for(String s : storageManager.getExtOtherStorages()) {
            vols.add(new Volume(s, i));
            if (log.isDebugEnabled()) log.debug("ensureVolumesV23: add volume {}", s);
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
        List<Volume> tracked = new ArrayList<>(Arrays.asList(mVolumes));
        ArrayList<Volume> updates = new ArrayList<Volume>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            for (Volume volume : tracked) {
                if (volume.setMountState(evaluateMountState(ExtStorageManager.getVolumeState(volume.getMountPoint()))))
                    updates.add(volume);
            }
        } else {
            // M+: reconcile the tracked volumes against the live volume lists instead of
            // assuming every volume is mounted. Assuming mounted used to hide the fact that
            // a volume was unmounted during device sleep, and prevented the re-mount (USB
            // drive back after wake) from being reported to observers when the mount
            // broadcast was missed while the app was in background. As a result no import
            // was triggered and files stayed hidden: the video library appeared empty until
            // a manual rescan (issue #1403).
            Set<String> mountedNow = currentMountedVolumePaths();
            boolean scanConclusive = isScanConclusive(mountedNow, tracked);
            updates.addAll(reconcileVolumeStates(tracked, mountedNow, scanConclusive));
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
        Set<String> mounted = new HashSet<>();
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

    /**
     * A volume scan is conclusive when it lists at least one non-primary volume, or when
     * no non-primary volume is tracked. If the scan comes back with only the primary
     * storage while external volumes are tracked, the scan most likely failed (or all
     * removable media is gone but we cannot tell the difference): in that case we must
     * NOT mass-unmount the tracked volumes, otherwise the whole library of a USB drive
     * would be hidden (empty video list, issue #1403).
     */
    private static boolean isScanConclusive(Set<String> mountedNow, List<Volume> tracked) {
        String primary = Environment.getExternalStorageDirectory().getPath();
        boolean onlyPrimaryMounted = true;
        for (String path : mountedNow) {
            if (!path.equals(primary)) {
                onlyPrimaryMounted = false;
                break;
            }
        }
        if (!onlyPrimaryMounted) return true;
        for (Volume volume : tracked) {
            if (volume != null && !volume.getMountPoint().equals(primary))
                return false; // external volume tracked but scan lists nothing: inconclusive
        }
        return true;
    }

    /**
     * Reconciles the tracked volumes with the set of currently mounted volume paths.
     * <ul>
     * <li>volumes present in {@code currentlyMounted} but not tracked yet are appended to
     * {@code tracked} and reported as mounted (this is how a volume that re-appeared after
     * a device sleep/wake cycle is discovered even if its mount broadcast was missed);</li>
     * <li>tracked volumes present in {@code currentlyMounted} are marked mounted;</li>
     * <li>tracked volumes absent from {@code currentlyMounted} are marked unmounted when
     * {@code scanConclusive} is true, otherwise their state is left untouched.</li>
     * </ul>
     *
     * @return the volumes whose mount state changed, in order. Package-visible for tests.
     */
    static List<Volume> reconcileVolumeStates(List<Volume> tracked, Set<String> currentlyMounted, boolean scanConclusive) {
        List<Volume> changed = new ArrayList<>();
        if (tracked == null || currentlyMounted == null) return changed;
        Set<String> trackedPaths = new HashSet<>();
        for (Volume volume : tracked) {
            if (volume != null) trackedPaths.add(volume.getMountPoint());
        }
        // discover newly mounted volumes first so they get tracked
        for (String path : currentlyMounted) {
            if (!trackedPaths.contains(path)) {
                Volume volume = new Volume(path, tracked.size());
                volume.mMounted = -1; // unknown until reconciled: guarantees the change is reported
                tracked.add(volume);
                if (volume.setMountState(true)) changed.add(volume);
            }
        }
        // then update the state of every tracked volume
        for (Volume volume : tracked) {
            if (volume == null) continue;
            if (currentlyMounted.contains(volume.getMountPoint())) {
                if (volume.setMountState(true)) changed.add(volume);
            } else if (scanConclusive) {
                if (volume.setMountState(false)) changed.add(volume);
            }
        }
        return changed;
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
