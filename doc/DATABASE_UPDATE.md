# Database migration guide (VideoOpenHelper / ScraperTables)

Audience: developers changing the Nova media database schema. Read this fully
before adding a column, table, index, view or trigger. Migrations run on every
device in the field, on SQLite versions you cannot choose. A single incompatible
DDL statement can prevent the database from opening on every launch and may
eventually cause recovery code to delete `media.db`. Backward compatibility
with every released schema version is mandatory.

For shared connection ownership, provider transaction behavior, and destructive
backup/restore locking, see `DATABASE_RUNTIME.md`.

Relevant files:

- `MediaLib/src/com/archos/mediaprovider/video/VideoOpenHelper.java` — schema
  owner, `onCreate`, `onUpgrade`, version constants, the `video` view and all
  `files`/`smb`/`subtitles`/thumbnail objects.
- `MediaLib/src/com/archos/mediaprovider/video/ScraperTables.java` — scraper
  tables/views/triggers, `create()` and `upgradeTo(db, version)`.
- `MediaLib/src/com/archos/mediaprovider/video/ListTables.java` — user list
  tables (`upgradeTo`).
- `MediaLib/src/com/archos/mediaprovider/SQLiteUtils.java` — safe drop/replace
  helpers (`dropView`, `dropTrigger`, `dropTriggersCompat`,
  `replaceTriggersCompat`, `alterTable`).
- `MediaLib/test/java/com/archos/mediaprovider/video/DatabaseMigrationTest.java`
  — Robolectric migration tests.

## 1. How the lifecycle actually works

There are two version constants in `VideoOpenHelper`:

```java
private static final int DATABASE_CREATE_VERSION = 36; // the frozen base schema
private static final int DATABASE_VERSION        = 59; // the current schema
```

`onCreate()` builds the **frozen version-36 base schema** and then immediately
replays every migration up to the target:

```java
// VideoOpenHelper.onCreate(), last line
onUpgrade(db, DATABASE_CREATE_VERSION, mTargetVersion);
```

Consequences you must internalize:

- **A fresh install is not a shortcut.** A brand-new database is created at
  version 36 and then runs migrations 37 → current. Every migration bug is also
  a fresh-install bug. Issue #1787 was a fresh-install crash caused by the v55
  migration, not by an in-field upgrade.
- **You normally do NOT edit `onCreate()`.** Because `onCreate` replays through
  `onUpgrade`, adding a properly gated migration block automatically covers both
  fresh installs and field upgrades. Do not also hand-edit the base schema.
- **Never modify the version-36 base SQL** (`CREATE_*_V32`, `ScraperTables.create`
  as it stands, etc.). Those statements describe what a v36 database looked like
  and are the starting point of the replay. Changing them desynchronizes fresh
  installs from upgraded installs. Express all changes as migration steps.
- **Downgrades delete the database.** `VideoOpenHelper` extends
  `DeleteOnDowngradeSQLiteOpenHelper`, and `onUpgrade` calls `deleteDatabase()`
  for `oldVersion < DATABASE_CREATE_VERSION` (anything older than 36). Pre-36
  Archos databases are intentionally not migrated.
- **`mTargetVersion`** lets tests build a database that stops at an intermediate
  version. Production always targets `DATABASE_VERSION`.

`onOpen()` enables WAL and `PRAGMA foreign_keys = ON`, but it runs **after**
creation or upgrade. Do not assume foreign-key cascades are active inside
`onUpgrade()`; migration 43 explicitly propagates related ids for this reason.
Update dependent rows yourself where required, then verify the result with
`PRAGMA foreign_key_check`. Foreign keys are ON during normal production use,
so a migration that leaves violations may fail later even if the upgrade itself
completed.

## 2. The golden rules

1. Every migration step is gated by **both** bounds:
   `if (oldVersion < N && newVersion >= N) { ... }`. Gating on `oldVersion`
   alone makes intermediate-target upgrades (and tests) run steps they should
   not.
2. Migration steps are **append-only and normally immutable once released.**
   Add new schema changes in a new version. The exception is a compatibility
   defect that prevents a field database from completing an old step. Such a
   database cannot reach a later repair migration, so the failing historical
   step itself must be made compatible while preserving its intended resulting
   schema. The v51 dependent-view fix and v55 trigger fix are examples. Keep
   these corrections narrowly scoped and add regression coverage.
3. Each step must be **idempotent-safe under retry.** SQLiteOpenHelper wraps the
   upgrade in a transaction, but a platform error handler can delete the file
   and retry from scratch, so prefer `IF EXISTS` / `IF NOT EXISTS` on drops and
   helper objects.
