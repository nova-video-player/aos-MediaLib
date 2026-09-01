# Media database runtime contract

This document covers runtime ownership and replacement of `media.db`, plus the
transaction behavior of provider writes. Schema evolution belongs in
`DATABASE_UPDATE.md`; network scan orchestration belongs in `SCANNING.md`.

The rules here exist because providers, scanners, autoscrape, playback state, and
backup/restore all share one process-wide database connection holder. Closing or
replacing the file without coordinating that holder can reopen a deleted or
partially restored database and produce closed-handle, transaction, or corruption
errors.

Relevant files:

- `src/com/archos/mediaprovider/VideoDb.java` - process-wide `DbHolder` owner.
- `src/com/archos/mediaprovider/DbHolder.java` - cached connection and replacement
  lock.
- `src/com/archos/mediaprovider/video/VideoOpenHelper.java` - database creation,
  upgrade, WAL, and foreign-key configuration.
- `src/com/archos/mediaprovider/video/VideoProvider.java` - primary read/write
  provider and bulk transaction boundary.
- `src/com/archos/mediaprovider/video/ScraperProvider.java` - scraper data access
  through the same holder.
- `Video/src/main/java/com/archos/mediacenter/video/utils/MediaLibraryBackupService.java`
  - destructive replacement caller in the application repository.

This contract is motivated by the restore race reported in issues #1783 and #1785,
and by the transaction cleanup failure tracked as NOVA-VIDEO-PLAYER-15A7.

## 1. Ownership

`VideoDb.getHolder(Context)` lazily creates one `DbHolder` for the process. All
MediaLib components that use `media.db` must obtain the database through this shared
holder, directly or through a provider that owns it.

Do not create an independent `VideoOpenHelper` for normal provider work. Independent
helpers have separate cached connection state and cannot participate in destructive
replacement locking.

Do not retain a `SQLiteDatabase` across a logical operation. Call `DbHolder.get()`
when the operation starts, use the returned handle synchronously, and release all
cursors and transactions before returning. A cached handle can become invalid when
backup/restore closes the shared helper or Android recycles the connection.

## 2. Normal open and recovery

`DbHolder.get()` returns the cached database while it is open. If no database is
cached, or the cached handle reports closed, it acquires the holder lock and opens a
fresh writable database through `VideoOpenHelper`.

The open path also recovers from `IllegalStateException` caused by attempting to
reopen a helper whose cached object was closed: it closes/reset the helper and tries
`getWritableDatabase()` again. This is defensive recovery, not a replacement for
proper locking around destructive operations.

`DbHolder.close()` closes the cached database and helper under the lock. It must not
be used as a two-step "close now, replace later" protocol: once `close()` returns,
another thread can call `get()` and reopen `media.db` before deletion or extraction
starts.

## 3. Destructive replacement protocol

Any operation that deletes, renames, overwrites, or extracts over `media.db` must
hold the exclusive holder lock for the **entire destructive window**:

```java
DbHolder holder = VideoDb.getHolder(context);
holder.lockExclusive();
try {
    // Delete/replace media.db and its side files as one controlled operation.
} finally {
    holder.unlockExclusive();
}
```

`lockExclusive()` acquires the holder lock, closes the cached database, clears the
cached reference, and closes the helper. While the lock is held, a thread that needs
to reopen the database blocks in `get()`. After `unlockExclusive()`, the next `get()`
opens the replacement database and runs normal helper validation.

Required rules:

1. Acquire the lock before deleting the first database file.
2. Keep it held through deletion, extraction/copy, and final placement of every
   database file.
3. Pair it with `unlockExclusive()` in `finally`; never return or throw past the
   unlock.
4. Validate backup format and schema version before entering the destructive window
   when possible.
5. Do not call provider methods or `DbHolder.get()` from the thread while it holds
   the replacement lock. The lock is reentrant, but opening or querying a database
   being replaced violates the protocol.
6. Do not replace only the main file while leaving incompatible `-wal` or `-shm`
   state beside it. Treat the database and its SQLite side files as one unit.

### What the lock does not guarantee

The holder lock serializes connection reopening; it is not a read/write lock around
every SQL statement. A thread that obtained the open database before
`lockExclusive()` may already be executing. The destructive caller must therefore
also use its higher-level service/application controls to avoid starting replacement
while imports, scans, or other long-running writes are active.

