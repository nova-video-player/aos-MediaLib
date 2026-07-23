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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    public void discoveredUsbVolumeIsNotFilteredByLegacyMountState() {
        String usb = "/storage/39F3-140A";

        Set<String> paths = VideoStoreImportImpl.collectDiscoveredRemovableStoragePaths(
                PRIMARY, Collections.emptyList(), Arrays.asList(usb),
                Collections.emptyList());

        assertEquals(Collections.singleton(usb), paths);
    }

    @Test
    public void changedIdMoveWithAmbiguousIdentityIsNotRemapped() throws Exception {
        String removable = "/storage/ABCD-1234";
        ContentResolver resolver = mock(ContentResolver.class);
        MatrixCursor imported = importIdentityCursor();
        imported.addRow(new Object[] {
                44L, removable + "/FolderA/Movie.mkv", "Movie.mkv", 1234L, 200L
        });
        MatrixCursor media = mediaCursor(84L, removable + "/FolderB/Movie.mkv");
        media.addRow(new Object[] {
                85L, removable + "/FolderC/Movie.mkv", "Movie.mkv", 1234L,
                100L, 202L, "bucket", "FolderC", 0, 1
        });
        stubQueries(resolver, imported, media);

        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileChangedMediaStoreIds(
                        resolver, removable, 1234);

        assertEquals(0, result.updated);
        verify(resolver, never()).applyBatch(anyString(), any());
    }

    @Test
    public void changedIdMoveAcrossMountedUsbVolumesUsesOneSnapshotPass() throws Exception {
        String sourceStorage = "/storage/AAAA-1111";
        String destinationStorage = "/storage/BBBB-2222";
        String oldPath = sourceStorage + "/Movies/Movie.mkv";
        String newPath = destinationStorage + "/Movies/Movie.mkv";
        ContentResolver resolver = mock(ContentResolver.class);

        when(resolver.query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                any(Bundle.class), isNull())).thenAnswer(invocation -> {
                    String prefix = invocation.<Bundle>getArgument(2)
                            .getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)[0];
                    MatrixCursor cursor = importIdentityCursor();
                    if (prefix.startsWith(sourceStorage)) {
                        cursor.addRow(new Object[] {
                                44L, oldPath, "Movie.mkv", 1234L, 200L
                        });
                    }
                    return cursor;
        });
        when(resolver.query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), any(Bundle.class), isNull()))
                .thenAnswer(invocation -> {
                    String prefix = invocation.<Bundle>getArgument(2)
                            .getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)[0];
                    return prefix.startsWith(destinationStorage)
                            ? mediaCursor(84L, newPath) : new MatrixCursor(MEDIA_COLUMNS);
                });

        List<VideoStoreImportImpl.StorageLocation> locations = Arrays.asList(
                new VideoStoreImportImpl.StorageLocation(sourceStorage, 1111, false),
                new VideoStoreImportImpl.StorageLocation(destinationStorage, 2222, false));
        VideoStoreImportImpl.ReconciliationSnapshot snapshot =
                VideoStoreImportImpl.loadMountedStorageSnapshot(resolver, locations);
        VideoStoreImportImpl.LocalReconciliationResult result =
                VideoStoreImportImpl.reconcileStorageSnapshot(resolver, snapshot);

        assertEquals(1, result.updated);
        assertEquals(0, result.removed);
        verify(resolver, times(2)).query(eq(VideoStoreInternal.FILES_IMPORT),
                any(String[].class), any(Bundle.class), isNull());
        verify(resolver, times(2)).query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), any(Bundle.class), isNull());
        verify(resolver).applyBatch(eq(VideoStore.AUTHORITY), any());
    }

    @Test
    public void snapshotUsesKeysetPaginationForBothIdentitySources() {
        String removable = "/storage/ABCD-1234";
        ContentResolver resolver = mock(ContentResolver.class);

        when(resolver.query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                any(Bundle.class), isNull())).thenAnswer(invocation -> {
                    Bundle queryArgs = invocation.getArgument(2);
                    String[] selectionArgs = queryArgs.getStringArray(
                            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
                    long afterId = Long.parseLong(selectionArgs[1]);
                    MatrixCursor cursor = importIdentityCursor();
                    if (afterId < 0) {
                        for (long id = 1; id <= 2500; id++) {
                            cursor.addRow(new Object[] {
                                    id, removable + "/Movies/Movie-" + id + ".mkv",
                                    "Movie-" + id + ".mkv", 1234L, 200L
                            });
                        }
                        // Providers can ignore QUERY_ARG_LIMIT. The loader must stop consuming
                        // this cursor at 2,500 and request the remainder with a keyset boundary.
                        cursor.addRow(new Object[] {
                                3000L, removable + "/Movies/New.mkv",
                                "New.mkv", 1234L, 200L
                        });
                    } else if (afterId == 2500) {
                        // Simulates the next surviving row after concurrent changes below the
                        // keyset boundary. OFFSET pagination could shift and skip this row.
                        cursor.addRow(new Object[] {
                                3000L, removable + "/Movies/New.mkv",
                                "New.mkv", 1234L, 200L
                        });
                    }
                    return cursor;
                });
        when(resolver.query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), any(Bundle.class), isNull()))
                .thenAnswer(invocation -> {
                    Bundle queryArgs = invocation.getArgument(2);
                    String[] selectionArgs = queryArgs.getStringArray(
                            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
                    long afterId = Long.parseLong(selectionArgs[1]);
                    MatrixCursor cursor = new MatrixCursor(MEDIA_COLUMNS);
                    if (afterId < 0) {
                        for (long id = 1; id <= 2500; id++) {
                            cursor.addRow(new Object[] {
                                    id, removable + "/Movies/Movie-" + id + ".mkv",
                                    "Movie-" + id + ".mkv", 1234L, 100L, 200L,
                                    "bucket", "Movies", 0, 1
                            });
                        }
                        cursor.addRow(new Object[] {
                                3000L, removable + "/Movies/New.mkv", "New.mkv",
                                1234L, 100L, 200L, "bucket", "Movies", 0, 1
                        });
                    } else if (afterId == 2500) {
                        cursor.addRow(new Object[] {
                                3000L, removable + "/Movies/New.mkv", "New.mkv",
                                1234L, 100L, 200L, "bucket", "Movies", 0, 1
                        });
                    }
                    return cursor;
                });

        VideoStoreImportImpl.ReconciliationSnapshot snapshot =
                VideoStoreImportImpl.loadMountedStorageSnapshot(resolver,
                        Collections.singletonList(
                                new VideoStoreImportImpl.StorageLocation(
                                        removable, 1234, false)));

        assertTrue(snapshot.complete);
        assertEquals(2501, snapshot.importedIds.size());
        assertTrue(snapshot.importedIds.contains(3000L));
        assertEquals(2501, snapshot.mediaStoreIds.size());
        assertTrue(snapshot.mediaStoreIds.contains(3000L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Bundle> importedQueryArgs = ArgumentCaptor.forClass(Bundle.class);
        verify(resolver, times(2)).query(eq(VideoStoreInternal.FILES_IMPORT),
                any(String[].class), importedQueryArgs.capture(), isNull());
        String[] secondSelectionArgs = importedQueryArgs.getAllValues().get(1).getStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
        assertEquals("2500", secondSelectionArgs[1]);
        assertTrue(importedQueryArgs.getAllValues().get(1)
                .getString(ContentResolver.QUERY_ARG_SQL_SELECTION).contains("_id>?"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Bundle> mediaStoreQueryArgs = ArgumentCaptor.forClass(Bundle.class);
        verify(resolver, times(2)).query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), mediaStoreQueryArgs.capture(), isNull());
        assertEquals("2500", mediaStoreQueryArgs.getAllValues().get(1)
                .getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)[1]);
    }

    @Test
    @Config(sdk = 29)
    public void snapshotUsesLegacySortLimitBeforeAndroidR() {
        String removable = "/storage/ABCD-1234";
        ContentResolver resolver = mock(ContentResolver.class);
        when(resolver.query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                anyString(), any(String[].class), anyString()))
                .thenReturn(importedCursor(42L, removable + "/Movies/Movie.mkv"));
        when(resolver.query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), anyString(), any(String[].class), anyString()))
                .thenReturn(new MatrixCursor(MEDIA_COLUMNS));

        VideoStoreImportImpl.ReconciliationSnapshot snapshot =
                VideoStoreImportImpl.loadMountedStorageSnapshot(resolver,
                        Collections.singletonList(
                                new VideoStoreImportImpl.StorageLocation(
                                        removable, 1234, false)));

        assertTrue(snapshot.complete);
        assertEquals(Collections.singleton(42L), snapshot.importedIds);
        verify(resolver).query(eq(VideoStoreInternal.FILES_IMPORT), any(String[].class),
                eq("_data LIKE ? AND _id>?"),
                eq(new String[] { removable + "/%", "-1" }),
                eq("_id ASC LIMIT 2500"));
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

        ContentValues idReconciliation = new ContentValues();
        idReconciliation.put("_id", 84L);
        idReconciliation.put("_data", PRIMARY + "/Movies/Remapped.mkv");
        idReconciliation.put(VideoStoreInternal.KEY_IMPORT_RECONCILE_ID, true);
        VideoProvider.sanitizeImportedFileUpdate(idReconciliation, true);
        assertEquals(84L, idReconciliation.getAsLong("_id").longValue());
        assertEquals(PRIMARY + "/Movies/Remapped.mkv",
                idReconciliation.getAsString("_data"));
        assertEquals(false, idReconciliation.containsKey(
                VideoStoreInternal.KEY_IMPORT_RECONCILE_ID));

        ContentValues externalIdUpdate = new ContentValues();
        externalIdUpdate.put("_id", 85L);
        externalIdUpdate.put("_data", PRIMARY + "/Movies/External-remap.mkv");
        externalIdUpdate.put(VideoStoreInternal.KEY_IMPORT_RECONCILE_ID, true);
        VideoProvider.sanitizeImportedFileUpdate(externalIdUpdate, false);
        assertEquals(false, externalIdUpdate.containsKey("_id"));
        assertEquals(false, externalIdUpdate.containsKey("_data"));
    }

    private static MatrixCursor importedCursor(long id, String path) {
        MatrixCursor cursor = new MatrixCursor(new String[] { "_id", "_data" });
        cursor.addRow(new Object[] { id, path });
        return cursor;
    }

    private static MatrixCursor importIdentityCursor() {
        return new MatrixCursor(new String[] {
                "_id", "_data", "_display_name", "_size", "date_modified"
        });
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
                any(Bundle.class), isNull())).thenReturn(imported);
        when(resolver.query(eq(MediaStore.Files.getContentUri("external")),
                any(String[].class), any(Bundle.class), isNull()))
                .thenReturn(media);
    }
}
