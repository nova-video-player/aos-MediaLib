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

package com.archos.mediaprovider.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.ImportState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class VideoStoreImportServiceTest {
    @Test
    @Config(sdk = 34)
    public void foregroundCommandOwnsTheImportAndHonorsDirtyState() {
        VideoStoreImportService service = Robolectric.buildService(VideoStoreImportService.class).get();
        Looper looper = Looper.getMainLooper();
        List<Integer> imports = new ArrayList<>();
        service.mHandler = new Handler(looper, message -> imports.add(message.what));
        try {
            service.onStart(null);
            shadowOf(looper).idleFor(Duration.ofSeconds(2));
            assertTrue("lifecycle callback must not start a second import", imports.isEmpty());

            service.onStartCommand(new Intent(), 0, 1);
            service.onStartCommand(new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_IMPORT_INCR), 0, 2);
            shadowOf(looper).idleFor(Duration.ofSeconds(2));
            service.onStartCommand(new Intent(), 0, 3);
            shadowOf(looper).idleFor(Duration.ofSeconds(2));
            ImportState.VIDEO.setDirty(true);
            service.onStartCommand(new Intent(), 0, 4);
            shadowOf(looper).idleFor(Duration.ofSeconds(2));
            assertEquals(Arrays.asList(VideoStoreImportService.MESSAGE_IMPORT_FULL,
                    VideoStoreImportService.MESSAGE_IMPORT_INCR,
                    VideoStoreImportService.MESSAGE_IMPORT_FULL), imports);
        } finally {
            service.mVolumeState.unregisterReceiver();
            ImportState.VIDEO.setDirty(false);
        }
    }

    @Test
    @Config(sdk = 28)
    public void androidNineQueuesOneFullImportOnEachForegroundStart() {
        VideoStoreImportService service = Robolectric.buildService(VideoStoreImportService.class).get();
        Looper looper = Looper.getMainLooper();
        List<Integer> imports = new ArrayList<>();
        service.mHandler = new Handler(looper, message -> imports.add(message.what));
        service.onStartCommand(null, 0, 1);
        shadowOf(looper).idleFor(Duration.ofSeconds(2));
        service.onStartCommand(null, 0, 2);
        shadowOf(looper).idleFor(Duration.ofSeconds(2));
        assertEquals(Arrays.asList(VideoStoreImportService.MESSAGE_IMPORT_FULL,
                VideoStoreImportService.MESSAGE_IMPORT_FULL), imports);
    }
}
