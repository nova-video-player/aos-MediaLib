# Media scanning, import & autoscrape coordination (spec)

This document specifies two indexing paths:

1. how a network (remote) re-index runs from the periodic refresh trigger to the
   single autoscrape fired after clean batch completion; and
2. how `VideoStoreImportImpl` mirrors Android MediaStore, including primary and
   removable-volume lifecycle rules.

It documents runtime contracts enforced by the indexing write paths, not schema
migration procedure (see `DATABASE_UPDATE.md`) or scrape matching heuristics (see
`SCRAPE.md`).

Sentry: NOVA-VIDEO-PLAYER-15A7

## 1. Overview

```
NetworkAutoRefresh (BroadcastReceiver)
   └─ build eligible URI list + protocol setup
   └─ startNetworkScanBatch(memberCount)            ── AutoScrapeService
   └─ staggered handler.postDelayed(...) broadcasts
          ↓ (one ACTION_VIDEO_SCANNER_SCAN_FILE per folder, carrying batch id)
NetworkScannerServiceVideo (Service)
   └─ dedup queued URIs
   └─ doScan(uri, batchId) → performScan → VideoProvider DB write
   └─ completeNetworkScan(batchId, hadError, resolved)   ── AutoScrapeService
          ↓ (owner only, count→0)
AutoScrapeService.handleNetworkScanCompletion(ctx, completion)
   └─ start autoscrape iff clean batch with ≥1 resolved folder
```

Key files:

- `src/com/archos/mediaprovider/video/NetworkAutoRefresh.java` — refresh trigger / batch starter.
- `src/com/archos/mediaprovider/video/NetworkScannerServiceVideo.java` — per-folder scan + completion reporting.
- `src/com/archos/mediascraper/AutoScrapeService.java` — batch accounting + completion routing.
- `src/com/archos/mediaprovider/video/VideoProvider.java` — transactional DB writes the scans depend on.
- `src/com/archos/mediaprovider/video/VideoStoreImportImpl.java` — MediaStore import, path reconciliation, and volume visibility.
- `src/com/archos/mediaprovider/video/VideoStoreImportService.java` — mount/unmount import scheduling and delayed cleanup.
- `src/com/archos/mediaprovider/video/VideoOpenHelper.java` — `files_import`, `files`, and volume lifecycle triggers.

## 2. Batch model & invariants

A "batch" is the set of folders one refresh decides to re-scan. Accounting lives
in `AutoScrapeService` behind a single lock (`networkScanLock`). The following are
non-negotiable contracts; the tests in `TEST.md` exist to keep them true.

### 2.1 Membership is registered atomically, up front

`startNetworkScanBatch(int memberCount)` registers the **entire** membership in
one locked step, before any scan is scheduled. Membership is never incremented as
scans are queued. Reason: if the count grew one-at-a-time, an early-finishing scan
could observe a partially-built batch, drive the count to zero, complete the batch,
and make every later registration stale — stranding those folders and firing
autoscrape too early.

Consequently, `NetworkAutoRefresh` resolves the eligible URI list **and runs each
folder's (possibly failing) protocol setup** (UPnP / FTP / SFTP) *before* calling
`startNetworkScanBatch`. A protocol setup failure is therefore excluded from batch
membership. Scheduling can still fail after registration; that terminal path must
release the member's slot as described in 2.4.

### 2.2 Unique batch IDs; the `0` sentinel is dual-purpose

Each batch gets an id that is unique and monotonically increasing **within the
current process**. The sequence is in-memory and restarts with the process.
`STANDALONE_SCAN_BATCH_ID = 0` is never handed out as a real id and has two meanings:

- on a **scan request**: an un-batched (standalone) scan — e.g. a manual single-folder
  scan — that completes as its own one-off batch without touching active-batch state;
- as the **return of `startNetworkScanBatch`**: "rejected, a batch is already active"
  (see 2.3).

### 2.3 Overlapping batches are rejected, not merged

