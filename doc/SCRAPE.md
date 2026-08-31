# Nova Video Player - Scrape Preprocessing Specification

This document defines the strategy and logic for extracting metadata from filenames and querying TMDb. It combines **Leeroy's "Backwards Loop"** efficiency with **Nova's "Title Integrity"** rules.

## 1. Overview & Goals
The goal is to transform a raw filename into an optimal TMDb query while distinguishing between a **Release Year** and a **Year in the Title** (e.g., *1984*, *2001: A Space Odyssey*, *Class of 1999*, *1917*).

### Original movie metadata persistence

The full TMDb movie-details response includes `original_title`,
`original_language`, `spoken_languages`, and `belongs_to_collection`. Nova
preserves these independently from the localized title/overview used for display:
`MovieIdParser2` copies them into `MovieTags`, which persists them in the movie
row and the existing `movie_collection` relation.

Movie and TV-show rows use equivalent `original_title_*`,
`original_language_*`, `spoken_languages_*`, and `title_language_*` columns. The original title is
the source title, while the localized display title remains `name_movie` or
`name_show`. Spoken languages are a stable comma-separated list of
non-translated ISO 639-1 codes. TMDb currently returns ISO 639-1 two-letter
codes (for example `en` or `fr`), not display names or locale tags such as
`en-US`; the scraper normalizes accepted two- or three-letter ISO input to ISO
639-1. If the original-language value is unavailable, the database stores `und`
(ISO 639-2/3: undetermined); absent original title and spoken languages are
stored as empty strings. Existing rows receive those same defaults during the
v58 migration. `belongs_to_collection` already has a normalized
`movie_collection` table keyed by TMDb collection id; the scraper now also saves
its overview as the collection description.

`title_language_*` records the language of the title/name TMDb actually returned. It is inferred
from TMDb's appended translations in the same details response, so it adds no HTTP request. It
may therefore differ from the requested scrape language when TMDb falls back. If the returned
text cannot identify one language unambiguously, Nova stores `und`; rows that predate v59 are
also backfilled to `und`.

---

## 2. Movie vs TV Decision

`SearchPreprocessor.parseFileBased()` tries TV matchers before movie matchers. This is intentional because a filename such as `Show.S01E03.mkv` must not be swallowed by the movie fallback matcher.

### 2.1 TV Matching Input Cleanup
Before applying TV filename regexes, the filename used for TV pattern matching is cut at the first release-garbage token and then has punctuation/whitespace separators normalized to spaces:

- resolutions: `1920x1080`, `1280x720`, `3840x2160`, `1080p`, `2160p`
- codecs and release tags: `x264`, `x265`, `HEVC`, `BluRay`, etc.

This cleanup happens before the TV-vs-movie decision so release-tail tokens cannot be interpreted as season/episode numbers, while valid episode markers before the release tail remain visible to the TV regexes.

Example:

- `1961 le cave se rebiffe 1920x1080 HD ABcollection gabin.mkv`
- Without upfront cleanup, `1920x1080` can be parsed as `S1920E1080`.
- With cleanup, TV matching only sees `1961 le cave se rebiffe`, does not claim the file, and the movie matcher can extract title/year correctly.

Path-based matchers still receive the original URI. The cleanup is scoped to TV filename regex input to avoid breaking folder/path rules.

Episode markers must appear before release garbage. Inputs such as `The.100.2014.720p.S01E01.mkv` are not considered valid TV filenames by these rules because the release token starts the tail before `S01E01`.

The raw filename is still inspected before this cleanup for explicit country-of-origin hints. This matters because `Zorro.(FR).2024.S01E01...` becomes `Zorro FR 2024 S01E01` for TV regex matching, but the original `(FR)` marker must still be available for TMDb result filtering.

### 2.2 Known Ambiguous TV Patterns
Some filename forms are intentionally still treated carefully because they can be valid TV patterns or movie-title collisions:

- `SxxEyy` is a strong TV signal.
- `Show 0208 title` can mean season 2 episode 8.
- `[0033]` can look like `S00E33`, but it can also be a release tag.
- Numeric movie titles such as `1408` can look like `S14E08`.