The lock's essential guarantee is narrower: after it closes and clears the cached
connection, new `get()` calls cannot reopen `media.db` until replacement is complete.

## 4. Backup and restore

Export and import have different requirements:

- Export must checkpoint/flush WAL state before copying database content so the
  archive contains a consistent database.
- Import must validate the recorded database version against
  `VideoOpenHelper.getDatabaseVersion()` before replacement.
- Import must hold `lockExclusive()` across cleanup and extraction, not merely while
  closing the old connection.
- Once unlocked, consumers must obtain a fresh handle from `DbHolder`; no pre-import
  `SQLiteDatabase` or cursor remains valid.

The application-level backup service lives in the `Video` repository, but its
locking contract is owned by MediaLib because MediaLib owns the shared holder.

## 5. Provider write transactions

`VideoProvider.bulkInsert()` and `VideoProvider.applyBatch()` each create one
non-exclusive SQLite transaction per invocation. A network folder scan can invoke
these methods multiple times, so the whole folder scan is not atomic. A later batch
failure does not undo earlier provider calls that already committed.

The required transaction order is:

1. Obtain the current handle from `DbHolder`.
2. Call `beginTransactionNonExclusive()`.
3. Enter `VobHandler` transaction mode.
4. Apply all operations.
5. Call `setTransactionSuccessful()` and mark that a commit was requested.
6. Call `endTransaction()`.
7. Always leave `VobHandler` transaction mode in a nested `finally`.
8. Notify content observers only after `endTransaction()` returns successfully.

Starting the SQLite transaction before `VobHandler.onBeginTransaction()` prevents a
failed transaction begin from leaving VOB processing permanently deferred.

## 6. Rollback and commit failures

Fatal SQLite statement errors such as full disk, I/O failure, interruption, or
corruption can auto-rollback the native transaction. Android's Java transaction
state may still call `endTransaction()`, which then throws `cannot rollback - no
transaction is active` and masks the original operation failure.

Provider bulk paths distinguish cleanup from commit using `commitRequested`:

- Before `setTransactionSuccessful()`, an `endTransaction()` `SQLiteException` is a
  secondary rollback failure. It is logged and suppressed so the original operation
  exception remains visible.
- After `setTransactionSuccessful()`, `endTransaction()` performs the commit. Its
  exception must propagate; callers must not report success or emit notifications for
  data that did not commit.

Do not add an `inTransaction()` guard as a substitute. Android's Java-side state can
still report an active transaction after SQLite has already auto-rolled back it.

## 7. Failure handling ownership

The provider preserves transaction truth: operation and commit failures propagate,
and notifications are emitted only for committed writes. The caller decides whether
a particular failure is expected and recoverable.

For network scans:

- `SQLiteFullException` and `SQLiteDiskIOException` abort the current scan cleanly
  and suppress post-batch autoscrape.
- Other `SQLiteException` instances are captured in Sentry before the scan aborts.
- Non-SQLite runtime defects still propagate after scan-batch accounting is released.

Do not catch broad `RuntimeException` in low-level executors merely to keep scanning.
That can hide deterministic provider/schema defects and continue with a partial
index. Handle known storage failures at the workflow boundary and preserve
observability for unexpected failures.

## 8. Tests and device validation

Run from the `Video` repository root:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediaprovider.DbHolderTest' \
  --tests 'com.archos.mediaprovider.video.VideoProviderTransactionTest'
```

Then run the full MediaLib suite documented in `TEST.md`.

These tests verify cached-handle recovery, exclusive-lock blocking, balanced provider
transactions, exception preservation, commit propagation, and notification
suppression. They do not reproduce process death, a real full filesystem, or every
SQLite implementation shipped by Android. Validate destructive restore and old
platform behavior on representative devices.

## 9. Review checklist

Before merging a runtime database change, verify:

- all normal access uses the shared `VideoDb` holder;
- no database or cursor handle survives a replacement boundary;
- destructive file work is fully enclosed by `lockExclusive()`/`unlockExclusive()`;
- version and archive validation happen before destructive work;
- provider commit failures propagate and do not notify observers;
- rollback cleanup cannot replace the original operation exception;
- workflow-level storage failures remain observable and do not start more DB work;
- focused tests and the complete MediaLib suite pass;
- device validation covers any old-SQLite or real-filesystem assumption.
