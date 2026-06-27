# Network scan & autoscrape coordination (spec)

How a network (remote) re-index runs end to end: from the periodic refresh
trigger, through per-folder scanning and database writes, to the single
autoscrape that fires once the whole batch finishes cleanly. This documents the
runtime contract enforced by the indexing write-path — not the schema
(see `DATABASE_UPDATE.md`) nor the scrape matching heuristics (see `SCRAPE.md`).

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

## 8. Cross-references

- `TEST.md` → "Network scanner service", "Video provider transactions",
  "Auto-scrape network scan coordination" — the regression tests for sections 2–5.
- `DATABASE_UPDATE.md` → schema/migration rules for the tables these scans write to.
- `DATABASE_RUNTIME.md` → connection ownership, replacement locking, and provider
  transaction rules.
- `SCRAPE.md` → what autoscrape does once it starts (movie/TV matching).
