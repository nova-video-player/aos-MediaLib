# MediaLib test guide

Run MediaLib tests from the `Video` repository root. `MediaLib` is included as
the `:MediaLib` Gradle module and does not contain its own Gradle wrapper.

## Full unit test suite

This is the required command before committing a MediaLib change:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest
```

`--offline` prevents Gradle from silently changing dependency resolution. It
requires the declared dependencies to already exist in the local Gradle cache.
It does not block network access from test code: `ScraperIntegrationTest`
performs live TMDB searches. Remove `--offline` only when intentionally
resolving new dependencies.

HTML report:

```text
MediaLib/build/reports/tests/testDebugUnitTest/index.html
```

JUnit XML results:

```text
MediaLib/build/test-results/testDebugUnitTest/
```

## Running one class or test

Run one complete test class with:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'fully.qualified.TestClass'
```

Run one test method with:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'fully.qualified.TestClass.testMethod'
```

## Existing tests

### Track naming

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediacenter.utils.TrackNamingTest'
```

- `com.archos.mediacenter.utils.TrackNamingTest.testTrackNaming`

Uses `test/resources/track_naming_tests.csv`. The helper script
`test/tools/ffprobe_track_naming_to_csv.sh` can generate compatible fixture
rows from media files.

### Database holder

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediaprovider.DbHolderTest'
```

- `com.archos.mediaprovider.DbHolderTest.testGetAndCaching`
- `com.archos.mediaprovider.DbHolderTest.testRecoverWhenClosedUnderneath`
- `com.archos.mediaprovider.DbHolderTest.testRecoverFromIllegalStateException`
- `com.archos.mediaprovider.DbHolderTest.testLockExclusiveClosesAndReopens`
- `com.archos.mediaprovider.DbHolderTest.testGetBlocksDuringExclusiveLock`
- `com.archos.mediaprovider.DbHolderTest.testExplicitClose`

### Database migrations

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediaprovider.video.DatabaseMigrationTest'
```

- `com.archos.mediaprovider.video.DatabaseMigrationTest.testUpgradeFromEverySupportedVersion`
- `com.archos.mediaprovider.video.DatabaseMigrationTest.testCreateAtVersion50StopsBeforeV51`
- `com.archos.mediaprovider.video.DatabaseMigrationTest.testMigrationV43RecreatesScannerTriggers`
- `com.archos.mediaprovider.video.DatabaseMigrationTest.testMigrationV51RebuildsArtworkTablesAndRestoresVideoView`
- `com.archos.mediaprovider.video.DatabaseMigrationTest.testFreshCreateCurrentSchema`
- `com.archos.mediaprovider.video.DatabaseMigrationTest.testMigrationV55`

These tests use Robolectric's SQLite implementation. They verify migration
structure and resulting schemas, but they do not reproduce every SQLite version
shipped by old Android releases. Migration changes must also be validated on a
representative old Android device when compatibility behavior is involved. See
`doc/DATABASE_UPDATE.md` for the complete migration procedure.

### Network scanner service

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediaprovider.video.NetworkScannerServiceVideoTest'
```

- `com.archos.mediaprovider.video.NetworkScannerServiceVideoTest.duplicateQueuedRequestReleasesItsBatchSlot`
- `com.archos.mediaprovider.video.NetworkScannerServiceVideoTest.uncheckedScanFailureStillCompletesBatch`

These tests exercise service request and completion accounting. They do not
perform a real network scan.

### Video provider transactions

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediaprovider.video.VideoProviderTransactionTest'
```

- `com.archos.mediaprovider.video.VideoProviderTransactionTest.applyBatchPreservesOperationFailureWhenRollbackAlsoFails`
- `com.archos.mediaprovider.video.VideoProviderTransactionTest.applyBatchPropagatesCommitFailureWithoutNotifying`

These tests inject mocked SQLite failures. They verify exception propagation,
balanced transaction callbacks, and notification suppression without requiring
a real full-disk condition.

