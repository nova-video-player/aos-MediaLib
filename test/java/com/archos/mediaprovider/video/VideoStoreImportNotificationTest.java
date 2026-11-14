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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Message;
import android.provider.MediaStore;

import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.MediaRetrieverServiceClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Method;
import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33})
public class VideoStoreImportNotificationTest {
    private ServiceController<VideoStoreImportService> controller;
    private VideoStoreImportService service;
    private VideoStoreImportImpl importer;
    private ContentResolver resolver;
    private MediaRetrieverServiceClient retriever;
    private NotificationManager notifications;

    @Before
    public void setUp() {
        controller = Robolectric.buildService(VideoStoreImportService.class).create();
        service = controller.get();
        importer = ReflectionHelpers.getField(service, "mImporter");
        retriever = mock(MediaRetrieverServiceClient.class);
        ReflectionHelpers.setField(importer, "mMediaRetrieverServiceClient", retriever);
        ReflectionHelpers.setStaticField(VideoStoreImportImpl.class, "mIsImportInterrupted", false);
        resolver = mock(ContentResolver.class);
        notifications = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @After
    public void tearDown() {
        controller.destroy();
        ImportState.VIDEO.setRemainingCount(0);
        ImportState.VIDEO.setState(ImportState.State.IDLE);
    }

    @Test
    public void screenshotIsClassifiedWithoutVideoRetrievalOrNotification() throws Exception {
        MatrixCursor cursor = scanCursor();
        cursor.addRow(new Object[] {42L, "/storage/emulated/0/Pictures/Screenshots/test.png", 0});

        scan(cursor);

        assertNull(notification());
        verifyNoInteractions(retriever);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ArrayList<ContentProviderOperation>> operations =
                ArgumentCaptor.forClass(ArrayList.class);
        verify(resolver).applyBatch(eq(VideoStore.AUTHORITY), operations.capture());
        assertEquals(1, operations.getValue().size());
        ContentValues values = operations.getValue().get(0).resolveValueBackReferences(
                null, 0);
        assertEquals("image/png", values.getAsString(MediaStore.MediaColumns.MIME_TYPE));
        assertEquals(Integer.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                values.getAsInteger(MediaStore.Files.FileColumns.MEDIA_TYPE));
        assertTrue(values.getAsLong(VideoStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE) > 0);
        assertTrue(cursor.isClosed());
    }

    @Test
    public void videoProgressIsRemovedOnCompletionWhileServiceStaysAlive() throws Exception {
        MatrixCursor cursor = scanCursor();
        cursor.addRow(new Object[] {43L, "/storage/emulated/0/Movies/test.mp4", 1});

        scan(cursor);

        assertNotNull(notification());
        assertEquals("file:///storage/emulated/0/Movies/test.mp4",
                notification().extras.getString(Notification.EXTRA_TEXT));
        verify(retriever).getMetadata(eq("/storage/emulated/0/Movies/test.mp4"),
                any(), any());
        finishWork();
        assertNull(notification());
        assertTrue(service.mHandler.getLooper().getThread().isAlive());
    }

    @Test
    public void emptyScanStillClearsPreviouslyPostedProgress() throws Exception {
        service.updateScanNotification(0, "file:///storage/emulated/0/Pictures/test.png");
        assertNotNull(notification());

        scan(scanCursor());
        finishWork();

        assertNull(notification());
        verifyNoInteractions(resolver, retriever);
    }

    @Test
    public void nullCursorDoesNotPreventCompletion() throws Exception {
        service.updateScanNotification(0, "test.mp4");

        scan(null);
        finishWork();

        assertNull(notification());
        verifyNoInteractions(resolver, retriever);
    }

    @Test
    public void cleanupProgressIsAlsoRemovedOnCompletion() {
        service.updateDeleteNotification(1, "/storage/emulated/0/Movies/removed.mp4");
        assertNotNull(notification());

        finishWork();

        assertNull(notification());
    }

    private MatrixCursor scanCursor() {
        return new MatrixCursor(new String[] {"_id", "_data", "ArchosMediaScraper_id"});
    }

    private void scan(Cursor cursor) throws Exception {
        Method scan = VideoStoreImportImpl.class.getDeclaredMethod("handleScanCursor",
                Cursor.class, ContentResolver.class, Context.class, Blacklist.class);
        scan.setAccessible(true);
        scan.invoke(importer, cursor, resolver, service, mock(Blacklist.class));
    }

    private void finishWork() {
        int kill = ReflectionHelpers.getStaticField(VideoStoreImportService.class, "MESSAGE_KILL");
        int keepAlive = ReflectionHelpers.getStaticField(VideoStoreImportService.class, "DONT_KILL_SELF");
        service.handleMessage(Message.obtain(service.mHandler, kill, keepAlive, 0));
    }

    private Notification notification() {
        return Shadows.shadowOf(notifications).getNotification(VideoStoreImportService.NOTIFICATION_ID);
    }
}
