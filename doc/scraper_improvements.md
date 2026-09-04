# Scraper improvement candidates (from confidence-index review)

Findings from a manual review of `ScraperResultDumpTest`'s confidence index (see `perf_scrape.md`)
run against the real 19,476-line library corpus. Lists concrete mis-scrapes worth fixing, as opposed
to the (much larger) set of low-confidence-but-correct matches caused by translated/localized titles.

## How to get the list

1. Generate `video-result.lst` with the confidence index (see `perf_scrape.md` for
   `ScraperResultDumpTest` details, env vars, and cache setup):
   ```bash
   NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
   NOVA_PERF_CACHE_DIR=/path/to/persistent/cache \
   NOVA_PERF_RESULT_FILE=/path/to/video-result.lst \
     ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperResultDumpTest"
   ```
2. Extract unique `(name, topTitle)` pairs among `OKAY` lines with `confidence < 30`, since most
   low-confidence lines repeat many times (e.g. every episode of a show), and sort by confidence:
   ```bash
   awk -F'|' '$5=="OKAY" && $9!="" && $9<30 {
       key=$3"\x01"$8
       if(!(key in minconf) || $9<minconf[key]) minconf[key]=$9
       if(!(key in reason)) reason[key]=$10
       count[key]++; type[key]=$1; topid[key]=$7; year[key]=$4
   }
   END{for (k in count) {
       split(k,a,"\x01")
       printf "%s\t%s\t%s\t%s\t%s -> %s (id %s)\t%s\n", minconf[k], count[k], type[k], year[k], a[1], a[2], topid[k], reason[k]
   }}' video-result.lst | sort -n
   ```
3. Manually review the sorted output (lowest confidence first). Most entries are correct matches
   with a translated/localized title (French/Japanese title vs. English TMDb title) - expected false
   positives of a text-similarity heuristic, not scraper bugs. The list below is the subset that are
   genuine mis-scrapes, identified by checking each candidate against known franchise facts.
   This is a manual step - the confidence index narrows ~19,186 `OKAY` lines down to ~807 (4.2%)
   worth a look, not an automatic bug detector.

## Confirmed mis-scrapes

Nearly all of these share one root-cause pattern: a short, ambiguous, number-suffixed query (e.g.
`Rocky 1`, `Star Wars VI`, `Cars Ii`, `Transformers 2`) resolves to an unrelated, low-popularity TMDb
title, while the *same* movie's fuller/subtitled filename elsewhere in the corpus resolves correctly.
The scraper's fallback search on "title + number" isn't reliably picking the popular/canonical entry
for the sequel.

| Query (name/year) | Wrong result (id) | Should be |
|---|---|---|
| `Millenium 2` | Godzilla 2000: Millennium (10643) | The Girl Who Played with Fire |
| `Wall e` | Westler: East of the Wall (39360) | Pixar's WALL·E |
| `La Haut` (no accent) | To the Top (398933) | Pixar's Up - `Là Haut` *with* the accent matches correctly (14160): accent-stripping bug |
| `Seven` (1995) | Seven Samurai (346) | Se7en |
| `Star Wars VI` | Star Wars [1977] (11) | Return of the Jedi - full title `Star Wars Episode VI Return Of The Jedi` matches correctly elsewhere |
| `Star Wars V` | Star Wars Tech (154452) | The Empire Strikes Back |
| `Superman I` | Superman II (8536) | Superman (1978) |
| `Rocky 1` | Creed (312221) | Rocky (1976) |
| `Le Parrain 3` | Gotti (339103) | The Godfather Part III |
| `Cars Ii` | Old Men in New Cars: In China They Eat Dogs II (10751) | Cars 2 |
| `Mad Max 3` | Stryker (30927) | Mad Max Beyond Thunderdome |
| `Monty Python 3 Le Sens De La Vie` | Best of Flying Circus Vol. 3 (484832) | Monty Python's The Meaning of Life |
| `Starwars 2 L'attaque Des Clones` | "Aussie StarWars 2!" (1437115) | Attack of the Clones - plain `Star Wars II` matches correctly elsewhere |
| `Transformers 2` (no subtitle) | Transformers: Rise of the Beasts (667538) | Revenge of the Fallen - `Transformers 2 La Revanche` matches correctly elsewhere |
| `Transformers 3` (no subtitle) | Space Transformer (159395) | Dark of the Moon - `...La Face cachée de la Lune` matches correctly elsewhere |
| `Thor 2` | Team Thor: Part 2 (441829) | Thor: The Dark World |
| `Dragon 2` (2014) | Lady Dragon 2 (80342) | likely How to Train Your Dragon 2 |
| `Godzilla King Of The Monsters FR` (2019) | Destroy All Monsters (3107) | Godzilla: King of the Monsters (2019) |
| `La Reine Des Neiges` (no year/qualifier) | The Ice Tower (1143440) | Disney's Frozen - `La Reine des neiges 3D` matches correctly elsewhere |
| `Cube` (1997, no other qualifier) | "Cube" id 1261243 (exact title match, but obscure/likely duplicate TMDb entry) | Cube (1997), canonical id 640 |
| `Zorro FR` (6 files) | Never Weaken (44405) | unrelated Harold Lloyd short - wrong for all 6 |
| `Episode Spécial Erazer` | Samurai Sentai Shinkenger Special (1136398) | unrelated |
| `Bad Money` | Dawg (46112) | unrelated (unverified, but implausible) |

Lower-priority, not individually re-verified against live TMDb: ~15 `NN Bonus ...` lines (Doctor Who
classic-serial DVD bonus features matched to unrelated workout/music DVDs) plus a few Adventure
Time/Minions "bonus" shorts, all at confidence 0 - plausibly all wrong, but these are DVD extras
rather than main features, so lower value to fix.