These cases should be fixed with narrow guards, not by disabling broad TV detection.

Current compact `SSEE` guard:

- `Battlestar Galactica 0208 Final cut` remains a valid compact TV match.
- `Chambre 1408 (2007)` is not treated as `S14E08` because the compact number is followed by an explicit release year.
- Resolution-like numbers already cut the release tail before this stage, and numbers preceded by `x` are excluded to avoid `1920x1080`-style matches.

### 2.3 TV Country Hints
TV preprocessing recognizes explicit parenthesized origin hints before cleanup:

- `(FR)`, `(US)`, `(UK)` are extracted as country-of-origin filters.
- The hint is removed from the show title and passed through `TvShowSearchInfo`.
- `SearchShowParser` skips TMDb results whose `origin_country` does not contain that hint.

Example:

- `Zorro.(FR).2024.S01E01.TRUEFRENCH.1080p.WEB.EAC3.H265-FW.mkv`
- Parsed as show `Zorro`, year `2024`, season 1 episode 1, country `FR`.
- TMDb has multiple 2024 shows named `Zorro`; the `FR` origin hint selects the French show instead of the more popular Spanish one.

Release/language tags such as `FRENCH`, `TRUEFRENCH`, `MULTI`, `VFF`, and `VOF` are treated as release garbage, not country-of-origin hints. They can describe audio/release language and are not reliable evidence of TMDb origin. For example, `Les.traitres.2022.S06E03.2026-04-11.FRENCH.1080p.WEB.H264-THESYNDiCATE.mkv` matches the French show through title/year/language search, not through a country-origin filter.

