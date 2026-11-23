// Copyright 2025 Courville Software
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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.VolumeState.Volume;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.robolectric.shadows.ShadowEnvironment.setExternalStorageState;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Regression tests for issue #1403: after a device sleep/wake cycle the USB volume is
 * unmounted then mounted again while the app may be in background (broadcasts missed).
 * The volume mount state reconciliation must detect the re-mount so that the import
 * service unhides the files of that volume again (otherwise the video list stays empty
 * until a manual rescan).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VolumeStateTest {

    private static final String USB = "/storage/1234-5678";

    @Test
    public void staggeredWakeOnlyReportsConfirmedVolumeChanges() {
        String primary = Environment.getExternalStorageDirectory().getPath();
        String driveA = "/storage/A";
        String driveB = "/storage/B";
        Set<String> paths = new LinkedHashSet<>(Arrays.asList(primary, driveA, driveB));
        VolumeState state = new VolumeState(ApplicationProvider.getApplicationContext()) {
            @Override
            protected Set<String> currentMountedVolumePaths() {
                return paths;
            }
        };
        List<Volume> changes = new ArrayList<>();
        VolumeState.Observer observer = volumes -> Collections.addAll(changes, volumes);
        state.addObserver(observer);
        try {
            state.updateState();
            paths.remove(driveB);
            setExternalStorageState(new File(driveB), Environment.MEDIA_CHECKING);
            state.updateState();
            assertTrue(changes.isEmpty());
            assertTrue(state.mVolumes[2].getMountState());

            // A later scan sees B again, even though its mount broadcast was missed.
            paths.add(driveB);
            state.updateState();
            state.updateState();
            assertEquals(1, changes.size());
            assertEquals(driveB, changes.get(0).getMountPoint());
            assertTrue(changes.get(0).getMountState());

            paths.remove(driveB);
            setExternalStorageState(new File(driveB), Environment.MEDIA_UNMOUNTED);
            state.updateState();
            assertEquals(2, changes.size());
            assertFalse(changes.get(1).getMountState());
            assertTrue(state.mVolumes[1].getMountState());

            paths.add("/storage/C");
            state.updateState();
            assertEquals(3, changes.size());
            assertEquals("/storage/C", changes.get(2).getMountPoint());
            assertTrue(changes.get(2).getMountState());
        } finally {
            state.removeObserver(observer);
        }
    }

    @Test
    public void startupWithUsbAndEjectDuringPartialScanLeaveOtherDriveMounted() {
        Context context = ApplicationProvider.getApplicationContext();
        String primary = Environment.getExternalStorageDirectory().getPath();
        String secondUsb = "/storage/ABCD-1234";
        Set<String> paths = new LinkedHashSet<>(Arrays.asList(primary, USB, secondUsb));
        VolumeState state = new VolumeState(context) {
            @Override
            protected Set<String> currentMountedVolumePaths() {
                return paths;
            }
        };
        List<Volume> changes = new ArrayList<>();
        VolumeState.Observer observer = volumes -> Collections.addAll(changes, volumes);
        state.addObserver(observer);
        state.registerReceiver(new Handler(Looper.getMainLooper()));
        try {
            state.updateState();
            assertTrue("initial snapshot must not trigger an import", changes.isEmpty());
            assertEquals(primary, state.mVolumes[0].getMountPoint());
            Volume second = state.mVolumes[2];
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_CHECKING, Uri.parse("file://" + secondUsb)));
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue("checking is a transient state", changes.isEmpty());

            paths.clear();
            paths.add(primary);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_EJECT, Uri.parse("file://" + USB)));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, changes.size());
            assertEquals(USB, changes.get(0).getMountPoint());
            assertFalse(changes.get(0).getMountState());
            assertTrue(second.getMountState());
        } finally {
            state.unregisterReceiver();
            state.removeObserver(observer);
        }
    }

    @Test
    public void updateStateWithFailedScanKeepsExternalVolumeMounted() {
        Context context = ApplicationProvider.getApplicationContext();
        final String primary = Environment.getExternalStorageDirectory().getPath();
        // scan returns only the primary storage (e.g. reflection failure on some devices)
        final Set<String> mountedNow = new HashSet<>(Collections.singleton(primary));
        VolumeState volumeState = new VolumeState(context) {
            @Override
            protected Set<String> currentMountedVolumePaths() {
                return new HashSet<>(mountedNow);
            }
        };
        Volume usbVolume = new Volume(USB, 1);
        volumeState.mVolumes = new Volume[]{new Volume(primary, 0), usbVolume};

        final List<Volume[]> events = new ArrayList<>();
        volumeState.addObserver(volumes -> events.add(volumes));

        volumeState.updateState();
        assertTrue("a failed volume scan must not hide the USB library", events.isEmpty());
        assertTrue(usbVolume.getMountState());
    }
}