If a batch is already in progress, `startNetworkScanBatch` returns
`STANDALONE_SCAN_BATCH_ID` and changes nothing. The caller **must** check for the
sentinel and schedule nothing. `NetworkAutoRefresh.doRescan` additionally guards
with `AutoScrapeService.getNetworkScanCount() > 0` (alongside `isScannerAlive()`)
because a batch can be counted but not yet "alive" during the initial
delayed-broadcast window; without that guard an overlapping refresh could reset a
valid active batch.

### 2.4 Every member reports exactly once

For each registered member exactly one `completeNetworkScan(batchId, hadError,
resolvedSuccessfully)` call is made, regardless of how the member ends:

- a folder that actually scans reports from `doScan`'s `finally`;
- a folder whose request is dropped as a **duplicate** (already queued) releases its
  slot immediately;
- a folder whose `postDelayed` **fails to schedule** (looper exiting) releases its slot.

Exactly-once completion is a caller obligation, not an idempotence guarantee.
`completeNetworkScan` does not keep per-member tokens, so a duplicate completion for
the active batch would decrement the count twice and could finish the batch early.
A completion whose `batchId` does not match the current batch (a **stale** completion
from an older batch) is ignored and cannot corrupt the current batch.

### 2.5 Aggregation under one lock; single owner runs post-scan work

`completeNetworkScan` aggregates `hadError` / `resolvedSuccessfully` across the whole
batch and decrements the count under one lock. Only the caller that drives the count
to zero receives `completedBatch == true` together with the batch-aggregated
`batchHadError` / `batchHadSuccess`, then the batch id is reset to the sentinel.
Holding the decrement and the flags under the same lock prevents two threads from
both observing zero and racing on post-scan handling.

`resetNetworkScanCount()` force-clears all of this (count, flags, current id) for
recovery; it warns if it had to reset a non-zero orphaned counter.

## 3. Completion routing

All three terminal paths funnel through one method so the batch outcome is acted on
exactly once, no matter which member happens to finish last:

```java
AutoScrapeService.handleNetworkScanCompletion(Context ctx, NetworkScanCompletion completion)
```

It is a no-op for non-owners (`completedBatch == false`). For the owner:

- `batchHadError` → **skip** autoscrape (a DB error occurred; the index may be
  incomplete, so do not scrape stale data);
- else `batchHadSuccess` and `AutoScrapeService.isEnable(ctx)` → start autoscrape via
  `startServiceAfterNetworkScan(ctx)`;
- else (clean but no folder resolved successfully) → do nothing.

`batchHadSuccess` does not mean that a row was inserted. It means at least one target
resolved and its scan reached the end without a database error. A successful scan
that found no new files still satisfies this condition.

Autoscrape is intentionally **not** started when the scans are merely queued: doing
so would scrape against the not-yet-updated database, run in parallel with scanning,
and defeat the full-disk suppression below. It starts only on real batch completion.

## 4. Database write safety during scans

Scans write through `VideoProvider.bulkInsert` and `applyBatch`. **Each invocation**
is wrapped in its own SQLite transaction; a whole folder scan can issue several such
calls and is not one atomic transaction. If a later write fails, rows committed by
earlier calls remain in the index. Two rules protect each write transaction:

### 4.1 Transaction before VOB handling

The DB transaction is opened (`beginTransactionNonExclusive()`) **before** entering
`VobHandler` transaction mode (`mVobHandler.onBeginTransaction()`), and
`onEndTransaction()` runs in a nested `finally`. Reason: if `beginTransaction()`
failed *after* the VOB handler flag was set, the handler would be stuck "in
transaction" with no balancing `onEndTransaction()` to drain its queued work.
(`VobHandler` collapses a DVD `VIDEO_TS` folder's many `.vob` files into one visible
entry; it defers its own DB work while a bulk operation is in flight — see
`VobHandler.java`.)

### 4.2 Rollback suppression vs commit propagation