## Regression test coverage (2026-09-01)

Added 9 of the "short/ambiguous sequel query" cases to `MediaLib/test/resources/scraper_test_cases.csv`
(expected ids verified against the cached TMDb search candidate lists, ranked by popularity, not just
guessed from memory). Ran `ScraperIntegrationTest` offline against the persistent cache
(`NOVA_TEST_CACHE_DIR=nova-tmdb-perf-cache`) to confirm each one actually reproduces:

```bash
cd Video && NOVA_TEST_CACHE_DIR=/path/to/nova-tmdb-perf-cache ./gradlew :MediaLib:testDebugUnitTest \
  --tests "com.archos.mediascraper.preprocess.ScraperIntegrationTest"
```

Result: **7 of 9 reproduce the mis-scrape**, 2 now resolve correctly (results below).

| Case | Result | Detail |
|---|---|---|
| Millenium 2 | **FAILS** | top=Godzilla 2000: Millennium (10643); expected The Girl Who Played with Fire (24253) not returned at all |
| Wall-e | **FAILS** | top=Eton Wall Game (1561933); expected WALL·E (10681) not returned at all - worse than the original dump's wrong id (39360), TMDb candidate set has drifted |
| Cars Ii | **FAILS** | top=Old Men in New Cars... (10751), matches doc; Cars 2 (49013) not returned at all |
| Mad Max 3 | **FAILS** | top=Stryker (30927), matches doc; Mad Max Beyond Thunderdome (9355) not returned at all |
| Transformers 2 | **PASSES** | scraper already resolves correctly with current cascade logic |
| Transformers 3 | **FAILS** | top=Space Transformer/Les Transformeurs De L'espace (159395), matches doc; correct id (38356) ranked position 2, not top |
| Thor 2 | **FAILS** | top=Team Thor: Part 2 (441829), matches doc; correct id (76338) ranked position 2, not top |
| Le Parrain 3 | **PASSES** | scraper already resolves correctly with current cascade logic |
| La Reine Des Neiges | **FAILS** | top=La Reine des neiges (72214) - a *different* wrong/duplicate TMDb entry than the original dump's (1143440); expected 109445 not returned at all |

Notes:
- For Transformers 3 and Thor 2 the correct entry *is* in the candidate list (position 2) - this is a
  pure ranking/scoring bug, the search itself finds the right movie.
- For Millenium 2, Wall-e, Cars Ii, Mad Max 3, and La Reine Des Neiges the correct entry isn't returned
  by the search at all within the considered candidates - the query itself needs improving (e.g. TMDb's
  own text search for these short/ambiguous queries doesn't surface the popular title), not just the
  local ranking.
- Transformers 2 and Le Parrain 3 no longer reproduce against the current cache/cascade logic, even
  though the original 19,476-line corpus dump showed them wrong - either the cascade fallback (retry
  with fuller filename/subtitle) already covers these two, or TMDb's search ranking for these exact
  queries has changed since the corpus was scraped. Left out of the CSV as flaky/non-reproducing.
- Root cause still not identified in `MovieScraper3`/`SearchMovieParser2` - these entries are useful
  regression coverage for whenever that investigation happens, not a fix.

## Bonus/Extras folder skip heuristic (implemented 2026-09-02)

The DVD/BluRay-bonus-disc cluster mentioned above (Doctor Who classic-serial extras, Kaamelott
bonus discs, Marx Brothers trailers/interviews, Minions bonus shorts - ~40 files in the corpus) is
essentially unrecoverable: none of them scored above confidence 29, and manual review found zero
genuine matches among them. Rather than trying to improve search ranking for these, they are now
skipped before any TMDb/TVDb query is issued.

**Heuristic:** if any *whole* path directory segment (not the filename, not a substring) equals
`bonus`, `bonuses`, `bonus features`, `extra`, or `extras` (case-insensitive, `_` treated as space),
the file is marked `skipScraping` and `Scraper.getMatches`/`getAutoDetails` short-circuit to
`ScrapeStatus.NOT_FOUND` without a network call.

Deliberately conservative to avoid false positives:
- Whole-segment match only, so a folder like `Bonusville (2020)` or a movie literally titled
  `Bonus (1964)` (a real TMDb entry, id 47702) is never affected.
- Only directory segments are checked, never the filename itself - so a real movie called "Bonus"
  living directly in a normal folder scrapes normally.
- `Specials`/season-0 folders are intentionally *not* on the list - those are legitimate TV
  structure used for real season-0 episodes elsewhere (see the Firefly season-0 fallback case in
  `scraper_test_cases.csv`), and conflating the two would break real matches.

Implementation: `ParseUtils.isNonScrapableFolder(Uri)` (MediaLib/src/com/archos/mediascraper/preprocess/ParseUtils.java),
wired into `SearchPreprocessor.parseFileBased` (sets `SearchInfo.skipScraping`) and checked in
`Scraper.getMatches`/`getAutoDetails` (MediaLib/src/com/archos/mediascraper/Scraper.java). Covered by
`MediaLib/test/java/com/archos/mediascraper/preprocess/NonScrapableFolderTest.java`.

## Caveats

- This review is based on general franchise knowledge, not live TMDb re-verification of every entry -
  spot-check before acting on any single row.
- The confidence index itself is a text-similarity heuristic (Levenshtein distance + exact-title +
  year-match), not language-aware, so it also flags many *correct* matches whose title is a
  translation of the TMDb title (e.g. `Akahige` -> Red Beard, `Le Roi Lion` -> The Lion King). Those
  are expected and not listed here.
- Root cause has not yet been investigated in `MovieScraper3`/`SearchMovieParser2` - this file
  records the *symptoms* found, not a fix.