### 2.4 TV Path Year Extraction & Season Folders
When the filename itself carries no year, `TvShowMatcher` looks for one in the last three path segments (the file's immediate parent, grandparent, and great-grandparent directories) using the same guarded backwards "anywhere" scan used for movies (Section 3.2). This lets a year embedded mid-segment be picked up, e.g. `神雕侠侣.1995.S01`, not just years that are parenthesized or trail the whole segment.

Because directory segments can also be generic, non-show organizational folders (e.g. a user sorting downloads by year into `download-2026/`), a year is only trusted from a segment when there is structural evidence that segment is actually part of the show's own `Show/Season/Episode` layout:

- The segment is itself a season folder (matches `S02`, `Season 1`, etc. at its start), or
- The segment is the **immediate parent** of a season folder (i.e. the show folder, e.g. `DuckTales (1987)/S02 (1988)/...`), or
- The segment embeds a season token itself alongside the year (e.g. `神雕侠侣.1995.S01`).

This evidence is intentionally scoped to direct parent/child adjacency, not "anywhere in the scanned window": a season folder two levels above the file (e.g. `download-2026/The 100/S02/episode.mkv`) does not lend its evidence to `download-2026`, only to its own immediate parent (`The 100`). Without this scoping, unrelated ancestor folders that merely contain a 4-digit number could leak a spurious year into the TMDb search filter.

If a year is found in both a season folder and its parent show folder, the show folder's year takes precedence as the show's first air date year; the season folder's year is a secondary fallback. Furthermore, if a year filter yields no results on TMDb, `ShowScraper4` retries searching the show name without a year constraint.

---

## 3. Year Extraction Strategy (The "Leeroy" Engine)

The scraper uses a right-to-left (backwards) scan to find the most likely release year candidate.

### 3.1 Core Rules
- **Valid Range**: **1906** to **CurrentYear + 1**. (Resolves *Paris Police 1900*).
- **Resolution Safety**: Uses Lookaround Regex `(?<![\d\p{L}])(\d{4})(?![\d\p{L}])` to ignore years inside resolutions (e.g., `1080` in `1920x1080`) or codecs.
- **Hard Break**: As soon as the backwards loop finds a valid year, it **stops** searching further left. This provides stability and protects title prefixes.

### 3.2 Extraction Heuristics (Sequential)
1. **Parentheses Year `(YYYY)`**: 
   - *Status:* **Confident**. 
   - *Action:* Strip from name.
2. **Backwards Scan ("Anywhere" Year)**:
   - Perform backwards regex scan for isolated 4-digit numbers.
   - On the first valid match found:
     - Calculate the **Cut Index** (characters remaining to the left of the year).
     - **If Cut Index == 0** (Year at start, e.g., `2001 A Space Odyssey`):
       - Identify year but **DO NOT STRIP** from the title.
       - Set `yearAtStart = true`.
     - **If Cut Index > 0 AND Remainder is < 2 characters** (e.g., `1984.mkv`):
       - **Ignore the year**. Keep it in the title.
     - **If Cut Index > 0 AND Remainder is >= 2 characters** (e.g., `Class of 1999`):
       - Identify year and **STRIP** from the title.
       - Set `yearConfident = false`.

---

## 4. Fallback Two-Stage Identification (The "Nova" Unified Scoring)

If a year is identified but not "Confident" (not in parentheses), the scraper performs two searches to ensure the best match wins.

### 4.1 Candidate Ordering
The order of search passes depends on the `yearAtStart` flag:

**Scenario A: Year at Start** (e.g., `1961 le cave se rebiffe`)
1. **Pass 1 (Title Priority):** Search **Original Name** (unstripped), Year: `null`.
2. **Pass 2 (Split Fallback):** Search **Cleaned Name** (remainder), Year: `1961`.

**Scenario B: Year at End/Middle** (e.g., `Class of 1999`)
1. **Pass 1 (Year Priority):** Search **Cleaned Name** (stripped), Year: `1999`.
2. **Pass 2 (Merge Fallback):** Search **Original Name** (unstripped), Year: `null`.

### 4.2 Unified Scoring (The Re-Rank)
1. Execute searches for **both** candidates.
2. Pool all results from both searches into a single list.
3. For every result, calculate the **Levenshtein Distance** against the `originalName`.
4. Sort the pool by distance (Ascending). 
   - *Example:* For `Class of 1999`, the year-pass might return *"In a Class of His Own"*, but the title-pass returns *"Class of 1999"*. Sorting by distance ensures the exact title match (distance 0) wins.

---

## 5. Result Ranking Rules

Search results are sorted by a shared comparator after each parser has assigned a Levenshtein distance.

### 5.1 Default Priority
1. **Lowest Levenshtein distance** wins.
2. **Highest popularity** wins when distance is tied.
3. **Known date beats missing date**.
4. **Oldest release/air date** wins when distance and popularity are tied.
5. If no date is available, string year is used as a final fallback.

Popularity is intentionally before date for normal tie-breaks. This protects cases such as:

- `Silo.S01E03.1080p.HEVC.x265-MeGusta[eztv.re].mkv`
- TMDb has two exact title matches named `Silo`.
- The 2023 Apple TV show has high popularity and should beat the lower-popularity 2017 show.

Without popularity, the date fallback can select the older but wrong show.

### 5.2 Same Movie Title Remakes
There is one narrow exception before popularity:

If both results are movies, have the same non-trivial localized title, have known release dates, have no explicit query year, and have equal Levenshtein distance, the older movie wins.

This handles classic animation/live-action remake collisions:

- `LES.101.DALMATIENS`
- `Les 101 Dalmatiens` 1961 and `Les 101 Dalmatiens` 1996 both have distance 0.
- With no year in the filename, the original 1961 movie is preferred over the later remake.

The exception is deliberately narrow to avoid regressions:

- It does not apply to TV shows, preserving `Silo`.
- It does not apply when a year is explicitly present in the query.
- It does not apply to numeric-only or very short titles, preserving ambiguous cases such as `1984` and `X`.

### 5.3 No Date In TV Show Folder Context
When a TV filename has no year and only a show name/folder hint is available, the first sorted result wins.

Example:

- `./series/Galactica/Season 1/galactica.ep3.avi`
- Parsed as show `Galactica`, season 1, episode 3.
- With no date in the filename, current ranking selects `Galactica 1980`.

This is considered expected behavior unless the filename or folder provides a discriminating year.

### 5.4 Acronym Show Title Boost (TV Shows)
TV shows are often titled `ACRONYM: Full Description` (e.g. `CSI: Crime Scene Investigation`, `CSI: NY`, `HPI : Haut Potentiel Intellectuel`). Raw Levenshtein distance penalizes these titles against an acronym-only filename query, because the subtitle after the colon counts as many insertions. This can let an unrelated but shorter title rank higher.

Example:

- `HPI.S03E01.FRENCH.1080p.10bit.WEBRip.6CH.x265.HEVC-SERQPH.mkv`
- Parsed show name `HPI`.
- Distance to the correct `HPI : Haut Potentiel Intellectuel` (TMDb id `112738`) is 30; distance to the unrelated US show `High Potential` (id `226637`) is only 11.
- Without correction, `High Potential` wins.

`SearchShowParser` detects this convention via `isAcronymHeadMatch`: it trims whitespace around the first `:` in the candidate's title/original title and, if the resulting head exactly equals the query, forces the Levenshtein distance to `0` for that candidate. Trimming (rather than requiring `": "` literally) also covers locales such as French that space the colon (`"ACRONYM : Subtitle"`).

### 5.5 Trailing Roman Numeral Head Boost (Movies)
Numbered franchise entries are often released/scraped as just `Title N` (roman numeral), while TMDb's actual title carries the full `Title: Episode N - Subtitle` form. Raw Levenshtein distance penalizes the correct long title against an unrelated but shorter candidate that happens to also contain the same roman numeral (e.g. a parody or "making of" entry).

Example:

- `Star.Wars.III.[1080p].MULTi.(2005).BluRay.x264-PopHD.(La.revanche.des.siths.3).mkv`
- Cleaned query `Star Wars III`.
- Distance to the correct `Star Wars: Episode III - Revenge of the Sith` (TMDb id `1895`) is 31; distance to the unrelated `The Robot Chicken: Star Wars Episode III` (id `51888`) is only 27.
- Without correction, the Robot Chicken entry wins.

`MovieScraper3` detects a standalone roman numeral at the end of the cleaned query via `ParseUtils.getTrailingRomanNumeral()`. If no candidate is already an exact match (distance `0`), candidates whose title/original title contain that same numeral as a standalone word are re-scored using only the title's head up to and including the numeral (dropping any trailing subtitle); the boosted distance replaces the original only if it is strictly lower.

The boost is skipped outright whenever an exact match already exists, so it never touches an already-decisive result. This protects titles that legitimately end in a roman numeral, such as `Henry IV` or other regnal names, from being second-guessed.

This only helps when the roman numeral literally appears in TMDb's title text. It cannot resolve cases like `Star.Wars.V`/`Star.Wars.VI`, whose correct TMDb titles are `The Empire Strikes Back` / `Return of the Jedi` — no numeral appears anywhere in that text, so no string-distance approach can bridge it; that would require a separate franchise-collection/chronology lookup instead.

---

## 6. Special Episodes Handling (TV Shows)
- **Season 0 Mapping**: Any episode parsed with Season `0` or `00` is mapped to TMDb "Specials".
- **Folder Fallback**: Folder named `Specials` without explicit `S00` tags defaults to Season 0.
- **Explicit E-only Patterns**: `E01`-style filenames can match as season 1 when no season marker is present.

---

## 7. TV Auto-Scrape Recovery (Season/Episode & Title-Collision Fallbacks)

`Scraper.getAutoDetails()` is the entry point used by directory/library auto-scraping (as opposed to interactive search). For TV shows it calls `ShowScraper4.searchWithTitleCollisionFallback()` instead of the shared `BaseScraper2.search()` used by movies, adding two recovery layers on top of the ranking described in Section 5.

### 7.1 Fuzzy Episode-Title Match
When the locally-parsed season/episode number is not found in the already-fetched season data (e.g. local numbering diverges from TMDb's, such as a filename numbered per broadcast order vs. TMDb's production order), `ShowScraper4.buildTag()` falls back to `fuzzyMatchEpisodeByTitle()`:

- `ShowUtils.extractEpisodeTitle()` pulls the episode title out of the filename by locating the `SxxExx` marker and taking the cleaned remainder. It tolerates extra leading zeros in the episode number (e.g. `S01E018` for episode 18).
- The extracted title is gated by `isPlausibleEpisodeTitle()` (at least 3 characters, containing at least two consecutive letters) so leftover release-tag garbage not caught by `ParseUtils`'s static `GARBAGE_*` lists cannot masquerade as a title.
- If it passes the guard, it is matched against the already-fetched season's episode titles using Levenshtein distance, with a match accepted only if the distance is within 40% of the longer title's length.
- This recovery costs no extra TMDb request: it only reuses the season data already fetched for the requested season.

### 7.2 Title-Collision Cascade Fallback
Some shows share the exact same title as an unrelated show (e.g. a classic show and its reboot), so the top-ranked search candidate is not always the right one, even after Section 5's ranking. `searchWithTitleCollisionFallback()`:

1. Runs the normal ranked search (Section 5), keeping up to `CASCADE_CANDIDATE_LIMIT` (5) candidates — free, since TMDb's `tv()` search already returns up to 20 results in one page.
2. Fetches full details for the top candidate. If season/episode data resolves to a genuine episode (a real title, via the recovery in 7.1 if needed), that result is returned.
3. Otherwise, if the top candidate's Levenshtein distance is exactly `0` (a genuine title collision, e.g. a classic show and its reboot sharing the exact same title), retries the next candidates that are also tied at distance `0`, until one yields a genuine match. If the top distance is not `0` (a fuzzy/partial match, not a true collision), no cascade happens: the top candidate's result is used as-is, same as a plain single-candidate lookup.
4. If no tied candidate yields a genuine match, returns the top candidate's (not-found) result, preserving existing behavior for episodes that are legitimately missing from the correct show.

Restricting the cascade to exact-title-tie (distance `0`) candidates, and only triggering the extra requests in the failure branch, avoids mis-attributing episodes to an unrelated, lower-ranked show and bounds the extra TMDb cost to the genuine-collision case: each additional candidate costs a full show/season fetch (and possibly a Season 0 fallback, 7.5), so cascading through fuzzy-matched, non-colliding candidates would multiply request cost with no accuracy benefit.

### 7.3 Per-Show/Per-Year Cache Key Scoping
Because the cascade fallback (7.2) can call into TMDb-detail fetches for multiple distinct shows that happen to share the same cleaned title, and `SearchShow`'s search cache can be reused across queries for the same cleaned name in different years, cache keys must be scoped precisely enough to avoid collisions:

- `SearchShow.showCache` is keyed by `cleanedName|year|language`, not just `cleanedName|language`: TMDb's `tv()` search applies server-side year filtering, so a year-filtered response must not be served back for an unrelated year-less (or different-year) query for the same name.
- `ShowScraper4`'s per-show/season metadata caches (`showKey`, `seasonKey`, `fallbackShowKey`, `season0Key`, `season0KeyEn`) are keyed by cleaned show name **and TMDb show id**, not name alone, so two different shows sharing the same cleaned title (e.g. a classic show and its reboot, as in 7.2) cannot collide and return each other's cached season/episode data.

### 7.4 Absolute (Continuous) Episode Numbering Fallback
Some long-running shows (mostly anime split into arbitrary TMDb "seasons") number episodes continuously across seasons on TMDb instead of resetting to `1` at the start of each season — e.g. *Hunter x Hunter* (2011, TMDb id 46298) season 2 starts at `episode_number` 63, not 1, and season 3 at 137. A file locally numbered per its own season's broadcast order (e.g. `S02E08`) then 404s against TMDb's `/tv/{id}/season/{s}/episode/{e}` endpoint, even though season 2 legitimately has 74 episodes.

`ShowScraper4.getDetailsInternal()` detects and corrects this purely from data, with no genre/media-type gating:

1. **Single-episode 404**: if the direct per-episode lookup returns `NOT_FOUND`, the full season is fetched and its first episode's `episode_number` is checked. If it isn't `1`, the requested episode is remapped to the equivalent absolute number (`firstEpisode.episode_number + requestedEpisode - 1`) and looked up again in the fetched season.
2. **Full-season fetches** (`getAllEpisodes` and season-only fetches): the same first-episode check runs unconditionally right after the season is fetched, since these paths never 404 per-episode but would otherwise silently fail the later `allEpisodes.get(episodeKey)` lookup in `buildTag()` for absolutely-numbered seasons.
3. **`sEpisodeCache` cache hit**: a season already cached from an earlier request skips the fetch paths above entirely, so the same detection is redone against the cached map — deriving the season's minimum episode number from the map's own keys (`showId|season|episode|language`, TMDb's real numbers) rather than from the cached `EpisodeTags` objects' `getSeason()`/`getEpisode()`, since those objects are shared across requests and must not be used as a numbering source of truth.

In all three cases, once the absolute episode is resolved, its `episodeKey` (built from TMDb's real season/episode numbers) is substituted so `buildTag()`'s map lookup succeeds instead of falling through to an empty tag. The returned `EpisodeTags` keeps TMDb's own absolute `episode_number` (set by `ShowIdEpisodes.getEpisodes()` from the matched `TvEpisode`, same as any normally-matched episode) rather than being renumbered back to the file's local per-season numbering: this matches how TMDb's own season page numbers these episodes, and keeps downstream season grouping and next-episode navigation (which key purely off the stored season/episode database columns) self-consistent. Since the shared cache entry is never mutated for this — the resolved tag is simply returned as-is — there is no risk of corrupting it for later lookups of that season.

This is self-limiting by construction: shows with normal per-season TMDb numbering always have a first episode numbered `1`, so the fallback never triggers for them.

### 7.5 Season 0 (Specials) Title-Match Fallback
Some episodes are moved by TMDb into Season 0 (Specials) entirely, rather than kept as a numbered episode of the season they locally belong to on disk — e.g. *Firefly* (TMDb id 1437) "Heart of Gold" aired after the original 2002 series was cancelled and is listed by TMDb only as Season 0 Episode 3, even though season 1 genuinely has just 11 numbered episodes there and the file is locally filed as `S01E12`. Neither a direct `episodeKey` lookup nor 7.1's fuzzy title match against season 1 can ever succeed for such a file, since the correct episode simply isn't present in that season's data.

After `buildTag()` returns its empty-placeholder tag (no title/plot, indicating both the direct lookup and 7.1's fuzzy match failed), `ShowScraper4.getDetailsInternal()` retries the extracted episode title hint against TMDb's season 0, using the same language-then-English fallback pattern as the existing `SxxE00` remap (Section 6): fetch season 0, run `fuzzyMatchEpisodeByTitle()` against it, and on a confident match fetch that episode's full metadata via `ShowIdEpisodes.getEpisodes()`. Like the `SxxE00` remap, the returned tag keeps TMDb's own season/episode numbering (`S00E03`) rather than the file's local `S01E12`, so it groups correctly under Specials in the UI and is consistent with how the app already treats every other special.

This fallback only triggers when the requested season is not already `0` (avoiding redundant work when the `SxxE00` remap already handled it) and only after both the direct lookup and the within-season fuzzy match have failed, so it adds no extra TMDb cost for the overwhelming majority of episodes that resolve normally.

---

## 8. The Preprocessing Pipeline (Sequence)
1. **Extension Stripping**: Defensive removal of `.mkv`, `.mp4`.
2. **Original Name Capture**: Capture title here for re-ranking reference.
3. **Numbering Stripping**: Remove leading numbers (restricted to **max 3 digits**).
4. **Year Extraction**: Apply Section 3 heuristics.
5. **Garbage Cleanup**:
   - Strip brackets `[]`, `{}`, `<>`.
   - Strip case-sensitive tags (`FRENCH`, `MULTI`).
   - Strip resolutions (`1920x1080`, `720p`).
6. **Normalization**: Trim, unify apostrophes, and resolve acronyms.

For TV detection there is an additional pre-pass before the general movie-style cleanup:

1. Extract raw parenthesized country hint, if present.
2. Cut the filename at the first release-garbage token.
3. Normalize separators for TV regex matching.
4. Apply TV regexes and pass the preserved country hint to result filtering.
