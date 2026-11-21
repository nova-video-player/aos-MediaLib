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
import android.os.Environment;

import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.VolumeState.Volume;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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

    private static final String PRIMARY = "/storage/emulated/0";
    private static final String USB = "/storage/1234-5678";

    private static Set<String> mounted(String... paths) {
        return new HashSet<>(Arrays.asList(paths));
    }

    private static List<Volume> tracked(Volume... volumes) {
        return new ArrayList<>(Arrays.asList(volumes));
    }

    @Test
    public void noChangeWhenMountStatesAreUnchanged() {
        List<Volume> tracked = tracked(new Volume(PRIMARY, 0), new Volume(USB, 1));
        List<Volume> changed = VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY, USB), true);
        assertTrue("no volume should change when everything is still mounted", changed.isEmpty());
        assertEquals(2, tracked.size());
        assertTrue(tracked.get(0).getMountState());
        assertTrue(tracked.get(1).getMountState());
    }

    @Test
    public void sleepUnmountsVolumeAndWakeReportsRemount() {
        // initial state: primary + USB mounted
        List<Volume> tracked = tracked(new Volume(PRIMARY, 0), new Volume(USB, 1));
        assertTrue(VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY, USB), true).isEmpty());
        Volume usb = tracked.get(1);

        // device sleeps, USB power is cut: the volume disappears from the mounted lists
        List<Volume> changed = VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY), true);
        assertEquals("unmount during sleep must be detected", 1, changed.size());
        assertSame(usb, changed.get(0));
        assertFalse("USB must be reported unmounted after sleep", usb.getMountState());

        // device wakes up, USB is back: the re-mount MUST be reported so that the import
        // service unhides the files of that volume (issue #1403)
        changed = VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY, USB), true);
        assertEquals("re-mount after wake must be reported, otherwise the library stays empty", 1, changed.size());
        assertSame(usb, changed.get(0));
        assertTrue("USB must be reported mounted again after wake", usb.getMountState());
    }

    @Test
    public void volumeMountedWhileAppWasAwayIsDiscoveredAndReported() {
        // app was in background during sleep: USB was not tracked when it re-mounted
        List<Volume> tracked = tracked(new Volume(PRIMARY, 0));
        List<Volume> changed = VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY, USB), true);
        assertEquals("the newly mounted USB volume must be discovered", 1, changed.size());
        assertEquals(USB, changed.get(0).getMountPoint());
        assertTrue(changed.get(0).getMountState());
        assertEquals("the new volume must be tracked afterwards", 2, tracked.size());
        // reconciling again must be a no-op (no duplicate notifications)
        assertTrue(VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY, USB), true).isEmpty());
    }

    @Test
    public void inconclusiveScanDoesNotMassUnmountTrackedVolumes() {
        // if the volume scan fails or returns no external volume at all while external
        // volumes are tracked, we must NOT mark them unmounted: that would hide the whole
        // USB library and empty the video list (issue #1403)
        List<Volume> tracked = tracked(new Volume(PRIMARY, 0), new Volume(USB, 1));
        List<Volume> changed = VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY), false);
        assertTrue("inconclusive scan must not change any mount state", changed.isEmpty());
        assertTrue("USB must stay mounted on inconclusive scan", tracked.get(1).getMountState());
        assertTrue(tracked.get(0).getMountState());
    }

    @Test
    public void conclusiveScanUnmountsRemovedVolumeAndDiscoversNewOne() {
        // another external volume is still listed, so the scan is conclusive: the missing
        // USB volume can safely be marked unmounted and the sdcard is discovered as mounted
        List<Volume> tracked = tracked(new Volume(PRIMARY, 0), new Volume(USB, 1));
        String sdcard = "/storage/ABCD-1234";
        List<Volume> changed = VolumeState.reconcileVolumeStates(tracked, mounted(PRIMARY, sdcard), true);
        assertEquals(2, changed.size());
        // first the newly discovered sdcard (reported mounted), then the removed USB (unmounted)
        assertEquals(sdcard, changed.get(0).getMountPoint());
        assertTrue(changed.get(0).getMountState());
        assertEquals(USB, changed.get(1).getMountPoint());
        assertFalse(changed.get(1).getMountState());
        assertEquals(3, tracked.size());
    }

    @Test
    public void updateStateNotifiesObserversOnSleepAndWake() {
        Context context = ApplicationProvider.getApplicationContext();
        final String primary = Environment.getExternalStorageDirectory().getPath();
        final Set<String> mountedNow = new HashSet<>(Collections.singleton(primary));
        VolumeState volumeState = new VolumeState(context) {
            @Override
            protected Set<String> currentMountedVolumePaths() {
                return new HashSet<>(mountedNow);
            }
        };
        Volume primaryVolume = new Volume(primary, 0);
        Volume usbVolume = new Volume(USB, 1);
        volumeState.mVolumes = new Volume[]{primaryVolume, usbVolume};

        final List<Volume[]> events = new ArrayList<>();
        volumeState.addObserver(volumes -> events.add(volumes));

        // device sleeps: USB disappears from the mounted lists -> unmount notification
        volumeState.updateState();
        assertEquals("unmount during sleep must notify observers", 1, events.size());
        assertEquals(1, events.get(0).length);
        assertSame(usbVolume, events.get(0)[0]);
        assertFalse(events.get(0)[0].getMountState());

        // device wakes: USB is back -> mount notification driving the unhide import
        mountedNow.add(USB);
        volumeState.updateState();
        assertEquals("re-mount after wake must notify observers (issue #1403)", 2, events.size());
        assertEquals(1, events.get(1).length);
        assertSame(usbVolume, events.get(1)[0]);
        assertTrue(events.get(1)[0].getMountState());

        // nothing changed anymore -> no further notification
        volumeState.updateState();
        assertEquals(2, events.size());
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