### Auto-scrape network scan coordination

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediascraper.AutoScrapeServiceNetworkScanTest'
```

- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.completionHandlerStartsForcedScrapeForCleanSuccessfulOwner`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.completionHandlerDoesNotStartScrapeForIneligibleOutcomes`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.standaloneScanCompletesImmediately`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.standaloneErrorDoesNotLeakIntoNextScan`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.batchCompletesOnlyAfterLastScanAndAggregatesErrors`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.resetClearsCountAndPendingBatchError`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.startRegistersAllMembersAtomically`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.overlappingBatchIsRejectedAndDoesNotResetActiveBatch`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.rejectedDuplicateMemberReleasesBatchSlot`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.standaloneCompletionWhileBatchActiveDoesNotTouchBatch`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.unresolvedFinalMemberStillReportsBatchSuccess`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.errorAggregationIsolatedByBatchId`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.staleCompletionFromOlderBatchIsIgnored`
- `com.archos.mediascraper.AutoScrapeServiceNetworkScanTest.concurrentCompletionHasSingleOwnerAndAggregatesError`

These tests exercise batch accounting and Robolectric service-start intents.
They do not perform real scraping or database insertion.

### Auto-scrape persistence

Class commands:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediascraper.AutoScrapeServicePersistenceTest' \
  --tests 'com.archos.mediascraper.EpisodeTagsPersistenceTest' \
  --tests 'com.archos.mediascraper.ShowTagsPersistenceTest' \
  --tests 'com.archos.mediaprovider.video.ScraperProviderInsertTest'
```

These tests cover single scrape-worker ownership, the bounded persistence
retry limit, retryable save failures, parent-show failure propagation,
concurrent show insertion recovery, and idempotent show/collection inserts.
They do not inject a real mid-scrape SQLite failure; the retry-round tests
exercise the deterministic coordination and state decisions directly.

### Movie filename preprocessing

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediascraper.preprocess.MovieScraperFileTest'
```

- `com.archos.mediascraper.preprocess.MovieScraperFileTest.testMoviesFromResourceFile`

Uses `test/resources/movie_test_cases.csv`.

### Sort title extraction ("Title, Article")

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediascraper.preprocess.SortTitleUtilsTest'
```

- `com.archos.mediascraper.preprocess.SortTitleUtilsTest.testSortTitlesFromResourceFile`
- `com.archos.mediascraper.preprocess.SortTitleUtilsTest.testWhitespaceAndEmpty`

Uses `test/resources/sort_title_test_cases.csv` to verify language-specific leading article inversion (e.g. `The Matrix` -> `Matrix, The` for `en`, `L'Auberge Espagnole` -> `Auberge Espagnole, L'` for `fr`), hybrid English franchise titles (`The Amazing Spider-Man : Le Destin d'un héros` -> `Amazing Spider-Man : Le Destin d'un héros, The`), and undetermined/unknown language fallback (`und` or empty language falls back to system locale then English).

#### Expanding Sort Title Tests

To add new sort title test cases, append rows to `MediaLib/test/resources/sort_title_test_cases.csv` following the format:

```text
Title|Language|ExpectedSortTitle
```

Examples:
```csv
The Big Lebowski|en|Big Lebowski, The
La Grande Illusion|fr|Grande Illusion, La
Das Boot|de|Boot, Das
The Matrix|und|Matrix, The
```

### Scraper integration

Class command:

```bash
./gradlew --offline :MediaLib:testDebugUnitTest \
  --tests 'com.archos.mediascraper.preprocess.ScraperIntegrationTest'
```

- `com.archos.mediascraper.preprocess.ScraperIntegrationTest.testScrapingFromCsv`

Uses `test/resources/scraper_test_cases.csv` as its input and expected-result
list, but performs live TMDB searches. It therefore requires working network
access and can fail because of API availability or changing upstream search
results. Review the CSV when changing scraper matching behavior.

## Required checks by change type

- Any MediaLib change: run the full suite.
- Database or provider change: run `DatabaseMigrationTest`, `DbHolderTest`, and
  `VideoProviderTransactionTest` before the full suite.
- Network scanner or auto-scrape change: run `NetworkScannerServiceVideoTest`,
  `AutoScrapeServiceNetworkScanTest`, and the auto-scrape persistence classes
  before the full suite.
- Filename parsing or scraper change: run `MovieScraperFileTest`,
  `ScraperIntegrationTest`, and `TrackNamingTest` as applicable, then the full
  suite.

Do not treat Robolectric as proof of behavior specific to old platform SQLite,
real storage exhaustion, process death, or actual network servers. Keep the unit
tests as regression coverage and add device validation for those conditions.