On the failure path SQLite may have already **auto-rolled-back** the native
transaction (disk full, IO error, interrupt); `endTransaction()` then throws
`"cannot rollback - no transaction is active"`, which would mask the real cause. That
secondary exception is suppressed. A `commitRequested` flag distinguishes the paths:

- failure path (`commitRequested == false`) → swallow the `SQLiteException`, log a warning;
- success path (`commitRequested == true`) → `endTransaction()` issues the COMMIT, so
  **let commit failures propagate** rather than report a success that never committed.

Change notifications (`notifyAllContentUri()`) are only sent when data was actually
committed (`result > 0` / `result != null`).

## 5. Full-disk / IO degradation

`NetworkScannerServiceVideo.performScan` classifies database failures:

- `SQLiteFullException` / `SQLiteDiskIOException` — **expected** storage failures.
  Handled gracefully: the scan is marked `hadDbError = true` (so the batch still
  completes but suppresses autoscrape), and no crash is raised.
- any other `SQLiteException` — **unexpected**. Reported to Sentry
  (`io.sentry.Sentry.captureException(e)`) and still marked `hadDbError = true`, so
  the batch can always complete instead of being stranded.

`ScanResult(resolved, hadDbError)` carries this back to `doScan`, which feeds it into
`completeNetworkScan`. A folder counts as success only when it resolved **and** had
no DB error.

## 6. Failure and recovery matrix

| Condition | Batch result | Observable/recovery behavior |
| --- | --- | --- |
| Protocol setup fails | URI is excluded before batch creation | Warning log; no slot exists to release. |
| Delayed request cannot be scheduled | Slot released as unresolved | Other members continue; a prior success may still trigger autoscrape. |
| Registered request is rejected as already queued | Slot released as unresolved | The already queued request remains responsible for its own completion. |
| Target cannot be resolved | Clean, unresolved completion | `AUTO_RESCAN_ERROR` records `-1`; other successful members may still trigger autoscrape. |
| Traversal/listing reports an error | Resolved completion without a DB error | `AUTO_RESCAN_ERROR` records `-1`; the scan is not classified as a database failure. |
| `SQLiteFullException` or `SQLiteDiskIOException` | Batch DB-error flag set | Scan aborts cleanly and autoscrape is suppressed for the whole batch. Earlier committed write transactions remain committed. |
| Other `SQLiteException` | Batch DB-error flag set | Captured explicitly in Sentry, scan aborts cleanly, autoscrape suppressed. |
| Unchecked non-SQLite exception | Slot released by `finally`, then exception propagates | The thread failure remains observable. It is not classified as a DB error, so another successful member can still make the batch eligible for autoscrape. |
| Process death | In-memory batch state, id sequence, and delayed callbacks are lost | There is no persisted batch recovery. A later periodic, forced, or manual scan performs recovery. |

`AUTO_RESCAN_ERROR` is diagnostic state read by `NetworkAutoRefresh.getLastError()`.
Setting it does **not** enqueue an immediate retry. `-1` means a folder could not be
fully scanned; `-2` means no local network was available. Recovery depends on a later
scheduled refresh or an explicit user/system trigger.

Database failure suppression is batch-wide: one DB failure prevents post-batch
autoscrape even when other folders completed successfully. This avoids adding more
database work under full-disk or I/O-failure conditions. It does not roll back writes
already committed by other provider transactions.

The coordination state is process-local and deliberately not persisted. After
process restart, the counter, current batch id, and id sequence return to their
initial values. Stale-id rejection protects batches within one process lifetime; it
is not a durable identity mechanism across restarts. Do not persist or externally
replay batch IDs as long-lived identifiers.

## 7. Validation limits

The Robolectric tests cover accounting, ownership, service-start decisions, and
injected provider failures. They do not perform a real network traversal, exhaust a
device filesystem, reproduce every Android SQLite version, or simulate process death
with pending Android service redelivery. Validate those conditions on representative
devices when changing their behavior.

## 8. Local MediaStore import and removable storage

