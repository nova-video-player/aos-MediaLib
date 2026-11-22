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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Regression tests for issue #1403: when the app returns to foreground (e.g. after the
 * device woke up from sleep), an import must always be scheduled so that the volume
 * visibility flags are re-evaluated and files hidden while a USB volume was unmounted
 * during sleep are shown again. importMessageOnForeground() picks that import message.
 */
@RunWith(RobolectricTestRunner.class)
public class VideoStoreImportServiceTest {

    @Test
    @Config(sdk = 34)
    public void resumeImportIsIncrementalWhenDbIsCleanOnPostP() {
        // post-Android-P incremental import still re-evaluates volume visibility and is cheap
        assertEquals(VideoStoreImportService.MESSAGE_IMPORT_INCR,
                VideoStoreImportService.importMessageOnForeground(false));
    }

    @Test
    @Config(sdk = 34)
    public void resumeImportIsFullWhenDbIsDirty() {
        // a dirty db always requires a full import, whatever the API level
        assertEquals(VideoStoreImportService.MESSAGE_IMPORT_FULL,
                VideoStoreImportService.importMessageOnForeground(true));
    }

    @Test
    @Config(sdk = 28)
    public void resumeImportIsFullOnPreP() {
        // pre-Android-P relies on the storage_id based full import pass
        assertEquals(VideoStoreImportService.MESSAGE_IMPORT_FULL,
                VideoStoreImportService.importMessageOnForeground(false));
    }

    @Test
    @Config(sdk = 26)
    public void resumeImportIsFullOnOldDevices() {
        assertEquals(VideoStoreImportService.MESSAGE_IMPORT_FULL,
                VideoStoreImportService.importMessageOnForeground(false));
    }

    @Test
    @Config(sdk = 34)
    public void fullAndIncrementalMessagesAreDistinct() {
        assertNotEquals(VideoStoreImportService.MESSAGE_IMPORT_FULL, VideoStoreImportService.MESSAGE_IMPORT_INCR);
    }
}
