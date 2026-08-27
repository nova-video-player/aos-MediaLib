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

package com.archos.mediaprovider.video;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.text.TextUtils;

import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.FileEditor;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaMetadata;
import com.archos.mediaprovider.ArchosMediaFile;
import com.archos.mediaprovider.ArchosMediaFile.MediaFileType;
import com.archos.mediaprovider.BulkInserter;
import com.archos.mediaprovider.CustomCursorFactory.CustomCursor;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.ImportState.State;
import com.archos.mediaprovider.MediaRetrieverServiceClient;
import com.archos.mediaprovider.VolumeState;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.NfoParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.archos.filecorelibrary.FileUtils.isNetworkShare;


/**
 * The media db import logic
 */
public class VideoStoreImportImpl {
    private static final Logger log = LoggerFactory.getLogger(VideoStoreImportImpl.class);
    private final Context mContext;
    private final ContentResolver mCr;
    private final Blacklist mBlackList;
    private final MediaRetrieverServiceClient mMediaRetrieverServiceClient;

    private static final String MediaColumnsDATA = MediaColumns.DATA;

    private static final int WINDOW_SIZE = 2500;
    // Rows hidden longer than this are purged from files_import instead of kept forever (see #1909)
    private static final long HIDDEN_FILES_RETENTION_SECONDS = 30L * 24 * 3600;
    private static String BLACKLIST;
    private static String sdCardPath = "";

    private final static boolean CRASH_ON_ERROR = false;
    private final static boolean CHECK_NFO = true;

    private static volatile boolean mIsImportInterrupted = false;

    public VideoStoreImportImpl(Context context) {
        mContext = context;
        // sdCardPath resolution needs a filesystem stat that can block for a long time on some
        // devices (seen causing main thread ANR on Android 15, issue 1860): defer it to
        // ensureSdCardPath() called from the background ImportWorker thread instead of doing it
        // here since this constructor runs on the main thread (VideoStoreImportService.onCreate).
        mCr = mContext.getContentResolver();
        mBlackList = Blacklist.getInstance(context);
        mMediaRetrieverServiceClient = new MediaRetrieverServiceClient(context);
        String [] blacklistCamDirs = mBlackList.getBlackListCamDirs();
        BLACKLIST = "";
        for (String blacklisted : mBlackList.getBlackListCamera())
            BLACKLIST += " AND _data NOT LIKE '%" + blacklisted + "%'";
        List<String> extPathList = ExtStorageManager.getExtStorageManager().getExtSdcards();
        extPathList.add(Environment.getExternalStorageDirectory().getPath());
        for (String extPath: extPathList)
            for (String blacklistedDir : blacklistCamDirs)
                BLACKLIST += " AND _data NOT LIKE '%" + extPath+blacklistedDir + "%'";
        if (log.isDebugEnabled()) log.debug("VideoStoreImportImpl: BLACKLIST {}", BLACKLIST);
    }

    public void destroy() {
        mMediaRetrieverServiceClient.unbindAndDestroy();
    }

    // resolves sdCardPath once, must be called from the background ImportWorker thread only
    // (getExternalFilesDir can block on filesystem canonicalization on some devices)
    private void ensureSdCardPath() {
        if (sdCardPath.isEmpty()) {
            File sdCardFile = mContext.getExternalFilesDir(null);
            // sdCardFile seen on sentry (3744020792) to be null on MIBOX4 android 9
            if (sdCardFile != null) {
                sdCardPath = sdCardFile.getPath();
            } else {
                // take a wild probable guess
                sdCardPath = "/storage/emulated/0";
            }
        }
    }