Network shares use `NetworkScannerServiceVideo`; local primary storage, SD cards,
and USB storage are discovered through Android MediaStore and mirrored by
`VideoStoreImportImpl`. MediaStore is the discovery source, but Nova's database is
the durable owner of scraper metadata, artwork relationships, playback state, and
user state.

```
Android MediaStore files
       ↓ full/incremental import
files_import                    imported MediaStore identity and volume state
       ↓ insert/delete/visibility triggers
files                           stable Nova file row and playback/scraper state
       ↓ remote_id relationships
movie / episode / artwork       scraped metadata
```

### 8.1 Identity and table ownership

`files_import` mirrors columns supplied by MediaStore, including MediaStore `_id`,
path, size, timestamps, parent, and Nova's `storage_id` / `volume_hidden` state.
`files` is the application-facing file table. On initial import its `_id` and
`remote_id` equal the MediaStore `_id`. They have different declared relationships,
but existing views and triggers assume that the three imported identities remain
equal:

- `files._id` is Nova's application-facing file identity. Playback state is stored
  on this row; subtitles reference this key with `ON UPDATE CASCADE`, while
  `videothumbnails.video_id` uses it without a foreign key.
- `files.remote_id` tracks the current imported identity. Movie and episode
  `video_id` foreign keys reference it with `ON UPDATE CASCADE`.
- `files_import._id` is the current MediaStore identity used to decide whether a row
  has already been imported.

Do not solve a move by deleting and reinserting the `files` row. Deletion can
cascade through movie/episode rows and their poster/backdrop relationships. Update
the existing row in place. For a new-id remap, update all three ids together: leaving
`files._id` at the old value breaks the `volume_hidden` trigger and the video views,
which join movie/episode `video_id` against `files._id` even though their foreign keys
reference `files.remote_id`.

### 8.2 Full and incremental import

Both import modes query `MediaStore.Files` and then scan imported rows whose media
metadata is incomplete:

- full import enumerates all eligible MediaStore rows;
- incremental import first loads the highest imported id and copies newer ids;
- both update mounted/unmounted volume visibility and run path reconciliation;
- `copyData` skips a MediaStore row when its `_id` already exists in `files_import`.

That final rule is why path reconciliation is required: an unchanged MediaStore id
with a changed path would otherwise be skipped forever, leaving Nova pointed at the
old path.

The current stable-id reconciliation runs after `copyData`. Any future reconciliation
that pairs an old id with a newly assigned MediaStore id must run **before**
`copyData`, or the new row will already have been inserted and a safe merge will be
more complicated.

### 8.3 Volume hide, remount, and retention lifecycle

An unavailable removable volume is not equivalent to deleted media. Nova keeps its
rows and changes visibility instead:

1. On unmount or absence, matching `files_import` rows receive a non-zero
   `volume_hidden` timestamp.
2. The update trigger propagates that state to `files`, removing the media from
   normal visible queries without deleting metadata.
3. On remount, rows belonging to the mounted storage id/path are unhidden and an
   import refreshes the volume.
4. For a mounted volume, a row missing from MediaStore is checked with
   `File.exists()` before it is hidden. This protects against MediaStore indexing
   delay immediately after mount.
5. The `hide_volume_cmd` cleanup policy may delete rows that have remained hidden
   for more than one month when cleanup for that volume runs.

The retention window is intentional. It preserves scraper and playback state across
normal unplug/replug cycles and provides the old row needed to reconcile a file that
was moved while the device was disconnected.

Android-version handling differs internally: older releases can use MediaStore's
`storage_id`; newer releases use mounted path prefixes because that projection is no
longer available. The lifecycle contract above must remain the same on both paths.
`ExtStorageManager`'s SD/USB/other lists are the mounted-readable source on recent
Android versions. Do not recheck those paths through its legacy `IMountService`
reflection: hidden-API restrictions can report `MEDIA_REMOVED` for a mounted volume.
If no removable storage hash is available, reconciliation preserves MediaStore's
projected or the existing row's `storage_id`; it must not substitute the primary
volume id.

### 8.4 Primary versus removable-storage policy

