// Copyright 2026 Courville Software
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.net.Uri;

import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediascraper.AutoScrapeService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NetworkScannerServiceVideoTest {

    @Before
    public void setUp() {
        AutoScrapeService.resetNetworkScanCount();
    }

    @After
    public void tearDown() {
        AutoScrapeService.resetNetworkScanCount();
    }

    @Test
    public void duplicateQueuedRequestReleasesItsBatchSlot() throws Exception {
        NetworkScannerServiceVideo service = new NetworkScannerServiceVideo();
        Uri uri = Uri.parse("smb://server/share");
        queuedRequests(service).put(uri.toString(), new Object());
        long batchId = AutoScrapeService.startNetworkScanBatch(1);
        Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FILE, uri)
                .putExtra(NetworkScannerServiceVideo.EXTRA_SCAN_BATCH_ID, batchId);

        service.onStartCommand(intent, 0, 1);

        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    @Test
    public void uncheckedScanFailureStillCompletesBatch() {
        RuntimeException failure = new RuntimeException("injected scan failure");
        NetworkScannerServiceVideo service = new NetworkScannerServiceVideo() {
            @Override
            ScanResult performScan(Uri what) {
                throw failure;
            }
        };
        long batchId = AutoScrapeService.startNetworkScanBatch(1);

        try {
            service.doScan(Uri.parse("smb://server/share"), batchId);
            fail("Expected the injected scan failure");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }

        assertEquals(0, AutoScrapeService.getNetworkScanCount());
    }

    @Test
    public void updateScanNotificationPostsNotification() {
        NetworkScannerServiceVideo service = org.robolectric.Robolectric.buildService(NetworkScannerServiceVideo.class).create().get();
        service.updateScanNotification("smb://server/share/folder", 25);
        android.app.NotificationManager nm = (android.app.NotificationManager) service.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        android.app.Notification notif = org.robolectric.Shadows.shadowOf(nm).getNotification(NetworkScannerServiceVideo.NOTIFICATION_ID);
        org.junit.Assert.assertNotNull(notif);
        org.junit.Assert.assertTrue(notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().contains("25"));
    }

    @Test
    public void updateDeleteNotificationPostsNotification() {
        NetworkScannerServiceVideo service = org.robolectric.Robolectric.buildService(NetworkScannerServiceVideo.class).create().get();
        service.updateDeleteNotification("smb://server/share/folder", 50);
        android.app.NotificationManager nm = (android.app.NotificationManager) service.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        android.app.Notification notif = org.robolectric.Shadows.shadowOf(nm).getNotification(NetworkScannerServiceVideo.NOTIFICATION_ID);
        org.junit.Assert.assertNotNull(notif);
        org.junit.Assert.assertTrue(notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().contains("50"));
    }

    @Test
    public void videoStoreImportServiceUpdateDeleteNotificationPostsNotification() {
        VideoStoreImportService service = org.robolectric.Robolectric.buildService(VideoStoreImportService.class).create().get();
        service.updateDeleteNotification(12, "/sdcard/Movies/test.mp4");
        android.app.NotificationManager nm = (android.app.NotificationManager) service.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        android.app.Notification notif = org.robolectric.Shadows.shadowOf(nm).getNotification(VideoStoreImportService.NOTIFICATION_ID);
        org.junit.Assert.assertNotNull(notif);
        org.junit.Assert.assertTrue(notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().contains("12"));
    }

    @Test
    public void formatNotificationBodySplitsShareAndSubdir() {
        String[] lines = NetworkScannerServiceVideo.formatNotificationBody(
                "smb://server/share",
                "smb://server/share/Movies/Action"
        );
        assertEquals("smb://server/share", lines[0]);
        assertEquals("/Movies/Action", lines[1]);

        String[] rootLines = NetworkScannerServiceVideo.formatNotificationBody(
                "smb://server/share",
                "smb://server/share"
        );
        assertEquals("smb://server/share", rootLines[0]);
        assertEquals("/", rootLines[1]);
    }

    @Test
    public void formatNotificationBodyPreservesPortNumbers() {
        String[] lines = NetworkScannerServiceVideo.formatNotificationBody(
                "upnp://192.168.1.50:50001/v/Media",
                "upnp://192.168.1.50:50001/v/Media/Movies"
        );
        assertEquals("upnp://192.168.1.50:50001/v/Media", lines[0]);
        assertEquals("/Movies", lines[1]);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> queuedRequests(
            NetworkScannerServiceVideo service) throws Exception {
        Field field = NetworkScannerServiceVideo.class.getDeclaredField("mScanRequests");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) field.get(service);
    }
}