    public void doFullImport() {
        mIsImportInterrupted = false;
        int countStart = getLocalCount(mCr);
        if (log.isDebugEnabled()) log.debug("doFullImport: ImportState.VIDEO.setState {}", (countStart == 0 ? State.INITIAL_IMPORT : State.REGULAR_IMPORT));
        ImportState.VIDEO.setState(countStart == 0 ? State.INITIAL_IMPORT : State.REGULAR_IMPORT);

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            ensureSdCardPath();
            LocalReconciliationResult reconciliation;
            int copy;
            if (countStart == 0) {
                // Keep the paged importer for an empty database: every MediaStore row is new and
                // retaining the complete dataset for reconciliation would waste memory.
                reconciliation = new LocalReconciliationResult();
                copy = copyData(mCr, null);
            } else {
                ReconciliationSnapshot snapshot = loadMountedStorageSnapshot(
                        mCr, getMountedStorageLocations());
                if (snapshot.complete) {
                    reconciliation = reconcileStorageSnapshot(mCr, snapshot, mContext);
                    copy = copySnapshotData(mCr, snapshot, null);
                } else {
                    log.warn("doFullImport: reconciliation snapshot incomplete; using paged importer");
                    reconciliation = new LocalReconciliationResult();
                    copy = copyData(mCr, null);
                }
            }
            // External storage is available, proceed with your operation
            // delete everything that was not replaced, ! only if it is on primary local storage !
            String existingFiles = getRemoteIdList(mCr);
            int del = reconciliation.removed;
            updateVolumeHiddenStates(existingFiles);
            int countEnd = getLocalCount(mCr);
            log.info("full import +:" + copy + " ~:" + reconciliation.updated + " -:" + del
                    + " " + countStart + "=>" + countEnd);
            ImportState.VIDEO.setDeleting(false);
            // then trigger scan of new data
            doScan(mCr, mContext, mBlackList);
            // ...
        } else {
            // External storage is not available, handle this situation
            log.error("doFullImport: external storage (volume: 'external_primary') is not available");
        }
        ImportState.VIDEO.setDeleting(false);
        ImportState.VIDEO.setNumberOfFilesRemainingToDelete(0);
        ImportState.VIDEO.setRemainingCount(0);
        ImportState.VIDEO.setState(State.IDLE);
        if (log.isDebugEnabled()) log.debug("doFullImport: ImportState.VIDEO.setState(State.IDLE)");
    }

    public void doIncrementalImport() {
        mIsImportInterrupted = false;
        int countStart = getLocalCount(mCr);
        ImportState.VIDEO.setState(countStart == 0 ? State.INITIAL_IMPORT : State.REGULAR_IMPORT);
        if (log.isDebugEnabled()) log.debug("doIncrementalImport: ImportState.VIDEO.setState {}", (countStart == 0 ? State.INITIAL_IMPORT : State.REGULAR_IMPORT));

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            ensureSdCardPath();
            // Capture the old maximum before remapping; a remapped high id must not cause
            // copyData to skip lower new MediaStore ids from the same import pass.
            String maxLocal = getMaxId(mCr);
            ReconciliationSnapshot snapshot = loadMountedStorageSnapshot(
                    mCr, getMountedStorageLocations());
            LocalReconciliationResult reconciliation;
            int copy;
            // Copy only MediaStore ids newer than the maximum captured before reconciliation.
            if (snapshot.complete) {
                reconciliation = reconcileStorageSnapshot(mCr, snapshot, mContext);
                copy = copySnapshotData(mCr, snapshot, maxLocal);
            } else {
                log.warn("doIncrementalImport: reconciliation snapshot incomplete; using paged importer");
                reconciliation = new LocalReconciliationResult();
                copy = copyData(mCr, maxLocal);
            }
            String existingFiles = getRemoteIdList(mCr);
            int del = 0;
            updateVolumeHiddenStates(existingFiles);

            del = reconciliation.removed;
            int countEnd = getLocalCount(mCr);
            log.info("part import +:" + copy + " ~:" + reconciliation.updated + " -:" + del
                    + " " + countStart + "=>" + countEnd);
            ImportState.VIDEO.setDeleting(false);
            // then trigger scan of new data
            doScan(mCr, mContext, mBlackList);
        } else {
            // External storage is not available, handle this situation
            log.error("doIncrementalImport: external storage (volume: 'external_primary') is not available");
        }

        ImportState.VIDEO.setDeleting(false);
        ImportState.VIDEO.setNumberOfFilesRemainingToDelete(0);
        ImportState.VIDEO.setRemainingCount(0);
        ImportState.VIDEO.setState(State.IDLE);
        if (log.isDebugEnabled()) log.debug("doIncrementalImport: ImportState.VIDEO.setState(State.IDLE)");
    }

    static final class LocalReconciliationResult {
        int updated;
        int removed;

        void add(LocalReconciliationResult other) {
            updated += other.updated;
            removed += other.removed;
        }
    }

    private static final long MEDIASTORE_MTIME_TOLERANCE_SECONDS = 3;
    private static final String[] IMPORT_IDENTITY_PROJECTION = {
            BaseColumns._ID,
            MediaColumnsDATA,
            MediaColumns.DISPLAY_NAME,
            MediaColumns.SIZE,
            MediaColumns.DATE_MODIFIED
    };

    static final class StorageLocation {
        final String path;
        final Integer storageId;
        final boolean primary;

        StorageLocation(String path, Integer storageId, boolean primary) {
            this.path = path;
            this.storageId = storageId;
            this.primary = primary;
        }
    }

    private static class ImportIdentity {
        final long id;
        final String path;
        final String normalizedName;
        final long size;
        final long modified;

        ImportIdentity(long id, String path, String displayName, long size, long modified) {
            this.id = id;
            this.path = path;
            this.normalizedName = normalizeDisplayName(displayName, path);
            this.size = size;
            this.modified = modified;
        }

        boolean canMatch() {
            return !TextUtils.isEmpty(normalizedName) && size > 0 && modified > 0;
        }
    }

    private static final class MediaStoreIdentity extends ImportIdentity {
        final ContentValues values;
        final StorageLocation location;

        MediaStoreIdentity(long id, String path, String displayName, long size, long modified,
                ContentValues values, StorageLocation location) {
            super(id, path, displayName, size, modified);
            this.values = values;
            this.location = location;
        }
    }

    static final class ReconciliationSnapshot {
        final Map<Long, ImportIdentity> importedById = new HashMap<>();
        final Map<Long, StorageLocation> importedLocations = new HashMap<>();
        final Map<String, Long> importedIdsByPath = new HashMap<>();
        final Map<Long, MediaStoreIdentity> mediaStoreById = new HashMap<>();
        final Set<Long> importedIds = new HashSet<>();
        final Set<Long> mediaStoreIds = new HashSet<>();
        boolean complete = true;
    }

    private static final class IdentityKey {
        final String name;
        final long size;

        IdentityKey(ImportIdentity identity) {
            name = identity.normalizedName;
            size = identity.size;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof IdentityKey)) return false;
            IdentityKey other = (IdentityKey) object;
            return size == other.size && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + Long.valueOf(size).hashCode();
        }
    }

    private List<StorageLocation> getMountedStorageLocations() {
        List<StorageLocation> locations = new ArrayList<>();
        String primaryPath = Environment.getExternalStorageDirectory().getPath();
        if (isMountedReadable(Environment.getExternalStorageState())) {
            locations.add(new StorageLocation(primaryPath,
                    VolumeState.STORAGE_ID_PRIMARY_VOLUME, true));
        }

        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        Set<String> removablePaths = collectDiscoveredRemovableStoragePaths(primaryPath,
                storageManager.getExtSdcards(), storageManager.getExtUsbStorages(),
                storageManager.getExtOtherStorages());
        if (log.isDebugEnabled()) {
            log.debug("getMountedStorageLocations: discovered mounted removable paths {}",
                    removablePaths);
        }

        for (String path : removablePaths) {
            // ExtStorageManager only exposes mounted-readable volumes. Do not recheck through
            // its legacy IMountService reflection: hidden-API restrictions return MEDIA_REMOVED
            // on recent Android versions even while the volume is mounted.
            Integer storageId = storageManager.getStorageId(path);
            if (storageId == null && log.isDebugEnabled()) {
                log.debug("getMountedStorageLocations: no resolved storage id for {}; preserving the MediaStore/existing value",
                        path);
            }
            locations.add(new StorageLocation(path, storageId, false));
        }
        return locations;
    }

    static Set<String> collectDiscoveredRemovableStoragePaths(String primaryPath,
            List<String> sdCards, List<String> usbStorages, List<String> otherStorages) {
        Set<String> paths = new HashSet<>();
        if (sdCards != null) paths.addAll(sdCards);
        if (usbStorages != null) paths.addAll(usbStorages);
        if (otherStorages != null) paths.addAll(otherStorages);
        paths.remove(primaryPath);
        paths.remove(null);
        paths.remove("");
        return paths;
    }

    static ReconciliationSnapshot loadMountedStorageSnapshot(ContentResolver cr,
            List<StorageLocation> locations) {
        ReconciliationSnapshot snapshot = new ReconciliationSnapshot();
        resetVisibleStorageIds();
        if (cr == null || locations == null || locations.isEmpty()) {
            snapshot.complete = false;
            return snapshot;
        }

        for (StorageLocation location : locations) {
            if (mIsImportInterrupted) {
                snapshot.complete = false;
                break;
            }
            loadStorageSnapshot(cr, location, snapshot);
            if (!snapshot.complete) break;
        }
        return snapshot;
    }

    private static void loadStorageSnapshot(ContentResolver cr, StorageLocation location,
            ReconciliationSnapshot snapshot) {
        if (location == null || TextUtils.isEmpty(location.path)) return;
        String storagePrefix = location.path.endsWith("/")
                ? location.path : location.path + "/";
        try {
            loadImportedIdentityPages(cr, location, storagePrefix, snapshot);
            if (!snapshot.complete || mIsImportInterrupted) return;

            String[] projection = Build.VERSION.SDK_INT > Build.VERSION_CODES.O
                    ? FILES_PROJECTION_AP : FILES_PROJECTION_BP;
            String selection = (Build.VERSION.SDK_INT > Build.VERSION_CODES.O
                    ? NOT_NETWORKINDEXED_AP : NOT_NETWORKINDEXED_BP)
                    + " AND " + MediaColumnsDATA + " LIKE ?"
                    + " AND " + BaseColumns._ID + ">?";
            if (!TextUtils.isEmpty(BLACKLIST)) selection += BLACKLIST;
            loadMediaStoreIdentityPages(cr, location, storagePrefix, projection, selection,
                    snapshot);
        } catch (RuntimeException e) {
            snapshot.complete = false;
            log.error("loadStorageSnapshot: failed to read storage {}", location.path, e);
        }
    }

    /**
     * Loads imported identities with keyset pagination. Unlike OFFSET pagination, insertions or
     * deletions below the last observed id cannot shift the boundary and skip or duplicate later
     * rows.
     */
    private static void loadImportedIdentityPages(ContentResolver cr, StorageLocation location,
            String storagePrefix, ReconciliationSnapshot snapshot) {
        long lastSeenId = -1;
        while (!mIsImportInterrupted) {
            Cursor imported = null;
            int rowsRead = 0;
            long pageLastId = lastSeenId;
            try {
                String selection = MediaColumnsDATA + " LIKE ? AND " + BaseColumns._ID + ">?";
                String[] selectionArgs = {
                        storagePrefix + "%", String.valueOf(lastSeenId)
                };
                imported = queryIdentityPage(cr, VideoStoreInternal.FILES_IMPORT,
                        IMPORT_IDENTITY_PROJECTION, selection, selectionArgs);
                if (imported == null) {
                    snapshot.complete = false;
                    return;
                }
                // Some providers have been observed to ignore QUERY_ARG_LIMIT. Enforce the
                // boundary here as well so one CursorWindow never grows into an unbounded pass.
                while (rowsRead < WINDOW_SIZE && imported.moveToNext()
                        && !mIsImportInterrupted) {
                    rowsRead++;
                    ImportIdentity identity = readImportIdentity(imported);
                    pageLastId = identity.id;
                    if (!isStoragePath(storagePrefix, identity.path)) continue;
                    snapshot.importedById.put(identity.id, identity);
                    snapshot.importedLocations.put(identity.id, location);
                    snapshot.importedIdsByPath.put(identity.path, identity.id);
                    snapshot.importedIds.add(identity.id);
                }
            } finally {
                if (imported != null) imported.close();
            }
            if (mIsImportInterrupted) {
                snapshot.complete = false;
                return;
            }
            if (rowsRead < WINDOW_SIZE) return;
            if (pageLastId <= lastSeenId) {
                snapshot.complete = false;
                log.error("loadImportedIdentityPages: non-advancing page for storage {} after id {}",
                        location.path, lastSeenId);
                return;
            }
            lastSeenId = pageLastId;
        }
        snapshot.complete = false;
    }

    private static void loadMediaStoreIdentityPages(ContentResolver cr, StorageLocation location,
            String storagePrefix, String[] projection, String selection,
            ReconciliationSnapshot snapshot) {
        long lastSeenId = -1;
        while (!mIsImportInterrupted) {
            Cursor mediaStore = null;
            int rowsRead = 0;
            long pageLastId = lastSeenId;
            try {
                String[] selectionArgs = {
                        storagePrefix + "%", String.valueOf(lastSeenId)
                };
                mediaStore = CustomCursor.wrap(queryIdentityPage(cr,
                        MediaStore.Files.getContentUri("external"), projection, selection,
                        selectionArgs));
                if (mediaStore == null) {
                    snapshot.complete = false;
                    return;
                }
                // Keep the client-side cap because some MediaStore implementations ignore the
                // requested limit even though the selection and ordering are honored.
                while (rowsRead < WINDOW_SIZE && mediaStore.moveToNext()
                        && !mIsImportInterrupted) {
                    rowsRead++;
                    MediaStoreIdentity identity = readMediaStoreIdentity(mediaStore, location);
                    pageLastId = identity.id;
                    if (!isStoragePath(storagePrefix, identity.path)) continue;
                    snapshot.mediaStoreById.put(identity.id, identity);
                    snapshot.mediaStoreIds.add(identity.id);
                    recordVisibleStorageId(location.storageId);
                }
            } finally {
                if (mediaStore != null) mediaStore.close();
            }
            if (mIsImportInterrupted) {
                snapshot.complete = false;
                return;
            }
            if (rowsRead < WINDOW_SIZE) return;
            if (pageLastId <= lastSeenId) {
                snapshot.complete = false;
                log.error("loadMediaStoreIdentityPages: non-advancing page for storage {} after id {}",
                        location.path, lastSeenId);
                return;
            }
            lastSeenId = pageLastId;
        }
        snapshot.complete = false;
    }

    private static Cursor queryIdentityPage(ContentResolver cr, Uri uri, String[] projection,
            String selection, String[] selectionArgs) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            Bundle queryArgs = new Bundle();
            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
            queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
            queryArgs.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    new String[] { BaseColumns._ID });
            queryArgs.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, WINDOW_SIZE);
            return cr.query(uri, projection, queryArgs, null);
        }
        return cr.query(uri, projection, selection, selectionArgs,
                BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE);
    }

    /**
     * Applies stable-id path updates and conservative changed-id remaps from one shared snapshot.
     * Candidate matching spans every mounted location, allowing USB-to-USB moves when filename,
     * size and modification time identify a unique source and destination.
     */
    static LocalReconciliationResult reconcileStorageSnapshot(ContentResolver cr,
            ReconciliationSnapshot snapshot) {
        return reconcileStorageSnapshot(cr, snapshot, null);
    }

    static LocalReconciliationResult reconcileStorageSnapshot(ContentResolver cr,
            ReconciliationSnapshot snapshot, Context context) {
        LocalReconciliationResult result = new LocalReconciliationResult();
        if (cr == null || snapshot == null || !snapshot.complete || mIsImportInterrupted) {
            return result;
        }

        Set<Long> remappedSourceIds = new HashSet<>();
        reconcileStableIds(cr, snapshot, result);
        reconcileChangedIds(cr, snapshot, result, remappedSourceIds);
        removeMissingPrimaryRows(cr, snapshot, result, remappedSourceIds, context);
        return result;
    }

    private static void reconcileStableIds(ContentResolver cr, ReconciliationSnapshot snapshot,
            LocalReconciliationResult result) {
        for (MediaStoreIdentity destination : snapshot.mediaStoreById.values()) {
            if (mIsImportInterrupted) return;
            ImportIdentity source = snapshot.importedById.get(destination.id);
            if (source == null || TextUtils.equals(source.path, destination.path)) continue;

            Long conflictingId = snapshot.importedIdsByPath.get(destination.path);
            if (conflictingId != null && conflictingId.longValue() != destination.id) {
                log.error("reconcileStableIds: refusing path update for id {} from {} to {}; path belongs to id {}",
                        destination.id, source.path, destination.path, conflictingId);
                continue;
            }

            ContentValues importedValues = new ContentValues(destination.values);
            ContentValues fileValues = new ContentValues(importedValues);
            fileValues.put(VideoStoreInternal.KEY_IMPORT_RECONCILE_PATH, true);
            ArrayList<ContentProviderOperation> operations = new ArrayList<>(2);
            operations.add(ContentProviderOperation.newUpdate(VideoStoreInternal.FILES_IMPORT)
                    .withSelection(BaseColumns._ID + "=? AND " + MediaColumnsDATA + "=?",
                            new String[] { String.valueOf(source.id), source.path })
                    .withValues(importedValues)
                    .withExpectedCount(1)
                    .build());
            operations.add(ContentProviderOperation.newUpdate(VideoStoreInternal.FILES)
                    .withSelection("remote_id=? AND " + MediaColumnsDATA + "=?",
                            new String[] { String.valueOf(source.id), source.path })
                    .withValues(fileValues)
                    .withExpectedCount(1)
                    .build());
            try {
                cr.applyBatch(VideoStore.AUTHORITY, operations);
                snapshot.importedIdsByPath.remove(source.path);
                snapshot.importedIdsByPath.put(destination.path, destination.id);
                result.updated++;
                log.info("reconcileStableIds: updated id {} path {} -> {}", destination.id,
                        source.path, destination.path);
            } catch (RemoteException | OperationApplicationException | RuntimeException e) {
                log.error("reconcileStableIds: failed id {} path {} -> {}", destination.id,
                        source.path, destination.path, e);
            }
        }
    }

    private static void reconcileChangedIds(ContentResolver cr, ReconciliationSnapshot snapshot,
            LocalReconciliationResult result, Set<Long> remappedSourceIds) {
        Map<IdentityKey, List<ImportIdentity>> oldBuckets = new HashMap<>();
        Map<IdentityKey, List<MediaStoreIdentity>> newBuckets = new HashMap<>();
        for (ImportIdentity identity : snapshot.importedById.values()) {
            if (!snapshot.mediaStoreIds.contains(identity.id) && identity.canMatch()
                    && !new File(identity.path).exists()) {
                addToBucket(oldBuckets, new IdentityKey(identity), identity);
            }
        }
        for (MediaStoreIdentity identity : snapshot.mediaStoreById.values()) {
            if (identity.canMatch()) {
                addToBucket(newBuckets, new IdentityKey(identity), identity);
            }
        }

        Set<Long> remappedDestinationIds = new HashSet<>();
        for (Map.Entry<IdentityKey, List<ImportIdentity>> entry : oldBuckets.entrySet()) {
            if (mIsImportInterrupted) return;
            List<MediaStoreIdentity> destinations = newBuckets.get(entry.getKey());
            if (destinations == null) continue;
            for (ImportIdentity source : entry.getValue()) {
                MediaStoreIdentity destination = uniqueDestination(source, entry.getValue(),
                        destinations);
                if (destination == null || !remappedDestinationIds.add(destination.id)) continue;

                ImportIdentity importedDestination = snapshot.importedById.get(destination.id);
                boolean repairDuplicate = importedDestination != null;
                if (repairDuplicate && (!TextUtils.equals(importedDestination.path, destination.path)
                        || !isDisposableDestination(cr, destination.id, destination.path))) {
                    remappedDestinationIds.remove(destination.id);
                    log.warn("reconcileChangedIds: destination id {} already owns state; skipping {} -> {}",
                            destination.id, source.path, destination.path);
                    continue;
                }
                try {
                    cr.applyBatch(VideoStore.AUTHORITY, buildChangedIdRemapOperations(source,
                            destination, repairDuplicate));
                    remappedSourceIds.add(source.id);
                    snapshot.importedIds.remove(source.id);
                    snapshot.importedIds.add(destination.id);
                    result.updated++;
                    log.info("reconcileChangedIds: remapped id {} -> {} path {} -> {}{}",
                            source.id, destination.id, source.path, destination.path,
                            repairDuplicate ? " (repaired duplicate)" : "");
                } catch (RemoteException | OperationApplicationException | RuntimeException e) {
                    remappedDestinationIds.remove(destination.id);
                    log.error("reconcileChangedIds: failed id {} -> {} path {} -> {}",
                            source.id, destination.id, source.path, destination.path, e);
                }
            }
        }
    }

    private static void removeMissingPrimaryRows(ContentResolver cr,
            ReconciliationSnapshot snapshot, LocalReconciliationResult result,
            Set<Long> remappedSourceIds, Context context) {
        if (mIsImportInterrupted) return;
        List<ImportIdentity> missingList = new ArrayList<>();
        for (ImportIdentity identity : snapshot.importedById.values()) {
            StorageLocation location = snapshot.importedLocations.get(identity.id);
            if (location == null || !location.primary || snapshot.mediaStoreIds.contains(identity.id)
                    || remappedSourceIds.contains(identity.id) || new File(identity.path).exists()) {
                continue;
            }
            missingList.add(identity);
        }

        int totalMissing = missingList.size();
        if (totalMissing == 0) return;

        int remaining = totalMissing;
        ImportState.VIDEO.setNumberOfFilesRemainingToDelete(remaining);
        ImportState.VIDEO.setDeleting(true);
        if (context instanceof VideoStoreImportService) {
            ((VideoStoreImportService) context).updateDeleteNotification(remaining, missingList.get(0).path);
        }

        final int BATCH_SIZE = 50;
        try {
            for (int i = 0; i < totalMissing; i += BATCH_SIZE) {
                if (mIsImportInterrupted) break;
                int end = Math.min(i + BATCH_SIZE, totalMissing);
                List<ImportIdentity> candidates = missingList.subList(i, end);
                List<ImportIdentity> batch = new ArrayList<>(candidates.size());
                for (ImportIdentity identity : candidates) {
                    if (!new File(identity.path).exists()) {
                        batch.add(identity);
                    } else {
                        // It reappeared after the snapshot. Retain its database row.
                        remaining--;
                    }
                }

                if (!batch.isEmpty()) {
                    StringBuilder selection = new StringBuilder(BaseColumns._ID).append(" IN (");
                    String[] selectionArgs = new String[batch.size()];
                    for (int j = 0; j < batch.size(); j++) {
                        if (j > 0) selection.append(',');
                        selection.append('?');
                        selectionArgs[j] = String.valueOf(batch.get(j).id);
                    }
                    selection.append(')');
                    int removed = cr.delete(VideoStoreInternal.FILES_IMPORT, selection.toString(), selectionArgs);
                    if (removed == batch.size()) {
                        for (ImportIdentity identity : batch) {
                            snapshot.importedIds.remove(identity.id);
                        }
                    }
                    result.removed += removed;
                    remaining -= removed;
                    log.info("removeMissingPrimaryRows: removed {} inaccessible rows (remaining: {})", removed, remaining);
                }
                ImportState.VIDEO.setNumberOfFilesRemainingToDelete(remaining);
                if (context instanceof VideoStoreImportService) {
                    String path = batch.isEmpty()
                            ? candidates.get(candidates.size() - 1).path
                            : batch.get(batch.size() - 1).path;
                    ((VideoStoreImportService) context).updateDeleteNotification(remaining, path);
                }
            }
        } finally {
            ImportState.VIDEO.setDeleting(false);
            ImportState.VIDEO.setNumberOfFilesRemainingToDelete(0);
        }
    }

    static LocalReconciliationResult reconcileChangedMediaStoreIds(ContentResolver cr,
            String storagePath, Integer storageId) {
        StorageLocation location = new StorageLocation(storagePath, storageId, false);
        ReconciliationSnapshot snapshot = loadMountedStorageSnapshot(cr,
                java.util.Collections.singletonList(location));
        return reconcileStorageSnapshot(cr, snapshot);
    }

    private static ImportIdentity readImportIdentity(Cursor cursor) {
        return new ImportIdentity(
                cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(MediaColumnsDATA)),
                getCursorString(cursor, MediaColumns.DISPLAY_NAME),
                getCursorLong(cursor, MediaColumns.SIZE),
                getCursorLong(cursor, MediaColumns.DATE_MODIFIED));
    }

    private static String getCursorString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static long getCursorLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getLong(index);
    }

    private static MediaStoreIdentity readMediaStoreIdentity(Cursor cursor,
            StorageLocation location) {
        ContentValues values = new ContentValues(cursor.getColumnCount() + 2);
        DatabaseUtils.cursorRowToContentValues(cursor, values);
        long id = values.getAsLong(BaseColumns._ID);
        String path = values.getAsString(MediaColumnsDATA);
        String displayName = values.getAsString(MediaColumns.DISPLAY_NAME);
        long size = getLong(values, MediaColumns.SIZE);
        long modified = getLong(values, MediaColumns.DATE_MODIFIED);
        values.remove(BaseColumns._ID);
        if (location.storageId != null) {
            values.put(VideoStore.Files.FileColumns.STORAGE_ID, location.storageId);
        }
        values.put("volume_hidden", 0);
        return new MediaStoreIdentity(id, path, displayName, size, modified, values, location);
    }

    private static long getLong(ContentValues values, String key) {
        Long value = values.getAsLong(key);
        return value == null ? 0 : value;
    }

    private static String normalizeDisplayName(String displayName, String path) {
        String name = displayName;
        if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(path)) name = new File(path).getName();
        return TextUtils.isEmpty(name) ? null : name.trim().toLowerCase(Locale.ROOT);
    }

    private static <T> void addToBucket(Map<IdentityKey, List<T>> buckets,
            IdentityKey key, T value) {
        List<T> values = buckets.get(key);
        if (values == null) {
            values = new ArrayList<>();
            buckets.put(key, values);
        }
        values.add(value);
    }

    private static MediaStoreIdentity uniqueDestination(ImportIdentity source,
            List<ImportIdentity> sources, List<MediaStoreIdentity> destinations) {
        MediaStoreIdentity match = null;
        for (MediaStoreIdentity destination : destinations) {
            if (!modifiedTimesMatch(source.modified, destination.modified)) continue;
            if (match != null) return null;
            match = destination;
        }
        if (match == null) return null;

        int sourceMatches = 0;
        for (ImportIdentity candidate : sources) {
            if (modifiedTimesMatch(candidate.modified, match.modified)) sourceMatches++;
        }
        return sourceMatches == 1 ? match : null;
    }

    private static boolean modifiedTimesMatch(long first, long second) {
        return first > 0 && second > 0
                && Math.abs(first - second) <= MEDIASTORE_MTIME_TOLERANCE_SECONDS;
    }

    private static final String DISPOSABLE_DESTINATION_WHERE =
            "_id=? AND remote_id=? AND _data=? "
            + "AND ifnull(ArchosMediaScraper_id,0)<=0 "
            + "AND ifnull(ArchosMediaScraper_type,0)<=0 "
            + "AND ifnull(bookmark,0)=0 AND ifnull(Archos_bookmark,0)=0 "
            + "AND ifnull(Archos_lastTimePlayed,0)=0 "
            + "AND ifnull(Archos_favorite_track,0)=0 "
            + "AND ifnull(Archos_traktSeen,0)=0 AND ifnull(Archos_traktLibrary,0)=0 "
            + "AND ifnull(Archos_traktResume,0)=0 AND ifnull(Archos_hiddenByUser,0)=0 "
            + "AND ifnull(Archos_title,'')='' "
            + "AND NOT EXISTS (SELECT 1 FROM movie WHERE video_id=files.remote_id) "
            + "AND NOT EXISTS (SELECT 1 FROM episode WHERE video_id=files.remote_id)";

    private static boolean isDisposableDestination(ContentResolver cr, long id, String path) {
        Cursor cursor = null;
        try {
            cursor = cr.query(VideoStoreInternal.FILES, new String[] { BaseColumns._ID },
                    DISPOSABLE_DESTINATION_WHERE,
                    new String[] { String.valueOf(id), String.valueOf(id), path }, null);
            return cursor != null && cursor.moveToFirst() && cursor.getCount() == 1;
        } catch (RuntimeException e) {
            log.error("isDisposableDestination: failed for id {} path {}", id, path, e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    static ArrayList<ContentProviderOperation> buildChangedIdRemapOperations(
            ImportIdentity source, MediaStoreIdentity destination, boolean repairDuplicate) {
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();

        if (repairDuplicate) {
            String destinationGuard = BaseColumns._ID + "=? AND " + MediaColumnsDATA + "=? "
                    + "AND EXISTS (SELECT 1 FROM files WHERE "
                    + DISPOSABLE_DESTINATION_WHERE + ")";
            operations.add(ContentProviderOperation.newDelete(VideoStoreInternal.FILES_IMPORT)
                    .withSelection(destinationGuard, new String[] {
                            String.valueOf(destination.id), destination.path,
                            String.valueOf(destination.id), String.valueOf(destination.id),
                            destination.path })
                    .withExpectedCount(1)
                    .build());
        }

        ContentValues fileValues = new ContentValues(destination.values);
        fileValues.put(BaseColumns._ID, destination.id);
        fileValues.put("remote_id", destination.id);
        fileValues.put(VideoStoreInternal.KEY_IMPORT_RECONCILE_ID, true);
        operations.add(ContentProviderOperation.newUpdate(VideoStoreInternal.FILES)
                .withSelection(BaseColumns._ID + "=? AND remote_id=? AND "
                                + MediaColumnsDATA + "=?",
                        new String[] { String.valueOf(source.id), String.valueOf(source.id),
                                source.path })
                .withValues(fileValues)
                .withExpectedCount(1)
                .build());

        ContentValues thumbnailValues = new ContentValues();
        thumbnailValues.put("video_id", destination.id);
        operations.add(ContentProviderOperation.newUpdate(
                        VideoStoreInternal.getRawUri(VideoOpenHelper.VIDEOTHUMBNAIL_TABLE_NAME))
                .withSelection("video_id=?", new String[] { String.valueOf(source.id) })
                .withValues(thumbnailValues)
                .build());

        ContentValues importedValues = new ContentValues(destination.values);
        importedValues.put(BaseColumns._ID, destination.id);
        operations.add(ContentProviderOperation.newUpdate(VideoStoreInternal.FILES_IMPORT)
                .withSelection(BaseColumns._ID + "=? AND " + MediaColumnsDATA + "=?",
                        new String[] { String.valueOf(source.id), source.path })
                .withValues(importedValues)
                .withExpectedCount(1)
                .build());
        return operations;
    }

    /**
     * Reconciles path changes on one mounted storage volume without replacing file rows. MediaStore
     * keeps an item's id for an in-volume move, so updating both tables in place preserves scraper
     * metadata and playback state. Missing-row deletion is enabled only for primary storage.
     */
    static LocalReconciliationResult reconcilePrimaryStorageRows(ContentResolver cr,
            String primaryPath) {
        return reconcileStorageRows(cr, primaryPath, VolumeState.STORAGE_ID_PRIMARY_VOLUME, true);
    }

    static LocalReconciliationResult reconcileStorageRows(ContentResolver cr, String storagePath,
            Integer storageId, boolean removeMissingRows) {
        StorageLocation location = new StorageLocation(storagePath, storageId, removeMissingRows);
        ReconciliationSnapshot snapshot = loadMountedStorageSnapshot(cr,
                java.util.Collections.singletonList(location));
        return reconcileStorageSnapshot(cr, snapshot);
    }

    static boolean isStoragePath(String storagePrefix, String path) {
        return path != null && path.startsWith(storagePrefix);
    }

    private static final String[] ID_DATA_PROJ = new String[] {
            BaseColumns._ID,
            MediaColumnsDATA,
            VideoColumns.ARCHOS_MEDIA_SCRAPER_ID
    };
    private static final String UPDATE_WHERE = "remote_id=?";
    /** scans every file in cursor and update database, also closes cursor */
    private void handleScanCursor(Cursor c, ContentResolver cr, Context context, Blacklist blacklist) {
        // for some reasons doScan passes a a cursor with size > WINDOW_SIZE
        // thus only process WINDOW_SIZE entries
        int remaining = c.getCount();
        if (c == null || remaining == 0) {
            if (c != null) c.close();
            if (log.isDebugEnabled()) log.debug("handleScanCursor: no media to scan");
            return;
        }

        int scanned = 0;
        int scraped = 0;

        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        long time = System.currentTimeMillis() / 1000L;
        String timeString = String.valueOf(time);

        NfoParser.ImportContext importContext = new NfoParser.ImportContext();
        // Still getting SQLiteBlobTooBigException due perhaps to large blobs in the database for some reasons
        try {
            int count = 0;
            while (c.moveToNext() && count < WINDOW_SIZE && !mIsImportInterrupted) {
                count++;
                ImportState.VIDEO.setRemainingCount(remaining--);
                if (log.isDebugEnabled()) log.debug("handleScanCursor: ImportState.VIDEO.setRemainingCount {} count {}", remaining, count);
                String id;
                String path;
                int scraperID;
                try {
                    id = c.getString(0);
                    path = c.getString(1);
                    if (path.startsWith("/"))
                        path = "file://" + path;
                    scraperID = c.getInt(2);
                } catch (IllegalStateException ignored) {
                    log.error("handleScanCursor: IllegalStateException caught, content deleted while scanning?");
                    //we silently ignore empty lines - it means content has been deleted while scanning
                    continue;
                }
                if (context instanceof VideoStoreImportService) {
                    ((VideoStoreImportService) context).updateScanNotification(remaining, path);
                }
                Job job = new Job(path, id, blacklist);
                if (log.isDebugEnabled()) log.debug("handleScanCursor: scanning {}", job.mPath);
                // update property with current file
                ContentValues cv = null;
                try {
                    cv = fromRetrieverService(job, timeString);
                } catch (InterruptedException e) {
                    log.error("handleScanCursor: InterruptedException caught");
                    // won't happen but stopping as soon as we can would be desired
                    if (CRASH_ON_ERROR) throw new RuntimeException(e);
                    break;
                } catch (MediaRetrieverServiceClient.ServiceManagementException e) {
                    if (mIsImportInterrupted) {
                        // Expected: MediaRetrieverService was stopped by lifecycle onStop while
                        // import was still running. interruptImport() was called; we break here.
                        log.warn("handleScanCursor: MediaRetrieverServiceClient.ServiceManagementException caught during teardown");
                    } else {
                        // Unexpected: retriever failure while import should be active.
                        log.error("handleScanCursor: MediaRetrieverServiceClient.ServiceManagementException caught", e);
                    }
                    if (CRASH_ON_ERROR) throw new RuntimeException(e);
                    break;
                }
                // set the scan_state correctly so that it is not picked up again
                // using ContentProviderOperation so updates are done as single transaction but at the applyBatch
                operations.add(
                        ContentProviderOperation.newUpdate(VideoStoreInternal.FILES)
                                .withSelection(UPDATE_WHERE, new String[]{job.mId})
                                .withValues(cv)
                                .build()
                );
                scanned++;
                if (CHECK_NFO) {
                    // .nfo auto-parsing
                    if (job.mRetrieve && scraperID <= 0) {
                        Uri videoFile = job.mPath;
                        if (videoFile != null) {
                            if (log.isDebugEnabled()) log.debug("handleScanCursor: searching .nfo for {}", videoFile);
                            NfoParser.NfoFile nfo = NfoParser.determineNfoFile(videoFile);
                            if (nfo != null && nfo.hasNfo()) {
                                if (log.isDebugEnabled()) log.debug("handleScanCursor: .nfo found for {} : {}", videoFile, nfo.videoNfo);
                                BaseTags tagForFile = NfoParser.getTagForFile(nfo, context, importContext);
                                if (tagForFile != null) {
                                    if (log.isDebugEnabled()) log.debug("handleScanCursor: .nfo contains valid BaseTags for {}", videoFile);
                                    long videoId = parseLong(job.mId, -1);
                                    if (videoId > 0) {
                                        tagForFile.save(context, videoId);
                                        if (log.isDebugEnabled()) log.debug("handleScanCursor: BaseTags saved for {}", videoFile);
                                        scraped++;
                                    }
                                }
                            }
                        }
                    }
                }
                if (job.mMediaType == FileColumns.MEDIA_TYPE_VIDEO) {
                    /* Process the FileName for more information */
                    ContentValues cvExtra = VideoNameProcessor.extractValuesFromPath(path);
                    operations.add(
                            ContentProviderOperation.newUpdate(VideoStoreInternal.FILES)
                                    .withSelection(UPDATE_WHERE, new String[]{job.mId})
                                    .withValues(cvExtra)
                                    .build()
                    );
                }
            }
        } catch (Exception e) {
            log.error("handleScanCursor: exception while moving to next cursor row!", e);
            if (CRASH_ON_ERROR) throw new RuntimeException(e);
        } finally {
            try {
                cr.applyBatch(VideoStore.AUTHORITY, operations);
            } catch (RemoteException | OperationApplicationException e1) {
                log.error("handleScanCursor: RemoteException or OperationApplicationException applying batch", e1);
                if (CRASH_ON_ERROR) throw new RuntimeException(e1);
            }
        }
        if (c != null) c.close();
        log.info("handleScanCursor: media scanned:" + scanned + " nfo-scraped:" + scraped);
        if (scraped > 0)
            TraktService.onNewVideo(context); // done once for the full import
        // TODO MARC do an else with sync!!!
    }

    private static class Job {
        @SuppressWarnings("deprecation") // FileColumns.MEDIA_TYPE_PLAYLIST
        public Job(String path, String id, Blacklist blacklist) {
            mPath = Uri.parse(path);
            mId = id;
            mMft = ArchosMediaFile.getFileType(path);
            // default mime type / media type
            int mediaType = FileColumns.MEDIA_TYPE_NONE;
            String mimeType = "application/octet-stream";
            boolean retrieve = false;
            if (mMft != null) {
                mimeType = mMft.mimeType;
                if (!isNoMediaPath(path) && !blacklist.isBlacklistedManual(mPath)) {
                //if (!isNoMediaPath(path) && !blacklist.isBlacklisted(mPath)) {
                    if (ArchosMediaFile.isAudioFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_AUDIO;
                    } else if (ArchosMediaFile.isVideoFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_VIDEO;
                        retrieve = true;
                    }  else if (ArchosMediaFile.isImageFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_IMAGE;
                    } else if (ArchosMediaFile.isPlayListFileType(mMft.fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_PLAYLIST;
                    }
                }
            }
            mMimeType = mimeType;
            mMediaType = mediaType;
            mRetrieve = retrieve;
        }

        public final Uri mPath;
        public final String mId;
        public final MediaFileType mMft;
        public final int mMediaType;
        public final boolean mRetrieve;
        public final String mMimeType;
    }

    /** removes file(s) defined by Uri */
    public void doRemove(Uri what) {
        mIsImportInterrupted = false;
        if (what == null || !"file".equals(what.getScheme()))
            return;
        String path = what.getPath();
        File f = new File(path);
        String where = WHERE_PATH;
        if (f.isFile())
            where = WHERE_FILE;
        if (log.isDebugEnabled()) log.debug("doRemove: Removing file(s): {}", path);
        int deleted = mCr.delete(VideoStoreInternal.FILES_IMPORT, where, new String[]{path});
        log.info("doRemove: removed:" + deleted);
    }

    private static String WHERE_PATH = "_data LIKE ?||'/%'";
    private static String WHERE_FILE = "_data = ?";
    /** executes metadata scan of files defined by Uri */
    public void doScan(Uri what) {
        // TODO MARC only works with local storage does not work with FileEditor editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(what, null);
        // TODO MARC this makes fail the associate sub on remote storage
        mIsImportInterrupted = false;
        if (log.isDebugEnabled()) log.debug("doScan: Scanning Metadata single uri: {}", what);
        if (what == null) return;
        String path = what.getPath();

        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        File f = new File(path);
        if (!f.exists()) {
            log.info("Not scanning " + f + ", it does not exist.");
            return;
        }
        String where = WHERE_PATH;
        if (f.isFile())
            where = WHERE_FILE;
        if (log.isDebugEnabled()) log.debug("doScan: Scanning Metadata: {}", path);
        //initNoMedia(mCr);
        Cursor c = mCr.query(VideoStoreInternal.FILES, ID_DATA_PROJ, where, new String[]{ path }, null);
        handleScanCursor(c, mCr, mContext, mBlackList);
    }

    private static final String SELF_NEEDS_SCAN = "scan_state < date_modified";
    private static final String DIRECTORY_NEEDS_SCAN = "scan_state < (select date_modified from files as files_parent where files_parent._id = files.parent)";
    private static final String WHERE_UNSCANNED = "scan_state >= 0 AND ("
            + SELF_NEEDS_SCAN
            + " OR "
            + DIRECTORY_NEEDS_SCAN
            + ") AND _id < " + VideoOpenHelper.SCANNED_ID_OFFSET;
    /** executes metadata scan of every unscanned file */
    private void doScan(ContentResolver cr, Context context, Blacklist blacklist) {
        if (log.isDebugEnabled()) log.debug("doScan: Scanning Metadata all unscanned files");
        mIsImportInterrupted = false;
        Cursor c = null;
        int cursorCount = 0;
        while (!mIsImportInterrupted) {
            try {
                // for some reasons this does return a cursor with size > WINDOW_SIZE
                // thus only process WINDOW_SIZE entries in handleScanCursor
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) { // API>30 requires bundle to LIMIT
                    final Bundle bundle = new Bundle();
                    bundle.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, WHERE_UNSCANNED);
                    bundle.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, null);
                    bundle.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, new String[]{BaseColumns._ID});
                    bundle.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);
                    bundle.putInt(ContentResolver.QUERY_ARG_LIMIT, WINDOW_SIZE);
                    bundle.putInt(ContentResolver.QUERY_ARG_OFFSET, 0);
                    if (log.isDebugEnabled()) log.debug("doScan: >Q new batch fetching cursor with bundle={}", bundle.toString());
                    c = cr.query(VideoStoreInternal.FILES, ID_DATA_PROJ, bundle, null);
                } else {
                    c = cr.query(VideoStoreInternal.FILES, ID_DATA_PROJ,
                            WHERE_UNSCANNED, null, BaseColumns._ID + " ASC LIMIT " + WINDOW_SIZE);
                }
                if (c != null) cursorCount = c.getCount();
                else cursorCount = 0;
                if (log.isDebugEnabled()) log.debug("doScan: new batch fetching window={} 0<= entries <={}, new batch cursor has size {}", WINDOW_SIZE, WINDOW_SIZE, cursorCount);
                handleScanCursor(c, cr, context, blacklist);
                if (cursorCount < WINDOW_SIZE) { // avoid infinite loop: trusts that handleScanCursor processes all entries
                    if (log.isDebugEnabled()) log.debug("doScan: no more data after handleScanCursor, c.getCount()={}<WINDOW_SIZE={} exit loop", cursorCount, WINDOW_SIZE);
                    break;
                }
            } catch (SQLException | IllegalStateException e) {
                log.error("SQLException or IllegalStateException",e);
                if (CRASH_ON_ERROR) throw new RuntimeException(e);
                break;
            } finally {
                if (c != null) c.close();
            }
        }
        if (c != null) c.close();
    }

    /** get MediaMetadata object or null for a path */
    private MediaMetadata getMetadata(String path) throws RemoteException, InterruptedException, MediaRetrieverServiceClient.ServiceManagementException {
        if(path.startsWith("file://")) {
            path = path.substring("file://".length()); //we need to remove "file://"
        }
        return mMediaRetrieverServiceClient.getMetadata(path);
    }

    /** creates ContentValues via MediaRetrieverService, can't be null */
    private ContentValues fromRetrieverService(Job job, String timeString) throws InterruptedException, MediaRetrieverServiceClient.ServiceManagementException {
        if (log.isDebugEnabled()) log.debug("fromRetrieverService: Scanning metadata of: {}", job.mPath);
        ContentValues cv = new ContentValues();
        String path = job.mPath.toString();
        // tell mediaprovider that this update originates from here.
        cv.put(VideoStoreInternal.KEY_SCANNER, "1");
        // also put the path here so MediaProvider knows which file it is
        cv.put(MediaColumnsDATA, path);
        cv.put(BaseColumns._ID, job.mId);

        String defaultTitle = getDefaultTitle(path);
        cv.put(FileColumns.TITLE, defaultTitle);
        cv.put(MediaColumns.MIME_TYPE, job.mMimeType);
        cv.put(FileColumns.MEDIA_TYPE, String.valueOf(job.mMediaType));
        cv.put(VideoStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE, timeString);

        // try to get metadata if file is a mediafile
        MediaMetadata metadata = null;
        if (job.mRetrieve) {
            try {
                metadata = getMetadata(path);
            } catch (RemoteException e) {
                log.warn("Blacklisting file because it killed metadata service:{}", path);
                cv.put(VideoStoreInternal.FILES_EXTRA_COLUMN_SCAN_STATE, String.valueOf(VideoStoreInternal.SCAN_STATE_SCAN_FAILED));
                return cv;
            }
            if (metadata == null) {
                // file didn't kill the service but still failed to give metadata
                log.info("Failed to get metadata for file:" + path);
                return cv;
            }
        }

        // if we don't need to scan further also end here.
        if (!job.mRetrieve)
            return cv;

        if (log.isDebugEnabled()) log.debug("fromRetrieverService: Scanning metadata of: {}", path);
        switch (job.mMediaType) {
            case FileColumns.MEDIA_TYPE_VIDEO:
                extract(cv, metadata, VideoColumns.ARCHOS_ENCODING_PROFILE, IMediaMetadataRetriever.METADATA_KEY_ENCODING_PROFILE, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_FRAMES_PER_THOUSAND_SECONDS, IMediaMetadataRetriever.METADATA_KEY_FRAMES_PER_THOUSAND_SECONDS, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_NUMBER_OF_AUDIO_TRACKS, IMediaMetadataRetriever.METADATA_KEY_NB_AUDIO_TRACK, "-1");
                extract(cv, metadata, VideoColumns.ARCHOS_NUMBER_OF_SUBTITLE_TRACKS, IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK, "-1");
                extract(cv, metadata, VideoColumns.ARCHOS_VIDEO_BITRATE, IMediaMetadataRetriever.METADATA_KEY_VIDEO_BITRATE, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_VIDEO_FOURCC_CODEC, IMediaMetadataRetriever.METADATA_KEY_VIDEO_FOURCC_CODEC, "0");
                extract(cv, metadata, MediaColumns.HEIGHT, IMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "0");
                extract(cv, metadata, MediaColumns.WIDTH, IMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "0");
                extract(cv, metadata, VideoColumns.DURATION, IMediaMetadataRetriever.METADATA_KEY_DURATION, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_SAMPLERATE, IMediaMetadataRetriever.METADATA_KEY_SAMPLE_RATE, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_NUMBER_OF_CHANNELS, IMediaMetadataRetriever.METADATA_KEY_NUMBER_OF_CHANNELS, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_AUDIO_WAVE_CODEC, IMediaMetadataRetriever.METADATA_KEY_AUDIO_WAVE_CODEC, "0");
                extract(cv, metadata, VideoColumns.ARCHOS_AUDIO_BITRATE, IMediaMetadataRetriever.METADATA_KEY_AUDIO_BITRATE, "0");
                extract(cv, metadata, FileColumns.TITLE, IMediaMetadataRetriever.METADATA_KEY_TITLE, defaultTitle);
                break;
        }
        return cv;
    }

    /** helper to extract metadate key into ContentValues if that key is != null */
    private static void extract(ContentValues target, MediaMetadata metadata,
            String cvKey, int retrieverKey, String defaultValue) {
        extract(target, metadata, cvKey, retrieverKey, defaultValue, false);
    }

    /** helper to extract metadate key into ContentValues if that key is != null */
    private static void extract(ContentValues target, MediaMetadata metadata,
            String cvKey, int retrieverKey, String defaultValue, boolean treat0AsNull) {
        if (metadata.has(retrieverKey)) {
            String val = metadata.getString(retrieverKey);
            if (treat0AsNull && !isNumberOtherThanZero(val)) {
                target.put(cvKey, defaultValue);
            } else {
                target.put(cvKey, val);
            }
        } else {
            target.put(cvKey, defaultValue);
        }
    }

    private static boolean isNumberOtherThanZero(String input) {
        if (input == null || "0".equals(input)) return false;
        try {
            return Long.parseLong(input) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static long parseLong(String input, long fallback) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String getDefaultTitle(String path) {
        String ret = new File(path).getName();
        int lastDot = ret.lastIndexOf('.');
        if (lastDot > 0)
            ret = ret.substring(0, lastDot);
        return ret;
    }

    private static final String NOT_NETWORKINDEXED_BP = "storage_id & 1 AND _data NOT NULL AND _data != '' AND _size != '0'";
    private static final String NOT_NETWORKINDEXED_AP = "_data NOT NULL AND _data != '' AND _size != '0'";
    private static final String WHERE_MIN_ID_BP = NOT_NETWORKINDEXED_BP + " AND _id > ?";
    private static final String WHERE_MIN_ID_AP = NOT_NETWORKINDEXED_AP + " AND _id > ?";
    private static final String WHERE_ALL_BP = NOT_NETWORKINDEXED_BP;
    private static final String WHERE_ALL_AP = NOT_NETWORKINDEXED_AP;
    //storage_id does not exist in Android P causing a crash
    //two versions: before Android P (BP) and after Android P (AP)
    private final static String[] FILES_PROJECTION_AP = new String[]{
            BaseColumns._ID,
            MediaColumnsDATA,
            MediaColumns.DISPLAY_NAME,
            MediaColumns.SIZE,
            MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED,
            ImageColumns.BUCKET_ID,
            ImageColumns.BUCKET_DISPLAY_NAME,
            "format",
            FileColumns.PARENT
    };

    // Note: adb pushed files are not visible by on an onchange because _size=NULL to get the right size, cold boot or trigger a mediascan after adb push...

    private final static String[] FILES_PROJECTION_BP = new String[]{
            BaseColumns._ID,
            MediaColumnsDATA,
            MediaColumns.DISPLAY_NAME,
            MediaColumns.SIZE,
            MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED,
            ImageColumns.BUCKET_ID,
            ImageColumns.BUCKET_DISPLAY_NAME,
            "format",
            FileColumns.PARENT,
            "storage_id"
    };

    /** copies data from Android's media db to ours */
    // Tracks which storage ids were seen in the latest MediaStore query so we can
    // distinguish "volume missing" from "file removed" later in the process.
    private static final Set<Integer> sLastVisibleStorageIds = new HashSet<>();
    private static volatile boolean sRemoteProjectionHasStorageId = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O;

    private static synchronized void resetVisibleStorageIds() {
        sLastVisibleStorageIds.clear();
    }

    private static synchronized void recordVisibleStorageId(Integer storageId) {
        if (storageId != null) {
            sLastVisibleStorageIds.add(storageId);
        }
    }

    private static synchronized Set<Integer> getVisibleStorageIdsSnapshot() {
        return new HashSet<>(sLastVisibleStorageIds);
    }

    private static synchronized boolean remoteProjectionHasStorageId() {
        return sRemoteProjectionHasStorageId;
    }

    private static synchronized void disableRemoteStorageIdTracking() {
        sRemoteProjectionHasStorageId = false;
        sLastVisibleStorageIds.clear();
    }

    /** Inserts unmatched MediaStore rows already collected by reconciliation. */
    private static int copySnapshotData(ContentResolver cr, ReconciliationSnapshot snapshot,
            String minId) {
        if (cr == null || snapshot == null || !snapshot.complete || mIsImportInterrupted) return 0;
        long minimum = parseLong(minId, Long.MIN_VALUE);
        BulkInserter inserter = new BulkInserter(VideoStoreInternal.FILES_IMPORT, cr, 2000);
        int pending = 0;
        for (MediaStoreIdentity identity : snapshot.mediaStoreById.values()) {
            if (mIsImportInterrupted) break;
            if (identity.id <= minimum || snapshot.importedIds.contains(identity.id)) continue;
            ContentValues values = new ContentValues(identity.values);
            values.put(BaseColumns._ID, identity.id);
            inserter.add(values);
            snapshot.importedIds.add(identity.id);
            pending++;
        }
        int imported = inserter.execute();
        if (log.isDebugEnabled()) {
            log.debug("copySnapshotData: queued {} and inserted {} rows", pending, imported);
        }
        return imported;
    }

    private static int copyData(ContentResolver cr, String minId) {
        int imported = 0;
        String where = null;
        String[] whereArgs = null;
        Cursor allFiles = null;
        ContentValues cv = null;
        ExtStorageManager extStorageManager = ExtStorageManager.getExtStorageManager();
        String[] projection;
        resetVisibleStorageIds(); // keep storage list in sync with this pass
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            where = WHERE_ALL_AP + BLACKLIST;
            projection = FILES_PROJECTION_AP;
            if (minId != null) {
                where = WHERE_MIN_ID_AP + BLACKLIST;
                whereArgs = new String[]{minId};
            }
        } else {
            where = WHERE_ALL_BP + BLACKLIST;
            projection = FILES_PROJECTION_BP;
            if (minId != null)  {
                where = WHERE_MIN_ID_BP + BLACKLIST;
                whereArgs = new String[] { minId };
            }
        }
        try { // for some reasons external_primary is not readable on some devices hinting READ_EXTERNAL_STORAGE permission missing
            allFiles = CustomCursor.wrap(cr.query(MediaStore.Files.getContentUri("external"),
                    projection, where, whereArgs, BaseColumns._ID));
        } catch (IllegalArgumentException e) {
            log.error("copyData: exception while querying external_primary, missing READ_EXTERNAL_STORAGE?", e);
            if (CRASH_ON_ERROR) throw new RuntimeException(e);
        }
        if (allFiles != null) {
            int count = allFiles.getCount();
            int ccount = allFiles.getColumnCount();
            if (count > 0) {
                HashSet<Long> ids = new HashSet<>();
                // Single query is fast; HashSet prevents OOM that ArrayList caused
                Cursor c = cr.query(VideoStoreInternal.FILES_IMPORT, new String[] { "_id" }, null, null, null);
                if (c != null) {
                    while (c.moveToNext() && !mIsImportInterrupted)
                        ids.add(c.getLong(0));
                    c.close();
                }
                if (log.isDebugEnabled()) log.debug("copyData: loaded {} existing IDs", ids.size());
                // transaction size limited, acts like buffered output stream and auto-flushes queue
                BulkInserter inserter = new BulkInserter(VideoStoreInternal.FILES_IMPORT, cr, 2000);
                if (log.isDebugEnabled()) log.debug("copyData: found items to import:{}", count);
                final int numberOfRows = allFiles.getCount();
                int window = WINDOW_SIZE;
                int index = 0;
                int cursor_count = 0;
                // break down the scan in batch of WINDOW_SIZE in order to avoid SQLiteBlobTooBigException: Row too big to fit into CursorWindow crash
                // note that the db is NOT being modified during import --> use an index
                allFiles.close();
                do {
                    if (index + window > numberOfRows)
                        window = numberOfRows - index;

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                        where = WHERE_ALL_AP + BLACKLIST;
                        if (minId != null) {
                            where = WHERE_MIN_ID_AP + BLACKLIST;
                            whereArgs = new String[]{minId};
                        }
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) { // API>29 requires bundle to LIMIT but it exists since 26
                            final Bundle BUNDLE_AP = new Bundle();
                            BUNDLE_AP.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, where);
                            BUNDLE_AP.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, whereArgs);
                            BUNDLE_AP.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, new String[]{BaseColumns._ID});
                            BUNDLE_AP.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);
                            BUNDLE_AP.putInt(ContentResolver.QUERY_ARG_LIMIT, window);
                            BUNDLE_AP.putInt(ContentResolver.QUERY_ARG_OFFSET, index);
                            allFiles = CustomCursor.wrap(cr.query(MediaStore.Files.getContentUri("external"),
                                    FILES_PROJECTION_AP, BUNDLE_AP, null));
                        } else {
                            allFiles = CustomCursor.wrap(cr.query(MediaStore.Files.getContentUri("external"),
                                    FILES_PROJECTION_AP, where, whereArgs, BaseColumns._ID + " ASC LIMIT " + index + "," + window));
                        }
                    } else { // API below 26 LIMIT is allowed
                        where = WHERE_ALL_BP + BLACKLIST;
                        if (minId != null)  {
                            where = WHERE_MIN_ID_BP + BLACKLIST;
                            whereArgs = new String[] { minId };
                        }
                        allFiles = CustomCursor.wrap(cr.query(MediaStore.Files.getContentUri("external"),
                                FILES_PROJECTION_BP, where, whereArgs, BaseColumns._ID + " ASC LIMIT " + index + "," + window));
                    }
                    if (log.isDebugEnabled()) log.debug("copyData: new batch fetching cursor from index={}, window={} -> index+window={} <= {}", index, window, (index + window), numberOfRows);

                    String data;
                    Integer storageId;

                    int allFilesCount = allFiles.getCount();
                    if (allFiles != null && allFilesCount >0) {
                        if (log.isDebugEnabled()) log.debug("copyData: new batch cursor has size {}", allFilesCount);
                        while (allFiles.moveToNext() && !mIsImportInterrupted) {
                            cursor_count++;
                            if (log.isTraceEnabled()) log.trace("copyData: processing cursor number={}/{}, {}", cursor_count, numberOfRows, DatabaseUtils.dumpCurrentRowToString(allFiles));
                            try {
                                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) { // API26(O)+
                                    cv = new ContentValues(ccount + 1);
                                    DatabaseUtils.cursorRowToContentValues(allFiles, cv);
                                    data = allFiles.getString(Math.max(allFiles.getColumnIndex(MediaColumnsDATA), 0));
                                    if (data != null) {
                                        if (data.startsWith(sdCardPath))
                                            storageId = 1;
                                        else
                                            storageId = extStorageManager.getStorageId3(data);
                                    } else {
                                        storageId = 1;
                                    }
                                    if (log.isTraceEnabled()) log.trace("copyData: _data={} -> storageId={}", data, storageId);
                                    cv.put("storage_id", storageId);
                                    recordVisibleStorageId(storageId);
                                } else {
                                    cv = new ContentValues(ccount);
                                    DatabaseUtils.cursorRowToContentValues(allFiles, cv);
                                    int storageIdx = allFiles.getColumnIndex("storage_id");
                                    if (storageIdx >= 0) {
                                        recordVisibleStorageId(allFiles.getInt(storageIdx));
                                    }
                                }
                                if (!ids.contains(cv.getAsLong("_id")))
                                    inserter.add(cv);
                            } catch (IllegalStateException ignored) { } //we silently ignore empty lines - it means content has been deleted while scanning
                        }
                        imported += inserter.execute();
                        if (log.isDebugEnabled()) log.debug("copyData: inserted in dB {}", imported);
                    } else {
                        if (log.isDebugEnabled()) log.debug("copyData: allFiles null now");
                    }

                    index += window;
                    if (allFiles != null) allFiles.close();
                } while (window < numberOfRows && window > 0 && !mIsImportInterrupted);
            } else allFiles.close();
        }
        return imported;
    }

    /** helper to find the max of local_id (= newest file) in our db */
    private static final String[] MAX_LOCAL_PROJ = new String[] { "max(local_id)" };
    private static String getMaxLocalId (ContentResolver cr) {
        Cursor c = cr.query(VideoStoreInternal.FILES_IMPORT, MAX_LOCAL_PROJ, null, null, null);
        String result = null;
        if (c != null) {
            if (c.moveToFirst()) {
                result = c.getString(0);
            }
            c.close();
        }
        return TextUtils.isEmpty(result) ? "0" : result;
    }

    /**
     * helper to get count of imported files
     */
    private static final String[] COUNT_PROJ = new String[] { "count(*)" };
    private static int getLocalCount (ContentResolver cr) {
        int result = 0;
        int offset = 0;
        Cursor c = null;
        try {
            while (!mIsImportInterrupted) {
                try {
                    c = cr.query(VideoStoreInternal.FILES_IMPORT, COUNT_PROJ, null, null, BaseColumns._ID + " LIMIT " + WINDOW_SIZE + " OFFSET " + offset);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            result += c.getInt(0);
                        }
                        c.close();
                    }
                    if (c == null || c.getCount() < WINDOW_SIZE) break;
                    offset += WINDOW_SIZE;
                } catch (Exception e) {
                    log.error("getLocalCount: exception while moving to next cursor row!", e);
                    if (CRASH_ON_ERROR) throw new RuntimeException(e);
                    break;
                } finally {
                    if (c != null) c.close();
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    private static final String[] MAX_ID_PROJ = new String[] { "max(_id)" };
    /** helper to find the max of _id (=newest in android's db) in our db */
    private static String getMaxId(ContentResolver cr) {
        int offset = 0;
        String result = null;
        int maxId = 0;
        int highestMaxId = 0;
        Cursor c = null;
        try {
            while (!mIsImportInterrupted) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API>=30 requires bundle to LIMIT
                        Bundle queryArgs = new Bundle();
                        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, null);
                        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, null);
                        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, new String[]{BaseColumns._ID});
                        queryArgs.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
                        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, WINDOW_SIZE);
                        queryArgs.putInt(ContentResolver.QUERY_ARG_OFFSET, offset);
                        c = cr.query(VideoStoreInternal.FILES_IMPORT, MAX_ID_PROJ, queryArgs, null);
                    } else {
                        c = cr.query(VideoStoreInternal.FILES_IMPORT, MAX_ID_PROJ, null, null, BaseColumns._ID + " DESC LIMIT " + WINDOW_SIZE + " OFFSET " + offset);
                    }
                    if (c != null && c.moveToFirst()) {
                        result = c.getString(0);
                        if (result != null) {
                            maxId = Integer.parseInt(result);
                            if (maxId > highestMaxId) {
                                highestMaxId = maxId;
                                break;
                            }
                        }
                        break;
                    }
                    if (c == null || c.getCount() < WINDOW_SIZE) break;
                    offset += WINDOW_SIZE;
                    c.close();
                } catch (Exception e) {
                    log.error("getMaxId: exception while querying", e);
                    break;
                } finally {
                    if (c != null) c.close();
                }
            }
        } finally {
            if (c != null) c.close();
        }
        if (log.isDebugEnabled()) log.debug("getMaxId: highestMaxId={}", highestMaxId);
        return Integer.toString(highestMaxId);
    }

    /**
     * Update hidden/visible flags based on the media ids returned by the last MediaStore query.
     * We only touch storage ids that are currently mounted to avoid hiding content while a USB
     * drive is still reconnecting.
     */
    private void updateVolumeHiddenStates(String existingFiles) {
        purgeExpiredHiddenFiles(mCr);

        if (!remoteProjectionHasStorageId()) {
            // Post-Android P: Use path-based volume detection
            updateVolumeHiddenStatesByPath(existingFiles);
            return;
        }

        Set<Integer> mountedStorageIds = getMountedReadableStorageIds();
        if (mountedStorageIds.isEmpty()) {
            if (log.isDebugEnabled()) log.debug("updateVolumeHiddenStates: no mounted storage, skip hide/unhide");
            return;
        }

        String mountedClause = buildStorageIdSelection(mountedStorageIds);
        if (!TextUtils.isEmpty(mountedClause)) {
            ContentValues cvHidden = new ContentValues();
            cvHidden.put("volume_hidden", Long.valueOf(System.currentTimeMillis() / 1000));
            StringBuilder whereHidden = new StringBuilder("volume_hidden = 0 AND ").append(mountedClause);
            if (!TextUtils.isEmpty(existingFiles)) {
                whereHidden.append(" AND _id NOT IN (").append(existingFiles).append(")");
            }

            // Direct UPDATE is efficient; no need for windowing
            int hiddenCount = mCr.update(VideoStoreInternal.FILES_IMPORT, cvHidden, whereHidden.toString(), null);
            if (log.isDebugEnabled()) log.debug("updateVolumeHiddenStates: hidden {} rows", hiddenCount);
        }

        if (!TextUtils.isEmpty(existingFiles)) {
            Set<Integer> visibleIds = getVisibleStorageIdsSnapshot();
            visibleIds.retainAll(mountedStorageIds);
            String visibleClause = buildStorageIdSelection(visibleIds);
            if (!TextUtils.isEmpty(visibleClause)) {
                ContentValues cvPresent = new ContentValues();
                cvPresent.put("volume_hidden", 0);
                String wherePresent = "_id IN (" + existingFiles + ") AND volume_hidden != 0 AND " + visibleClause;

                // Direct UPDATE is efficient; no need for windowing
                int presentCount = mCr.update(VideoStoreInternal.FILES_IMPORT, cvPresent, wherePresent, null);
                if (log.isDebugEnabled()) log.debug("updateVolumeHiddenStates: unhidden {} rows", presentCount);
            }
        }
    }

    /**
     * Permanently remove rows that have been hidden for more than a month. Hidden rows are kept
     * around temporarily so that transient unmounts (USB power-saving, slow remount, MediaStore
     * indexing delays) don't cause data loss, but without this purge the historical backlog of
     * hidden rows grows without bound and every future import pass pays the cost of scanning it.
     */
    static void purgeExpiredHiddenFiles(ContentResolver cr) {
        long cutoff = System.currentTimeMillis() / 1000 - HIDDEN_FILES_RETENTION_SECONDS;
        int purged = cr.delete(VideoStoreInternal.FILES_IMPORT,
                "volume_hidden > 0 AND volume_hidden < ?", new String[]{String.valueOf(cutoff)});
        if (log.isDebugEnabled()) log.debug("purgeExpiredHiddenFiles: purged {} rows hidden before {}", purged, cutoff);
    }

    private Set<Integer> getMountedReadableStorageIds() {
        Set<Integer> mountedIds = new HashSet<>();

        String primaryState = Environment.getExternalStorageState();
        if (isMountedReadable(primaryState)) {
            mountedIds.add(VolumeState.Volume.getStorageId(0));
        }

        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        collectMountedStorageIds(storageManager.getExtSdcards(), mountedIds, storageManager);
        collectMountedStorageIds(storageManager.getExtUsbStorages(), mountedIds, storageManager);
        collectMountedStorageIds(storageManager.getExtOtherStorages(), mountedIds, storageManager);

        return mountedIds;
    }

    private void collectMountedStorageIds(List<String> paths, Set<Integer> target, ExtStorageManager storageManager) {
        if (paths == null) return;
        for (String path : paths) {
            if (TextUtils.isEmpty(path)) continue;
            String state = ExtStorageManager.getVolumeState(path);
            if (!isMountedReadable(state)) continue;
            Integer storageId = storageManager.getStorageId(path);
            if (storageId != null) {
                target.add(storageId);
            }
        }
    }

    private boolean isMountedReadable(String state) {
        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    private String buildStorageIdSelection(Set<Integer> storageIds) {
        if (storageIds == null || storageIds.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("storage_id IN (");
        boolean first = true;
        for (Integer id : storageIds) {
            if (id == null) continue;
            if (!first) sb.append(',');
            sb.append(id);
            first = false;
        }
        if (first) {
            return null;
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Post-Android P: Hide files from unmounted volumes, unhide files from mounted volumes
     * Enhanced with recent mount detection for aggressive deletion of missing files
     */
    private void updateVolumeHiddenStatesByPath(String existingFiles) {
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        List<String> mountedVolumePaths = new ArrayList<>();
        List<String> unmountedVolumePaths = new ArrayList<>();
        List<String> recentlyMountedVolumePaths = new ArrayList<>();  // NEW

        // Check primary storage
        String primaryPath = Environment.getExternalStorageDirectory().getPath();
        String primaryState = Environment.getExternalStorageState();
        if (isMountedReadable(primaryState)) {
            mountedVolumePaths.add(primaryPath);
            // Primary storage is rarely unmounted, so don't check for recent mount
        } else {
            unmountedVolumePaths.add(primaryPath);
        }

        // Build list of all currently mounted external volumes once
        List<String> allMountedExternal = new ArrayList<>();
        allMountedExternal.addAll(storageManager.getExtSdcards());
        allMountedExternal.addAll(storageManager.getExtUsbStorages());
        allMountedExternal.addAll(storageManager.getExtOtherStorages());

        // Check all external volumes and detect recently mounted ones
        checkVolumeStatesWithRecentMount(storageManager.getExtSdcards(), allMountedExternal, mountedVolumePaths, unmountedVolumePaths, recentlyMountedVolumePaths);
        checkVolumeStatesWithRecentMount(storageManager.getExtUsbStorages(), allMountedExternal, mountedVolumePaths, unmountedVolumePaths, recentlyMountedVolumePaths);
        checkVolumeStatesWithRecentMount(storageManager.getExtOtherStorages(), allMountedExternal, mountedVolumePaths, unmountedVolumePaths, recentlyMountedVolumePaths);

        // Also check for volumes in database that are no longer mounted (e.g., USB removed while app was in background)
        checkDatabaseForUnmountedVolumes(mountedVolumePaths, unmountedVolumePaths);

        if (log.isDebugEnabled()) log.debug("updateVolumeHiddenStatesByPath: mounted={}, unmounted={}, recentlyMounted={}",
                  mountedVolumePaths, unmountedVolumePaths, recentlyMountedVolumePaths);
        if (log.isDebugEnabled()) log.debug("updateVolumeHiddenStatesByPath: ExtStorageManager USB storages: {}", storageManager.getExtUsbStorages());

        long now = System.currentTimeMillis() / 1000;

        // Hide files from unmounted volumes
        if (!unmountedVolumePaths.isEmpty()) {
            hideFilesFromVolumes(unmountedVolumePaths, now);
        }

        // Unhide files from mounted volumes, but only those MediaStore currently confirms are
        // present. Unhiding unconditionally here would make every hidden file (including ones
        // about to be re-hidden a few lines below because they are genuinely gone) flash back to
        // visible for the duration of this import pass, causing deleted files to flicker in the UI.
        if (!mountedVolumePaths.isEmpty() && !TextUtils.isEmpty(existingFiles)) {
            unhideFilesFromVolumes(mCr, mountedVolumePaths, existingFiles);
        }

        // For recently mounted volumes: Delete missing files more aggressively
        if (!recentlyMountedVolumePaths.isEmpty() && !TextUtils.isEmpty(existingFiles)) {
            hideDeletedFilesFromRecentlyMountedVolumes(recentlyMountedVolumePaths, existingFiles, now);
        }

        // Additionally delete files from other mounted volumes that are missing from MediaStore
        if (!mountedVolumePaths.isEmpty() && !TextUtils.isEmpty(existingFiles)) {
            List<String> nonRecentlyMounted = new ArrayList<>(mountedVolumePaths);
            nonRecentlyMounted.removeAll(recentlyMountedVolumePaths);
            if (!nonRecentlyMounted.isEmpty()) {
                hideDeletedFilesFromMountedVolumes(nonRecentlyMounted, existingFiles, now);
            }
        }
    }


    private void hideFilesFromVolumes(List<String> volumePaths, long timestamp) {
        if (volumePaths.isEmpty()) return;

        ContentValues cv = new ContentValues();
        cv.put("volume_hidden", timestamp);

        StringBuilder where = new StringBuilder("volume_hidden = 0 AND (");
        boolean first = true;
        for (String path : volumePaths) {
            if (!first) where.append(" OR ");
            where.append("_data LIKE '").append(path).append("/%'");
            first = false;
        }
        where.append(")");

        // Direct UPDATE is efficient; no need for windowing (UPDATE doesn't return cursors)
        int hidden = mCr.update(VideoStoreInternal.FILES_IMPORT, cv, where.toString(), null);
        if (log.isDebugEnabled()) log.debug("hideFilesFromVolumes: hidden {} files from unmounted volumes", hidden);
    }

    static void unhideFilesFromVolumes(ContentResolver cr, List<String> volumePaths, String existingFiles) {
        if (volumePaths.isEmpty() || TextUtils.isEmpty(existingFiles)) return;

        ContentValues cv = new ContentValues();
        cv.put("volume_hidden", 0);

        // Only unhide rows MediaStore currently confirms are present; rows missing from
        // existingFiles are left untouched here and handled by verifyAndHideDeletedFiles below.
        StringBuilder where = new StringBuilder("volume_hidden != 0 AND _id IN (")
                .append(existingFiles).append(") AND (");
        boolean first = true;
        for (String path : volumePaths) {
            if (!first) where.append(" OR ");
            where.append("_data LIKE '").append(path).append("/%'");
            first = false;
        }
        where.append(")");

        // Direct UPDATE is efficient; no need for windowing (UPDATE doesn't return cursors)
        int unhidden = cr.update(VideoStoreInternal.FILES_IMPORT, cv, where.toString(), null);
        if (log.isDebugEnabled()) log.debug("unhideFilesFromVolumes: unhidden {} files from mounted volumes", unhidden);
    }

    private void checkDatabaseForUnmountedVolumes(List<String> mountedVolumePaths, List<String> unmountedVolumePaths) {
        // Query database for distinct volume paths from file paths
        // Only check files that are currently visible (volume_hidden = 0)
        if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: checking database for unmounted volumes");
        if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: currently mounted: {}", mountedVolumePaths);
        Cursor c = null;
        try {
            // Get all distinct file paths and extract volume paths in Java (safer than complex SQL)
            c = mCr.query(VideoStoreInternal.FILES_IMPORT,
                    new String[]{"DISTINCT _data"},
                    "volume_hidden = 0 AND _data LIKE '/storage/%'",
                    null,
                    null);

            java.util.Set<String> volumePaths = new java.util.HashSet<>();
            if (c != null) {
                if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: found {} distinct file paths in database", c.getCount());
                while (c.moveToNext()) {
                    String filePath = c.getString(0);
                    if (filePath != null && filePath.startsWith("/storage/")) {
                        // Extract volume path: /storage/XXXXX or /storage/emulated/0
                        String[] parts = filePath.split("/");
                        if (parts.length >= 3) {
                            String volumePath;
                            if (parts[2].equals("emulated") && parts.length >= 4) {
                                // Handle /storage/emulated/0
                                volumePath = "/storage/emulated/" + parts[3];
                            } else {
                                // Handle /storage/39F3-140A
                                volumePath = "/storage/" + parts[2];
                            }
                            volumePaths.add(volumePath);
                        }
                    }
                }
            }

            // Build list of all currently mounted volumes from ExtStorageManager
            ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
            List<String> allCurrentlyMounted = new ArrayList<>();
            allCurrentlyMounted.addAll(storageManager.getExtSdcards());
            allCurrentlyMounted.addAll(storageManager.getExtUsbStorages());
            allCurrentlyMounted.addAll(storageManager.getExtOtherStorages());
            allCurrentlyMounted.add(Environment.getExternalStorageDirectory().getPath()); // Primary storage

            // Check each volume path to see if it's unmounted
            if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: extracted {} distinct volume paths from database", volumePaths.size());
            for (String volumePath : volumePaths) {
                if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: checking volume {}", volumePath);
                // If this volume is not in mounted list and not already in unmounted list, check if it's really unmounted
                if (!mountedVolumePaths.contains(volumePath) && !unmountedVolumePaths.contains(volumePath)) {
                    // Check if volume is in ExtStorageManager's current mounted list
                    boolean isMounted = allCurrentlyMounted.contains(volumePath);
                    if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: volume {} isMounted={}", volumePath, isMounted);
                    if (!isMounted) {
                        if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: found unmounted volume in database: {}", volumePath);
                        unmountedVolumePaths.add(volumePath);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("checkDatabaseForUnmountedVolumes: volume {} is in mounted list or already in unmounted list", volumePath);
                }
            }
        } catch (Exception e) {
            log.error("checkDatabaseForUnmountedVolumes: error querying database", e);
        } finally {
            if (c != null) c.close();
        }
    }

    private void hideDeletedFilesFromMountedVolumes(List<String> volumePaths, String existingFiles, long timestamp) {
        if (volumePaths.isEmpty() || TextUtils.isEmpty(existingFiles)) return;

        StringBuilder where = new StringBuilder("_id NOT IN (").append(existingFiles).append(") AND (");
        boolean first = true;
        for (String path : volumePaths) {
            if (!first) where.append(" OR ");
            where.append("_data LIKE '").append(path).append("/%'");
            first = false;
        }
        where.append(")");

        // For mounted volumes, verify files truly don't exist before hiding them
        // This prevents data loss when USB drives have MediaStore indexing delays
        verifyAndHideDeletedFiles(mCr, where.toString(), timestamp, "hideDeletedFilesFromMountedVolumes");
    }

    /**
     * More careful handling for recently remounted volumes
     */
    private void hideDeletedFilesFromRecentlyMountedVolumes(List<String> volumePaths, String existingFiles, long timestamp) {
        if (volumePaths.isEmpty() || TextUtils.isEmpty(existingFiles)) return;

        StringBuilder where = new StringBuilder("_id NOT IN (").append(existingFiles).append(") AND (");
        boolean first = true;
        for (String path : volumePaths) {
            if (!first) where.append(" OR ");
            where.append("_data LIKE '").append(path).append("/%'");
            first = false;
        }
        where.append(")");

        // For recently mounted volumes, verify files truly don't exist before hiding
        // This prevents data loss from USB drives with MediaStore indexing delays or power saving
        verifyAndHideDeletedFiles(mCr, where.toString(), timestamp, "hideDeletedFilesFromRecentlyMountedVolumes");
    }

    /**
     * Verify files still exist on filesystem before hiding them to prevent data loss from USB indexing delays
     */
    static void verifyAndHideDeletedFiles(ContentResolver cr, String whereClause, long timestamp, String logTag) {
        Cursor c = null;
        try {
            // Get list of files that aren't in MediaStore
            c = cr.query(VideoStoreInternal.FILES_IMPORT,
                    new String[]{"_id", "_data"},
                    whereClause,
                    null,
                    null);

            if (c == null) return;

            java.io.File file;
            StringBuilder toHide = new StringBuilder();
            StringBuilder toUnhide = new StringBuilder();

            while (c.moveToNext() && !mIsImportInterrupted) {
                long id = c.getLong(0);
                String filePath = c.getString(1);

                if (filePath == null) continue;

                file = new java.io.File(filePath);
                StringBuilder target = file.exists() ? toUnhide : toHide;
                if (target.length() > 0) target.append(',');
                target.append(id);
            }

            int hiddenCount = 0;
            if (toHide.length() > 0) {
                ContentValues cv = new ContentValues();
                cv.put("volume_hidden", timestamp);
                // File truly doesn't exist, hide it
                hiddenCount = cr.update(VideoStoreInternal.FILES_IMPORT, cv,
                        "_id IN (" + toHide + ")", null);
            }

            int existingCount = 0;
            if (toUnhide.length() > 0) {
                ContentValues cv = new ContentValues();
                cv.put("volume_hidden", 0);
                // File exists on filesystem even though not in MediaStore
                // Unhide it so it can be rescanned (handles MediaStore indexing delays)
                existingCount = cr.update(VideoStoreInternal.FILES_IMPORT, cv,
                        "_id IN (" + toUnhide + ")", null);
            }

            if (log.isDebugEnabled()) log.debug("{}: verified files - hidden {}, found existing but unindexed {}", logTag, hiddenCount, existingCount);
        } catch (Exception e) {
            log.error("verifyAndHideDeletedFiles: error verifying files", e);
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Enhanced volume state checking that detects recently mounted volumes
     * @param volumePaths List of volume paths to check
     * @param allMountedVolumes Pre-built list of all currently mounted volumes (for efficiency)
     * @param mounted Output list for mounted volumes
     * @param unmounted Output list for unmounted volumes
     * @param recentlyMounted Output list for volumes that were recently hidden and are now mounted
     */
    private void checkVolumeStatesWithRecentMount(List<String> volumePaths, List<String> allMountedVolumes,
                                                List<String> mounted, List<String> unmounted,
                                                List<String> recentlyMounted) {
        for (String path : volumePaths) {
            // Check if volume is in ExtStorageManager's mounted lists
            boolean isMounted = allMountedVolumes.contains(path);
            if (log.isDebugEnabled()) log.debug("checkVolumeStatesWithRecentMount: path={}, isMounted={}", path, isMounted);
            if (isMounted) {
                mounted.add(path);
                // Check if this volume was recently hidden (within last 5 minutes)
                if (wasVolumeRecentlyHidden(path)) {
                    recentlyMounted.add(path);
                }
            } else {
                unmounted.add(path);
            }
        }
    }

    /**
     * Check if volume was recently hidden using windowed cursor approach
     */
    private boolean wasVolumeRecentlyHidden(String volumePath) {
        long fiveMinutesAgo = (System.currentTimeMillis() / 1000) - 300; // 5 minutes
        String selection = "volume_hidden > ? AND _data LIKE ?";
        String[] args = {String.valueOf(fiveMinutesAgo), volumePath + "/%"};

        // Use windowed approach with small limit since we only need to check existence
        Cursor c = null;
        try {
            c = mCr.query(VideoStoreInternal.FILES_IMPORT,
                         new String[]{"_id"}, selection, args, "_id ASC LIMIT 1");
            boolean wasRecentlyHidden = (c != null && c.getCount() > 0);
            return wasRecentlyHidden;
        } finally {
            if (c != null) c.close();
        }
    }

    private static final String[] REMOTE_LIST_PROJECTION_BP = new String[] {
        BaseColumns._ID,
        "storage_id"
    };

    private static final String[] REMOTE_LIST_PROJECTION_AP = new String[] {
        BaseColumns._ID
    };
    /** helper to get a comma separated list of all ids */
    private static String getRemoteIdList(ContentResolver cr) {
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        int offset = 0;
        Cursor c = null;
        resetVisibleStorageIds(); // new fetch, flush previous storage ids
        boolean projectionHasStorageId = remoteProjectionHasStorageId();
        String[] projection = projectionHasStorageId ? REMOTE_LIST_PROJECTION_BP : REMOTE_LIST_PROJECTION_AP;
        try {
            while (!mIsImportInterrupted) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API>=30 requires bundle to LIMIT
                        Bundle queryArgs = new Bundle();
                        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, null);
                        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, null);
                        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, new String[]{BaseColumns._ID});
                        queryArgs.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);
                        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, WINDOW_SIZE);
                        queryArgs.putInt(ContentResolver.QUERY_ARG_OFFSET, offset);
                        c = cr.query(MediaStore.Files.getContentUri("external"), projection, queryArgs, null);
                    } else {
                        c = cr.query(MediaStore.Files.getContentUri("external"),
                                projection, null, null,
                                BaseColumns._ID + " LIMIT " + WINDOW_SIZE + " OFFSET " + offset);
                    }

                    int count = 0;

                    if (c != null) {
                        int storageIdx = projectionHasStorageId ? c.getColumnIndex("storage_id") : -1;
                        while (c.moveToNext() && count < WINDOW_SIZE && !mIsImportInterrupted) {
                            count++;
                            sb.append(prefix).append(c.getString(0));
                            prefix = ",";
                            if (storageIdx >= 0) {
                                recordVisibleStorageId(c.getInt(storageIdx));
                            }
                        }
                        if (log.isDebugEnabled()) log.debug("getRemoteIdList: count={} WINDOW_SIZE={} offset={}", count, WINDOW_SIZE, offset);
                        if (count < WINDOW_SIZE) {
                            c.close();
                            break;
                        }
                        offset += WINDOW_SIZE;
                        c.close();
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    log.error("getRemoteIdList: exception while moving to next cursor row!", e);
                    if (e instanceof IllegalArgumentException && projectionHasStorageId) {
                        log.warn("getRemoteIdList: storage_id column unavailable, falling back to legacy projection");
                        disableRemoteStorageIdTracking();
                        projectionHasStorageId = false;
                        projection = REMOTE_LIST_PROJECTION_AP;
                        continue;
                    }
                    if (CRASH_ON_ERROR) throw new RuntimeException(e);
                    break;
                } finally {
                    if (c != null) c.close();
                }
            }
        } finally {
            if (c != null) c.close();
        }
        String result = sb.toString();
        if (log.isTraceEnabled()) log.trace("getRemoteIdList: ids of files visible {}", result);
        return TextUtils.isEmpty(result) ? "" : result;
    }


    public static boolean isNoMediaPath(Uri uri) {
        String path = uri.toString();
        if (log.isDebugEnabled()) log.debug("isNoMediaPath: check {}", path);

        if (path == null) return false;

        // return true if file or any parent directory has name starting with a dot
        if (path.indexOf("/.") >= 0) return true;

        // now check to see if any parent directories have a ".nomedia" file
        // start from 1 so we don't bother checking in the root directory
        int offset = Uri.parse(path).getScheme()!=null?Uri.parse(path).getScheme().length()+3:1;//go after smb://

        // in case of smb, skip checking smb://SERVER/.nomedia since it makes jcifs-ng crash
        if ((uri.getScheme() != null) && uri.getScheme().startsWith("smb")) // in case of smb server skip the serveur name since exists causes a crash with jcifs-ng
            offset = path.indexOf('/', offset) + 1; // +1 moves past next slash

        while (offset >= 0) {
            int slashIndex = path.indexOf('/', offset);
            // Archos NOTE: here must be >= instead of >
            if (slashIndex >= offset) {
                slashIndex++; // move past slash
                Uri file = Uri.parse(path.substring(0, slashIndex) + ".nomedia");
                if (log.isDebugEnabled()) log.debug("isNoMediaPath: check {}", file.toString());
                FileEditor fe = FileEditorFactoryWithUpnp.getFileEditorForUrl(file, null);
                if (fe.exists()) {
                    // we have a .nomedia in one of the parent directories
                    return true;
                }
            }
            offset = slashIndex;
        }
        return false;
    }
    /** adjusted copy of {@link MediaScanner#isNoMediaPath(String)} */
    public static boolean isNoMediaPath(String path) {
        Uri uri = Uri.parse(path);
        if(uri.getScheme()!=null) //removing file://
            path = uri.getPath();
        if (path == null) return false;

        // return true if file or any parent directory has name starting with a dot
        if (path.indexOf("/.") >= 0) return true;

        // TODO: determine if this method needs to be fully implemented to work with smb:// or network shares
        if (isNetworkShare(path)) {
            log.warn("isNoMediaPath not fully checking {}", path);
            return false;
        }

        // now check to see if any parent directories have a ".nomedia" file
        // start from 1 so we don't bother checking in the root directory
        int offset = 1;
        while (offset >= 0) {
            int slashIndex = path.indexOf('/', offset);
            // Archos NOTE: here must be >= instead of >
            if (slashIndex >= offset) {
                slashIndex++; // move past slash
                File file = new File(path.substring(0, slashIndex) + ".nomedia");
                if (file.exists()) {
                    // we have a .nomedia in one of the parent directories
                    return true;
                }
            }
            offset = slashIndex;
        }
        return isNoMediaFile(path);
    }

    private static boolean isNoMediaFile(String path) {
        File file = new File(path);
        if (file.isDirectory()) return false;

        // special case certain file names
        // I use regionMatches() instead of substring() below
        // to avoid memory allocation
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 2 < path.length()) {
            // ignore those ._* files created by MacOS
            if (path.regionMatches(lastSlash + 1, "._", 0, 2)) {
                return true;
            }

            /* Archos: No need to check for images
            // ignore album art files created by Windows Media Player:
            // Folder.jpg, AlbumArtSmall.jpg, AlbumArt_{...}_Large.jpg
            // and AlbumArt_{...}_Small.jpg
            if (path.regionMatches(true, path.length() - 4, ".jpg", 0, 4)) {
                if (path.regionMatches(true, lastSlash + 1, "AlbumArt_{", 0, 10) ||
                        path.regionMatches(true, lastSlash + 1, "AlbumArt.", 0, 9)) {
                    return true;
                }
                int length = path.length() - lastSlash - 1;
                if ((length == 17 && path.regionMatches(
                        true, lastSlash + 1, "AlbumArtSmall", 0, 13)) ||
                        (length == 10
                         && path.regionMatches(true, lastSlash + 1, "Folder", 0, 6))) {
                    return true;
                }
            }
            */
        }
        /* Archos: No need to check for images
        // ignores images inside Music directory (in order to don't spam gallery with music cover)
        if (path.startsWith(MUSIC_STORAGE_PATH)) {
            MediaFileType type = MediaFile.getFileType(path);
            if (type != null && MediaFile.isImageFileType(type.fileType))
                return true;
        }
        */
        return false;
    }

    public void interruptImport() {
        mIsImportInterrupted = true;
    }
}