| Storage state | Path update | Missing-row policy |
| --- | --- | --- |
| Primary storage mounted | Reconcile matching MediaStore ids in place | Delete only after a complete MediaStore pass and `File.exists() == false`. |
| Removable storage mounted | Reconcile matching MediaStore ids in place | Never delete unmatched rows during reconciliation; use hide/unhide lifecycle. |
| Removable storage unmounted | Do not reconcile | Hide and retain; never infer deletion from absence. |

The removable policy is stricter because `File.exists() == false` is expected while
a drive is disconnected. Introducing removal into that path would turn an ordinary
unplug into destructive metadata cleanup.

### 8.5 Stable-id move reconciliation (implemented)

When MediaStore preserves `_id` and only `_data` changes, reconciliation updates
`files_import` and `files` in one provider batch. This preserves Nova's file id,
scraper rows, poster/backdrop choices, bookmarks, and playback state. It covers:

- moves on primary storage observed by the same Android device; and
- moves on a mounted SD/USB volume when that device's MediaStore preserves the id.

The raw `files` provider normally strips `_data` updates. Import reconciliation uses
an internal marker that is honored only for calls from Nova's own UID, then removed
before the SQL update. Do not weaken this boundary: `VideoProvider` is exported.

### 8.6 Offline same-volume moves with a new MediaStore id

Moving a file while its USB/SD volume is attached to a PC or another Android device
is not an observed rename from the TV's perspective. MediaStore state is local to
each Android device and is not stored on the removable filesystem. When the volume
returns, the TV may expose the new path under a new MediaStore id and later remove
the old id. The pre-copy new-id reconciliation phase associates conservative,
same-volume matches before normal import can create a replacement Nova row.

Moving a drive with a phone file manager has the same limitation when Nova runs on
the TV: updating the phone's MediaStore does not update the TV's MediaStore. If the
file manager and Nova run on the same device while the volume remains mounted, the
implemented stable-id path may apply.

The implementation matches a unique normalized filename and exact size with
modification time within three seconds. It deliberately does not reconcile moves
between different volumes, renamed files, ambiguous matches, or replacement files
whose size or timestamp changed.

Do not validate issue #1759 solely with a stable-id test. Validate both:

- mounted same-device folder move; and
- offline move followed by unmount/remount and a newly assigned MediaStore id.

### 8.7 New-id reconciliation design and constraints

This must be a separate **pre-copy reconciliation phase**, not an extension of the
current post-copy stable-id pass. Running before `copyData` is a correctness
requirement: `files.remote_id` is unique with `ON CONFLICT IGNORE`, so once the new
row has been inserted, an attempted old-to-new remap can be silently ignored.

The implemented phase uses conservative identity matching:

1. For each mounted volume, collect old imported rows whose id is absent from the
   current MediaStore result and whose old path no longer exists.
2. Collect new MediaStore rows. Normally their ids are absent from `files_import`;
   an already imported destination is considered only for guarded repair.
3. Match only an unambiguous one-to-one pair on the same volume. An initial identity
   may use normalized filename, exact size, and modification time within three
   seconds, matching the scanner's existing FAT/exFAT tolerance. Ambiguous matches
   must fall back to normal import without metadata transfer.
4. Require the destination id to be free, or prove that an existing destination is
   unscraped and carries no bookmark, playback, favorite, Trakt, hidden, custom
   title, movie, or episode state. Provider operations require expected update
   counts so `ON CONFLICT IGNORE` cannot masquerade as success.
5. In one transaction with foreign-key enforcement active:
   - update the existing `files` row's `_id`, `remote_id`, path, and imported columns
     to the new MediaStore identity;
   - let movie/episode references cascade through `remote_id`;
   - let subtitle references cascade through `_id`;
   - manually update `videothumbnails.video_id`, which has no foreign key; and
   - update `files_import._id`, path, and imported columns to the same identity.
   Bookmarks, playback state, Trakt state, and scraper ids remain on the updated
   `files` row.