4. **Schema-object replacement (triggers, views, table rebuilds) is the
   dangerous part.** Use the helpers in section 4; do not hand-roll
   `DROP TRIGGER` + `CREATE TRIGGER`.
5. **Bump `DATABASE_VERSION` last**, after the migration block and tests exist.

## 3. Step-by-step: adding migration N

Assume current `DATABASE_VERSION` is `M` and you are adding `N = M + 1`.

### 3a. Pure column add (simplest, preferred)

`ALTER TABLE ... ADD COLUMN` is the safest DDL across all SQLite versions.

- If the column is on a **scraper table**, add it inside
  `ScraperTables.upgradeTo(db, N)` under a `if (toVersion == N)` block.
- If the column is on a **VideoOpenHelper-owned table** (e.g. `files`), add it
  inline in the `onUpgrade` block.

```java
// VideoOpenHelper.onUpgrade()
if (oldVersion < N && newVersion >= N) {
    if (log.isDebugEnabled()) log.debug("onUpgrade: {} - <reason>", N);
    db.execSQL("ALTER TABLE " + FILES_TABLE_NAME + " ADD COLUMN new_col TEXT DEFAULT (NULL)");
}
```

Choose nullability and the default explicitly. A new `NOT NULL` column needs a
compatible non-null default on SQLite versions in the field. A nullable column
may intentionally default to `NULL`; test how existing rows are interpreted.

### 3b. Column that the `video` view must expose

The `video` view is versioned (`CREATE_VIDEO_VIEW_V32/V37/V38/V41/V49/V50`).
When a migration changes columns the view selects, define a **new**
`CREATE_VIDEO_VIEW_Vn` constant and recreate the view inside the step. Follow
the existing pattern (migrations 49 and 50):

```java
if (oldVersion < N && newVersion >= N) {
    db.execSQL("ALTER TABLE " + FILES_TABLE_NAME + " ADD COLUMN new_col TEXT DEFAULT (NULL)");
    SQLiteUtils.dropView(db, VIDEO_VIEW_NAME);
    db.execSQL(CREATE_VIDEO_VIEW_Vn);   // new constant
}
```

Keep old `CREATE_VIDEO_VIEW_V*` constants — earlier steps still reference them.
Add a new one rather than mutating an existing one.

### 3c. New scraper table / view / trigger

Put the `CREATE` statements in `ScraperTables` and execute them from
`ScraperTables.upgradeTo(db, N)`. Mirror the object into `ScraperTables.create()`
**only if** you intentionally raise `DATABASE_CREATE_VERSION` (you almost never
should — see section 7). Otherwise the replay path adds it.

### 3d. Trigger change — use `replaceTriggersCompat`

Old Android SQLite can leave a trigger row in `sqlite_master` after an ordinary
`DROP TRIGGER`, so the following `CREATE TRIGGER` fails with
`trigger <name> already exists` / `malformed database schema`. This caused
issues #469 (v39) and #1787 (v55). Always replace triggers with:

```java
SQLiteUtils.replaceTriggersCompat(db,
        new String[] { "episode_delete", "show_delete", "movie_delete" },
        EPISODE_DELETE_TRIGGER_CREATE_v2,
        SHOW_DELETE_TRIGGER_CREATE_v2,
        MOVIE_DELETE_TRIGGER_CREATE_v2);   // names[] aligns 1:1 with create statements
```

