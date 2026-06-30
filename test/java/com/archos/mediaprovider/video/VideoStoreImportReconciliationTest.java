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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class VideoStoreImportReconciliationTest {

    private static final String PRIMARY = "/storage/emulated/0";
    private static final String[] MEDIA_COLUMNS = {
            "_id", "_data", "_display_name", "_size", "date_added", "date_modified",
            "bucket_id", "bucket_display_name", "format", "parent"
    };

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void movedPrimaryFileIsUpdatedInPlace() throws Exception {
        ContentResolver resolver = mock(ContentResolver.class);
        MatrixCursor imported = importedCursor(42L, PRIMARY + "/Download/Movie.mkv");
        MatrixCursor media = mediaCursor(42L, PRIMARY + "/Movies/Movie.mkv");
        stubQueries(resolver, imported, media);

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcilePrimaryStorageRows(resolver, PRIMARY);

        assertEquals(1, result.updated);
        assertEquals(0, result.removed);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ArrayList<ContentProviderOperation>> operations =
                ArgumentCaptor.forClass(ArrayList.class);
        verify(resolver).applyBatch(eq(VideoStore.AUTHORITY), operations.capture());
        assertEquals(2, operations.getValue().size());
        assertEquals(VideoStoreInternal.FILES_IMPORT, operations.getValue().get(0).getUri());
        assertEquals(VideoStoreInternal.FILES, operations.getValue().get(1).getUri());
        verify(resolver, never()).delete(any(Uri.class), anyString(), any(String[].class));
    }

    @Test
    public void missingPrimaryFileIsRemoved() throws Exception {
        ContentResolver resolver = mock(ContentResolver.class);
        MatrixCursor imported = importedCursor(43L, PRIMARY + "/Movies/Missing.mkv");
        MatrixCursor media = new MatrixCursor(MEDIA_COLUMNS);
        stubQueries(resolver, imported, media);
        when(resolver.delete(eq(VideoStoreInternal.FILES_IMPORT), eq("_id=?"),
                any(String[].class))).thenReturn(1);

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcilePrimaryStorageRows(resolver, PRIMARY);

        assertEquals(0, result.updated);
        assertEquals(1, result.removed);
        verify(resolver).delete(eq(VideoStoreInternal.FILES_IMPORT), eq("_id=?"),
                any(String[].class));
        verify(resolver, never()).applyBatch(anyString(), any());
    }

    @Test
    public void primaryReconciliationDoesNotTouchRemovableStorageRows() throws Exception {
        ContentResolver resolver = mock(ContentResolver.class);
        MatrixCursor imported = importedCursor(44L, "/storage/ABCD-1234/Movies/Movie.mkv");
        MatrixCursor media = new MatrixCursor(MEDIA_COLUMNS);
        stubQueries(resolver, imported, media);

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcilePrimaryStorageRows(resolver, PRIMARY);

        assertEquals(0, result.updated);
        assertEquals(0, result.removed);
        verify(resolver, never()).delete(any(Uri.class), anyString(), any(String[].class));
        verify(resolver, never()).applyBatch(anyString(), any());
    }

    @Test
    public void movedFileOnMountedRemovableStorageIsUpdatedWithoutDeletingMissingRows()
            throws Exception {
        String removable = "/storage/ABCD-1234";
        ContentResolver resolver = mock(ContentResolver.class);
        MatrixCursor imported = importedCursor(44L, removable + "/FolderA/Movie.mkv");
        MatrixCursor media = mediaCursor(44L, removable + "/FolderB/Movie.mkv");
        imported.addRow(new Object[] { 45L, removable + "/FolderA/Missing.mkv" });
        stubQueries(resolver, imported, media);

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileStorageRows(
                        resolver, removable, 1234, false);

        assertEquals(1, result.updated);
        assertEquals(0, result.removed);
        verify(resolver).applyBatch(eq(VideoStore.AUTHORITY), any());
        verify(resolver, never()).delete(any(Uri.class), anyString(), any(String[].class));
    }

    @Test
    public void existingPrimaryFileIsRetainedWhenMediaStoreTemporarilyOmitsIt() throws Exception {
        File primary = temporaryFolder.newFolder("primary");
        File video = new File(primary, "Movie.mkv");
        assertEquals(true, video.createNewFile());
        ContentResolver resolver = mock(ContentResolver.class);
        MatrixCursor imported = importedCursor(45L, video.getAbsolutePath());
        MatrixCursor media = new MatrixCursor(MEDIA_COLUMNS);
        stubQueries(resolver, imported, media);

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcilePrimaryStorageRows(
                        resolver, primary.getAbsolutePath());

        assertEquals(0, result.updated);
        assertEquals(0, result.removed);
        verify(resolver, never()).delete(any(Uri.class), anyString(), any(String[].class));
    }

    @Test
    public void reconciliationMarkerAllowsOnlyTheImporterPathUpdate() {
        ContentValues reconciliation = new ContentValues();
        reconciliation.put("_id", 42L);
        reconciliation.put("_data", PRIMARY + "/Movies/Movie.mkv");
        reconciliation.put(VideoStoreInternal.KEY_IMPORT_RECONCILE_PATH, true);

        VideoProvider.sanitizeImportedFileUpdate(reconciliation);

        assertEquals(PRIMARY + "/Movies/Movie.mkv", reconciliation.getAsString("_data"));
        assertEquals(false, reconciliation.containsKey("_id"));
        assertEquals(false, reconciliation.containsKey(
                VideoStoreInternal.KEY_IMPORT_RECONCILE_PATH));

        ContentValues ordinaryUpdate = new ContentValues();
        ordinaryUpdate.put("_data", PRIMARY + "/Movies/Other.mkv");
        VideoProvider.sanitizeImportedFileUpdate(ordinaryUpdate);
        assertEquals(false, ordinaryUpdate.containsKey("_data"));

        ContentValues externalUpdate = new ContentValues();
        externalUpdate.put("_data", PRIMARY + "/Movies/External.mkv");
        externalUpdate.put(VideoStoreInternal.KEY_IMPORT_RECONCILE_PATH, true);
        VideoProvider.sanitizeImportedFileUpdate(externalUpdate, false);
        assertEquals(false, externalUpdate.containsKey("_data"));
        assertEquals(false, externalUpdate.containsKey(
                VideoStoreInternal.KEY_IMPORT_RECONCILE_PATH));
    }

    private static MatrixCursor importedCursor(long id, String path) {
        MatrixCursor cursor = new MatrixCursor(new String[] { "_id", "_data" });
        cursor.addRow(new Object[] { id, path });
        return cursor;
    }

    private static MatrixCursor mediaCursor(long id, String path) {
        MatrixCursor cursor = new MatrixCursor(MEDIA_COLUMNS);
        cursor.addRow(new Object[] {
                id, path, "Movie.mkv", 1234L, 100L, 200L, "bucket", "Movies", 0, 1
        });
        return cursor;
    }

    private static void stubQueries(ContentResolver resolver, Cursor imported, Cursor media) {
        when(resolver.query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                anyString(), any(String[].class), isNull())).thenReturn(imported);
        when(resolver.query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), anyString(), any(String[].class), anyString()))
                .thenReturn(media);
    }
}
