# Scraper performance: TMDb query-count measurement

Follow-up to issue [#1923](https://github.com/nova-video-player/aos-AVP/issues/1923): the
year-mismatch-ranking fix for `MovieScraper3.getMatches2()` made movie scraping noticeably
slower for some libraries. This measures why, and how much a follow-up fix (a cascade
short-circuit for the "unified scoring" branch) actually saves in practice, against a real
library rather than the small `scraper_test_cases.csv` corpus.

See `doc/SCRAPE.md` for the cascade design this measures (`getMatches2()` candidate passes,
`isYearConfident()`, unified scoring).

## Why not just use `ScraperIntegrationTest`

`ScraperIntegrationTest` (`test/resources/scraper_test_cases.csv`) is a correctness regression
suite, not a representative sample: it is deliberately weighted toward edge cases and previously
reported bugs. Measuring query counts against it under-represents the "normal" case and gives a
misleading performance picture. The tools below are built to run instead against a real,
unlabeled library filename list (e.g. exported via `find` from an actual library), with no
expected-TMDb-ID column required, since they only measure request counts and coverage, not
top-match correctness.

### Opt-in TMDb cache for `ScraperIntegrationTest`

`ScraperIntegrationTest` itself can optionally replay from a `PerfCacheTmdb`-backed cache too, via
the `NOVA_TEST_CACHE_DIR` env var (unset by default):

```bash
NOVA_TEST_CACHE_DIR=/path/to/persistent/cache \
  ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.preprocess.ScraperIntegrationTest"
```

This is for fast/offline local iteration only - **not** a substitute for the default (env var
unset) behavior, and not meant to be enabled in CI. Unlike the perf tools above,
`ScraperIntegrationTest` is a correctness suite: it exists to catch cases where the scraper's
ranking/cascade logic disagrees with real TMDb data, including real-world data drift (title
changes, new/removed entries affecting title-collision cases). A long-lived cache freezes TMDb's
answers at recording time, so a regression that happens to still match a stale cached response
would go undetected, and legitimate drift would never surface. Only reach for this when you
already trust the cached corpus is representative (e.g. iterating on code changes with no
suspected TMDb-side drift) and want to skip live network calls.

Swaps both `MovieScraper3`'s (`tmdb`, `searchService`, `moviesService`, `collectionService`) and
`ShowScraper4`'s (`tmdb`) static fields via reflection, the same pattern used by
`ScraperPerfQueryCountTest` below, and resets them to `null` in `@After` so a real client is
lazily re-created for any test running afterward in the same JVM.

## Tools

All added under `test/java/com/archos/mediascraper/perf/` (test-only code, not referenced by
production code) and `test/resources/perf_video_sample.lst` (small synthetic fixture used only
to validate the harness itself, not for real measurements).

### `PerfCacheTmdb`

A `MyTmdb` subclass used by the two live-query tests below. Installs its own OkHttp disk cache
(separate from the production `ScraperCache`, which has a 2-hour TTL) with a 365-day
`Cache-Control` max-age, so a query is only ever sent to the real TMDb API once; every later run
against the same corpus - including runs of a different code variant, via e.g. `git stash` around
`MovieScraper3.java` - replays the recorded response from disk. Cache key is the full request URL
(query + year + language), 500MB size cap. Tracks network-request vs. cache-hit counts.

Must call `super.setOkHttpClientDefaults(builder)` before adding its own interceptors: `Tmdb`'s
own `setOkHttpClientDefaults()` adds the interceptor that injects the required `api_key` query
parameter. Skipping it makes every request fail with 401, which makes `SearchMovie2` call
`MovieScraper3.reauth()` and silently replace the swapped-in test client with a real,
production-cached (short-TTL, uncounted) one for the rest of the run.

`ScraperPerfQueryCountTest` re-asserts the `MovieScraper3.tmdb` / `searchService` static field
swap (via reflection) before every movie, as a defensive measure against exactly that scenario
recurring for any other reason (e.g. a genuine transient 401).

### `ScraperPerfClassificationTest`

No network. Runs each filename through `SearchPreprocessor.parseFileBased()` and buckets movies
by year-detection outcome:

- **confident year** - `isYearConfident()` true, i.e. an explicit `(YYYY)` in the filename. Takes
  `MovieScraper3.getMatches2()`'s non-unified-scoring branch, which already had its own
  exact-match early-stop before this investigation.
- **non-confident year (eligible)** - year present but not confident (e.g. a bare year in a scene
  release filename like `Movie.Name.2015.1080p...`). Takes the `useUnifiedScoring` branch,
  the only branch the short-circuit fix changes.
- **no year at all** - different code path again (search-suggestion fallback), also unaffected.

Answers "how much of a library is even eligible for the short-circuit fix?" before spending any
network budget on it.

```bash
NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
  ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperPerfClassificationTest"
```

Without `NOVA_PERF_VIDEO_LIST`, runs against the bundled `perf_video_sample.lst` (harness
validation only, not representative).

### `ScraperPerfQueryCountTest`

Runs the real `MovieScraper3.getMatches2()` cascade (via reflection-swapped static fields) against
every movie line in the corpus, with the `PerfCacheTmdb` client installed, and reports total TMDb
search requests issued, broken down by the same three year-detection buckets. TV show lines are
skipped (the short-circuit only touches the movie cascade).

```bash
NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
NOVA_PERF_CACHE_DIR=/path/to/persistent/cache \
  ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperPerfQueryCountTest"
```

Without `NOVA_PERF_CACHE_DIR`, defaults to `$TMPDIR/nova-tmdb-perf-cache` (not guaranteed to
survive a reboot on macOS - copy it elsewhere to keep it around long-term and always pass that
path explicitly). The persistent copy used for the findings below lives at
`nova-tmdb-perf-cache/` in the repository root (sibling of `MediaLib/`, `Video/`, `video.lst`) -
untracked, not committed.

### `ScraperPerfNoCascadeTest`

Baseline floor: issues exactly one TMDb search per movie (the cascade's first candidate query
only - the cleaned name + parsed year, or the unstripped original name for the "year at start"
scenario - mirroring `getMatches2()`'s own candidate-selection logic), bypassing all fallback
passes (year-less retry, search-suggestion fallback, aggressive word-dropping). Reports how many
of those single-shot queries came back empty, i.e. how many movies the cascade's fallback passes
exist to rescue. Shares the same `PerfCacheTmdb` cache directory/format as
`ScraperPerfQueryCountTest`, so its single query per movie is usually already cached from a prior
cascade run against the same corpus.

```bash
NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
NOVA_PERF_CACHE_DIR=/path/to/persistent/cache \
  ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperPerfNoCascadeTest"
```

### `ScraperPerfCascadeOrderTest`

Diagnostic (exploratory, not part of the fix): answers "would swapping the order of the two
unified-scoring candidate queries increase the short-circuit's hit rate?" For every eligible-bucket
movie it issues BOTH candidate queries unconditionally (both are normally already cached from a
prior `ScraperPerfQueryCountTest` run, so this costs no extra network calls) and independently
checks each one against the same `countExactTitleMatches() == 1` condition the short-circuit uses.
Tallies, per scenario (year-at-start vs. year-at-end/middle), how many movies only the *current*
first candidate resolves vs. how many only the *other* (currently second) candidate would have
resolved - the latter is exactly the short-circuit rate that would be gained by swapping the order,
at the cost of losing it on the "current only" movies instead.

```bash
NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
NOVA_PERF_CACHE_DIR=/path/to/persistent/cache \
  ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperPerfCascadeOrderTest"
```

## Findings (real library, 19,476 filenames)

Corpus: 2,396 movies, 17,080 TV episodes (TV unaffected by this change, excluded below).

### Classification

| Bucket | Movies | Share |
|---|---:|---:|
| Confident year `(YYYY)` | 658 | 27.5% |
| Non-confident year (short-circuit-eligible) | 1,187 | 49.5% |
| No year at all | 551 | 23.0% |

About half the library takes the code path the short-circuit fix touches.

### TMDb request counts and recognition rate

| | No cascade (floor) | Cascade, before fix | Cascade, with short-circuit fix (optimization) |
|---|---:|---:|---:|
| Total requests | 2,396 | 4,400 | 4,002 |
| Avg requests/movie | 1.00 | 1.84 | 1.67 |
| Confident-year bucket | - | 1,306 (1.98/movie) | 1,306 (1.98/movie, unaffected) |
| Eligible bucket | - | 1,893 (1.59/movie) | 1,495 (1.26/movie) |
| No-year bucket | - | 1,201 (2.18/movie) | 1,201 (2.18/movie, unaffected) |
| Movies with zero results (penalty in recognition) | 376 (15.7%) | 267 (11.1%) | 267 (11.1%, unaffected) |

**Short-circuit fix**: -398 requests overall (-9.0%), concentrated entirely in the eligible
bucket (-21.0%, from 1.59 to 1.26/movie). The eligible-bucket average is not exactly 1.00 because
the short-circuit only fires on an unambiguous single exact-title match on the first candidate
query; roughly 1 in 4 eligible movies still need the second pass (no exact match on pass 1, or a
genuine title collision such as a remake).

**Penalty in recognition is unchanged by the fix**: both cascade variants measure exactly 267
zero-result movies (11.1%) - confirmed empirically with `ScraperPerfQueryCountTest`'s `noResults`
counter, run against the identical cached corpus with (working tree) and without (`git stash`) the
short-circuit fix. This is expected: the short-circuit only ever fires *after* a pass already
returned a single unambiguous exact-title match, so it can only skip a redundant second pass on an
already-resolved movie - it never removes a pass that could have turned a zero-result outcome into
a hit. The fix is a pure request-count win with no coverage cost.

**No-cascade floor**: disabling the fallback passes entirely raises the zero-result rate from
11.1% to 15.7% (+109 movies losing recognition entirely) while only cutting requests by a further
40-46% vs. the cascade. The cascade's fallback passes (year-less retry, search-suggestion
fallback, aggressive word-dropping) exist specifically to rescue those 109 movies; the
short-circuit fix keeps all of that rescue behavior and only removes redundant queries on cases
that were never ambiguous.

### Would a different candidate order help?

Ran `ScraperPerfCascadeOrderTest` to check whether swapping which candidate is tried first in the
unified-scoring branch would increase the short-circuit's hit rate.

**Scenario B - year at end/middle (578 eligible movies; current order = cleaned name+year first,
then raw original name):**

| | Movies | Share |
|---|---:|---:|
| Both candidates hit (order-independent) | 38 | 6.6% |
| Only current-first (cleaned name+year) hits | 360 | 62.3% |
| Only swapped-first (raw original name) hits | 2 | 0.3% |
| Neither hits | 178 | 30.8% |

Net effect of swapping: **-358 movies** (lose 360 short-circuits, gain 2).

**Scenario A - year at start (23 eligible movies; current order = raw original name first, then
cleaned name+year):** too small a sample to be conclusive (year-led filenames like "2001 A Space
Odyssey" are rare in this corpus), but 0/23 hit either way - no evidence swapping helps here either.

**Conclusion: no, reordering would not help.** The current order already puts the more-likely-correct
candidate first in both scenarios by construction:
- Scenario B: the cleaned, year-stripped name is the accurate title for scene-release filenames
  (`Movie.Name.2015.1080p...` -> "Movie Name"); the raw original name still contains junk tokens
  (resolution, codec, group tags) and almost never exact-matches (0.3% hit rate).
- Scenario A: when the year is literally the first token of the real title (`2001 A Space
  Odyssey`), year-stripping incorrectly removes it, so the "cleaned" candidate is the broken one
  ("A Space Odyssey") and the untouched original name is correct - hence it is already tried
  first.

Swapping would cost ~358 of the 1,187 eligible-bucket movies their pass-1 short-circuit, undoing
most of the short-circuit fix's -21% request reduction in that bucket.

## Reproducing / re-running

1. Get a real filename list, one path or filename per line (e.g. `find <library> -type f > video.lst`).
2. Pick a persistent cache directory outside `/tmp` if you want it to survive a reboot.
3. Run `ScraperPerfClassificationTest` first (no network, seconds) to see how much of the corpus
   is even eligible.
4. Run `ScraperPerfQueryCountTest`, then `ScraperPerfNoCascadeTest`, against the same
   `NOVA_PERF_CACHE_DIR`. The first run of each populates the cache with live TMDb calls (can
   take several minutes for a few thousand movies); subsequent runs against the same corpus,
   including runs of a different `MovieScraper3.java` variant, replay from disk.
5. To compare code variants, `git stash` / `git stash pop` around the change in `MovieScraper3.java`
   between two `ScraperPerfQueryCountTest` runs, same cache directory.
6. `ScraperPerfCascadeOrderTest` is optional and only useful when questioning the candidate order
   itself; it reuses the same cache and needs no extra network calls once step 4 has populated it.