6. Run `copyData` only after this remapping, so it sees the new id as imported.

The runtime database enables `PRAGMA foreign_keys = ON` in `VideoOpenHelper.onOpen`.
Real-database tests verify that setting on the provider connection and run
`PRAGMA foreign_key_check` after remapping. An interrupt or failed operation must
roll back the entire remap.

Additional safety rules:

- the old volume must be mounted and the old path confirmed absent;
- never interpret an unmounted source volume as proof of a move;
- never transfer state when multiple old or new rows share the candidate identity;
- never delete unmatched removable rows;
- do not apply move reconciliation to a replacement file whose size or identity
  changed; replacing an inferior copy is a separate workflow;
- repairing an already duplicated old/new pair must not delete a newly scraped row
  or trigger artwork cleanup without first deciding which row owns valid metadata.

Repair is not necessarily forward-only. A previously affected removable file can be
recovered while its old scraped row is still retained and hidden: if the destination
row is demonstrably unscraped and the old/new identity match is unique, delete the
unscraped duplicate and remap the retained row in one transaction. If cleanup has
already deleted the old movie/episode/artwork rows, there is nothing left to
preserve and the entry must be scraped again. If both rows contain meaningful user
or scraper state, skip automatic repair.

Filename/size/mtime matching covers common same-volume offline folder moves but not
all renamed/copied files. A durable content signature (for example, size plus hashes
of bounded chunks from the beginning and end of the file) is the stronger long-term
identity. It requires schema migration, backfill, and I/O-cost analysis before use.

### 8.8 Local-import invariants

- Absence of a removable volume never means its files were deleted.
- `files._id`, `files.remote_id`, and `files_import._id` remain equal for imported
  rows; a new-id remap updates all three atomically.
- Primary cleanup requires both a complete MediaStore pass and filesystem absence.
- Removable reconciliation updates known moves but does not delete unmatched rows.
- Import interruption or query failure must disable cleanup for that pass.
- Path conflicts and ambiguous identity matches are logged and left untouched.
- Full and incremental imports must enforce identical reconciliation guarantees.

### 8.9 Validation and diagnostics

`VideoStoreImportReconciliationTest` covers stable-id primary and removable moves,
primary deletion after confirmed absence, retention during MediaStore lag, the
no-delete removable guarantee, caller authorization, and ambiguous new-id matching.

`VideoStoreIdRemapDatabaseTest` uses a real Robolectric SQLite database and the
provider transaction path to cover:

- unique old/new match updating all three ids while preserving the `files` row's
  scraper, playback, bookmark, and Trakt values;
- movie/episode `video_id` and subtitle ids following their foreign-key cascades;
- `videothumbnails.video_id` being updated explicitly;
- volume hide/unhide still reaching the remapped row;
- ambiguous duplicate identity producing no remap;
- transaction rollback leaving both identities consistent;
- repair of a retained scraped source plus an already inserted unscraped destination;
- refusal to replace a destination that already carries user state;
- `PRAGMA foreign_key_check` returning no rows after each scenario;

Mounted-volume discovery and `File.exists()` remain device integration behavior;
validate an old path that still exists, an unmounted source volume, and an actual
unmount/offline-move/remount cycle on hardware. Migration from every supported
database version is required if a content-signature column is introduced later.

Import summaries log inserted (`+`), reconciled (`~`), and removed (`-`) counts.
Device validation should capture MediaStore `_id`/`_data` and Nova
`files._id`/`remote_id`/`_data` before and after each move; a path-only log cannot
distinguish stable-id from new-id behavior.

## 9. Cross-references

- `TEST.md` → "Network scanner service", "Video provider transactions",
  "Auto-scrape network scan coordination" — the regression tests for sections 2–5.
- `DATABASE_UPDATE.md` → schema/migration rules for the tables these scans write to.
- `DATABASE_RUNTIME.md` → connection ownership, replacement locking, and provider
  transaction rules.
- `SCRAPE.md` → what autoscrape does once it starts (movie/TV matching).