If you only need to drop triggers (because you recreate them differently), use
`SQLiteUtils.dropTriggersCompat(db, names...)`. Both helpers do ordinary drops
(to clear the connection's in-memory schema cache) **and** delete leftover
`sqlite_master` rows under `PRAGMA writable_schema = ON`, restoring it in a
`finally`. The ordinary drop alone is not enough; the row delete alone is not
enough. You need both.

### 3e. Table rebuild (new constraint / changed column type)

SQLite cannot add a `UNIQUE` constraint or change a column type in place. The
rebuild pattern is: create `_new` table → `INSERT ... SELECT` → `DROP TABLE` →
`ALTER TABLE _new RENAME TO original` → recreate indexes. This is what migration
51 does for `movie_posters` / `movie_backdrops`.

**Critical pitfall (Sentry issue 7263272934):** on some
SQLite versions `ALTER TABLE ... RENAME` revalidates **every view** in the
schema. While the rebuilt table is dropped, any view referencing it is
temporarily broken, and the rename aborts with
`error in view video: no such table: main.movie_posters`. **Drop dependent views
before the rebuild and recreate them after:**

```java
if (oldVersion < N && newVersion >= N) {
    SQLiteUtils.dropView(db, VIDEO_VIEW_NAME);   // any view referencing the table
    ScraperTables.upgradeTo(db, N);              // does the _new/RENAME rebuild
    db.execSQL(CREATE_VIDEO_VIEW_V50);           // the view definition current at N
}
```

When recreating, use the view constant that is **current at version N**, not the
newest one — at version 51 the current view is V50, since the view last changed
at migration 50. Verify with `grep -n "CREATE_VIDEO_VIEW_V" VideoOpenHelper.java`
and check which migration last recreated it. Before a rebuild, audit **all**
views (`grep CREATE VIEW` in both files) for references to the table — not just
`video`.

`SQLiteUtils.alterTable()` is a convenience that does rename → recreate →
copy-all → drop in a transaction, but it does **not** handle the dependent-view
problem; if a view references the table, drop/recreate the view yourself around
the call.

### 3f. Data migration (UPDATE / dedup / id rewrite)

- `INSERT OR IGNORE INTO _new SELECT * FROM old` silently drops a **whole** row
  on any unique conflict. If a row conflicts on one unique column but carries
  distinct data in another, you lose that data. Decide explicitly which row
  wins; if both associations matter, dedup with explicit SQL, not row-level
  `OR IGNORE` (migration 51 finding).
- `WHERE x NOT IN (SELECT ...)` returns no rows if the subquery yields a `NULL`
  (three-valued logic). Use `NOT EXISTS` when nullability is uncertain
  (migration 43 finding).
- Primary-key rewrites (e.g. migration 43 adds 1,000,000,000 to ids) can collide
  with existing destination ids and abort the transaction. Prove the destination
  range is free before updating.

### 3g. Bump the version

Only after the block and tests exist:

```java
private static final int DATABASE_VERSION = N;
```

`getDatabaseVersion()` is consumed by the backup/restore services to validate
compatibility — bumping it is what makes the new schema "current".

## 4. SQLiteUtils helper cheat-sheet

| Need | Use | Why not raw SQL |
| --- | --- | --- |
| Drop a view | `dropView` | wraps `DROP VIEW IF EXISTS` |
| Drop a table | `dropTable` | wraps `DROP TABLE IF EXISTS` |
| Drop trigger(s) safely | `dropTriggersCompat` | also clears stale `sqlite_master` rows |
| Replace trigger(s) | `replaceTriggersCompat` | drop-compat + create, length-checked |
| Rename-rebuild a table | `alterTable` (+ handle views yourself) | transactional copy |

Never leave `PRAGMA writable_schema = ON` un-restored — the helpers already wrap
it in `finally`. If you ever write `writable_schema = ON` by hand, restore it in
`finally` too, or the connection bypasses schema validation until closed.

## 5. SQLite version vs Android version — the real warning

The bundled SQLite library ships with the OS image; it is **not** chosen by Nova
and differs across Android versions and even OEM builds. Roughly:

| Android | API | Approx bundled SQLite |
| --- | --- | --- |
| 6.0    | 23  | ~3.8.10 |
| 8.0    | 26  | ~3.18 |
| 9.0    | 28  | ~3.22 |
| 11     | 30  | ~3.28 |
| 14     | 34  | ~3.39+ |

Old and OEM-customized SQLite builds (common on TV boxes) expose many migration
problems, but newer versions can also enforce schema rules that older versions
did not. For example, the v51 dependent-view failure was observed on Android
11. Do not classify a migration as safe based only on the Android API level.

Known compatibility failures include:

- Stale trigger rows remaining after `DROP TRIGGER` (#469, #1787).
- Full-schema/view validation during `ALTER TABLE RENAME` while a referenced
  table is temporarily absent (Sentry issue 7263272934).
- Behavior depending on OEM SQLite patches and existing `sqlite_master` state,
  so devices on the same Android version may behave differently.

The table is only a rough orientation. OEMs can backport, patch or replace
SQLite independently of the Android release. When investigating a device,
record the actual engine version with `SELECT sqlite_version()` from the app's
database connection. Do not rely solely on `Build.VERSION.SDK_INT`.

**Warning:** Robolectric does not reproduce every platform SQLite behavior.
SDK selection in `@Config` changes Android framework shadows, but it is not a
guarantee that the exact SQLite engine from that Android release is being used.
Tests can therefore pass DDL that fails on a physical Android 6, 9 or 11 device.
Unit tests prove that the SQL and expected schema are internally consistent;
they do **not** prove cross-version SQLite compatibility. The decisive check for
trigger/view/table-rebuild changes is a representative physical device or
emulator for each important Android generation, tested both with cleared app
storage and with a field database.

## 6. Tests to run

The migration suite lives in `DatabaseMigrationTest`. It uses a thin subclass of
`VideoOpenHelper` exposing the `(context, name, version)` constructor so a test
can target an intermediate `mTargetVersion`.

Existing coverage you must keep green:

- `testFreshCreateCurrentSchema` — create at current version, assert
  `PRAGMA integrity_check = ok` and `writable_schema = 0`.
- `testUpgradeFromEverySupportedVersion` — for every base 36 → 54, create at that
  version then upgrade to current; assert integrity, expected triggers,
  `writable_schema = 0`.
- `testCreateAtVersion50StopsBeforeV51` — proves `mTargetVersion` gating works
  (a v50 target must not run the v51 step).
- `testMigrationV43RecreatesScannerTriggers`,
  `testMigrationV51RebuildsArtworkTablesAndRestoresVideoView`,
  `testMigrationV55` — focused tests for the dangerous schema-object steps.

When you add migration N, add a focused test that:

1. Builds a populated database at version `N-1` (insert representative rows,
   including the conflict/null/edge cases your step handles).
2. Upgrades to `N`.
3. Asserts the new schema (column/table/index/trigger/view exists; trigger or
   view SQL contains the expected text; `UNIQUE` present if you added it).
4. Asserts data preservation / dedup outcome explicitly (row counts, winning
   row).
5. Asserts `PRAGMA integrity_check = ok`, and `PRAGMA foreign_key_check` is
   empty.
6. Asserts `getWritableSchema(db) == 0` (writable_schema restored).

The every-version test creates intermediate schemas by replaying the migration
code in the current checkout. It is valuable, but it is **not an authentic
field-database test**: a released build may have produced different SQL,
trigger text, data anomalies or `sqlite_master` state before a historical step
was corrected. For risky changes, maintain fixture databases created by the
actual released APK/code at representative versions. Populate them with local,
SMB/UPnP, movie, episode, subtitle and artwork rows, then open copies with the
new helper and verify schema and data. Never mutate the fixture in place.

For every migration, compare these paths:

1. Fresh creation with cleared storage: frozen v36 base replayed to current.
2. Synthetic upgrade from every supported version to current.
3. Focused populated upgrade from `N-1` to `N` and from `N-1` to current.
4. Authentic fixture upgrades from representative released versions.
5. Retry after an intentionally failed/interrupted upgrade where practical.

Compare normalized `sqlite_master` objects between fresh and upgraded databases,
then run `PRAGMA integrity_check` and `PRAGMA foreign_key_check`. A matching
database version number alone does not prove matching schemas.

Run order while developing:

```bash
# from repo root (the root gradle project is Video)
cd Video

# 1. compile MediaLib (catches Java errors in the migration code)
./gradlew :MediaLib:compileDebugJavaWithJavac

# 2. run the migration suite
./gradlew :MediaLib:testDebugUnitTest \
    --tests "com.archos.mediaprovider.video.DatabaseMigrationTest"

# 3. full MediaLib unit tests
./gradlew :MediaLib:testDebugUnitTest

# 4. full app compile (the providers/views must still build)
./gradlew compileNoamazonDebugJavaWithJavac
```

Test results: `MediaLib/build/test-results/testDebugUnitTest/`.

**Then, for any trigger / view / table-rebuild change, validate on real target
devices** (the unit tests cannot): build a debug APK and test both workflows:

1. Clear app storage, launch, and let the full fresh-install replay complete.
2. Install over a build/database from before the migration and launch without
   clearing storage.

Confirm `logcat` shows no `already exists`, `malformed database schema`,
`no such table`, transaction, or corruption messages. Verify that the app opens,
existing library data remains, queries through `video` work, and scanning can
write new rows. Record `SELECT sqlite_version()` for the tested device.

## 7. Backward compatibility — non-negotiable

Every Nova install in the field is at some schema version between 36 and current.
On update, that exact `oldVersion` runs `onUpgrade(oldVersion, current)`. You
must guarantee a correct result for **all** of them, not just the latest.

How this is achieved here:

- **Keep the full migration chain.** Do not delete released steps. Do not
  rewrite their intended result; compatibility corrections to a step are
  allowed only when required to let field databases complete that step.
  The chain from 36 upward is the compatibility contract.
- **Both-bound gating** (`oldVersion < N && newVersion >= N`) makes each step run
  exactly once for exactly the versions that need it.
- **Freeze the base.** `DATABASE_CREATE_VERSION = 36` and the v36 base SQL stay
  fixed so the fresh-install replay and the field-upgrade path converge on the
  same schema. This is the single most important invariant.
- **Test the whole matrix.** `testUpgradeFromEverySupportedVersion` exists
  precisely to prove every supported base still upgrades cleanly. Extend it (or
  its base range) rather than narrowing it.

Optional future optimization (Finding #1 in the migration analysis): create the
current schema directly in `onCreate()` and raise `DATABASE_CREATE_VERSION` so
fresh installs stop replaying history. **Do not do this casually.** It requires
reproducing every column, index, view and trigger added across all migrations
into a single correct creation path, backed by fixture tests for each released
version. Until that exists, the safe, compatible approach is the replay model
described above. If you ever raise `DATABASE_CREATE_VERSION`, every object added
by skipped migrations must be mirrored into the base `onCreate` /
`ScraperTables.create` SQL, and the lower migration steps must remain for devices
still on older versions.

### If the creation model is ever changed

The current model deliberately keeps `onCreate()` at v36 and replays upgrades,
so an ordinary migration must not separately update the frozen base. If a future
change raises `DATABASE_CREATE_VERSION` or creates the current schema directly,
treat creation and upgrade as two independent implementations of the same
schema:

1. Update `VideoOpenHelper.onCreate()`, `ScraperTables.create()` and
   `ListTables` so a fresh database contains every current object.
2. Keep historical `onUpgrade` steps needed by every still-supported field
   version; do not strand users below the new base.
3. Add a schema-equivalence test comparing normalized tables, columns, indexes,
   views and triggers from direct creation against upgrades from every fixture.
4. Test a fresh install, the oldest supported fixture, the immediately previous
   release, and backup/restore compatibility before merging.

Do not raise the creation base merely to make a migration test pass. That can
hide a broken field-upgrade path while abandoning existing users.

## 8. Version 58: original movie metadata

Version 58 adds original-language, original-title and spoken-language columns
to both `movie` and `show` through `ScraperTables.upgradeTo(db, 58)`:

- Newly scraped movies persist TMDb's non-translated ISO 639-1 original-language
  code in `original_language_movie`, such as `en` or `fr`; its non-null default
  is `und` (ISO 639-2/3: undetermined).
- `original_title_*` and comma-separated ISO 639-1 `spoken_languages_*` both
  default to the empty string.
- The migration's `UPDATE`s also assign those defaults to null or blank values,
  including rows from incomplete/external restores.
- The v58 recreation of the `video` view exposes the applicable movie/show value
  as `scraper_original_language`, so playback can use it without a second query.

## 9. Version 59: localized title language

Version 59 adds `title_language_movie` and `title_language_show`. Each contains the ISO 639-1
language of the title/name actually returned by TMDb. It is deliberately distinct from both the
requested scrape language and `original_language_*`: TMDb can fall back to another translation.
Rows created before this information was collected are backfilled to `und`. The `video` view
exposes the result as `scraper_title_language`.
- `belongs_to_collection` remains in the existing normalized `movie_collection`
  table introduced in v38; the scraper now persists its overview as the
  collection description, so no v58 table change is needed for it.
- Do not add these columns to the frozen v36 `MOVIE_TABLE_CREATE`; fresh
  databases receive them when `onCreate()` replays migration 58.

## 9. Pre-commit checklist

- [ ] New step gated `if (oldVersion < N && newVersion >= N)`.
- [ ] No released migration's intended result was changed; any compatibility
      correction to an old step is narrowly scoped and regression-tested.
- [ ] Frozen v36 creation SQL remains untouched unless an explicitly reviewed
      creation-model migration is being performed.
- [ ] Triggers changed only via `replaceTriggersCompat` / `dropTriggersCompat`.
- [ ] Table rebuilds drop **all** referencing views first and recreate the
      version-correct view after.
- [ ] Data steps reviewed for `INSERT OR IGNORE` row loss, `NOT IN` + NULL, and
      id collisions.
- [ ] `DATABASE_VERSION` bumped to `N` (last).
- [ ] Focused migration test added (schema + data + integrity + foreign_key +
      writable_schema assertions).
- [ ] `testUpgradeFromEverySupportedVersion` and the full suite pass.
- [ ] Relevant databases produced by released builds upgrade with data intact.
- [ ] Fresh and upgraded schemas have been compared, not only version numbers.
- [ ] App compiles (`compileNoamazonDebugJavaWithJavac`).
- [ ] Trigger/view/rebuild changes validated on target devices with both cleared
      storage and an upgraded field database (Robolectric is not sufficient).
