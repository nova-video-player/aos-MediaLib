# Nova Video Player - Scrape Preprocessing Specification

This document defines the strategy and logic for extracting metadata from filenames and querying TMDb. It combines **Leeroy's "Backwards Loop"** efficiency with **Nova's "Title Integrity"** rules.

## 1. Overview & Goals
The goal is to transform a raw filename into an optimal TMDb query while distinguishing between a **Release Year** and a **Year in the Title** (e.g., *1984*, *2001: A Space Odyssey*, *Class of 1999*, *1917*).

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

---

## 6. Special Episodes Handling (TV Shows)
- **Season 0 Mapping**: Any episode parsed with Season `0` or `00` is mapped to TMDb "Specials".
- **Folder Fallback**: Folder named `Specials` without explicit `S00` tags defaults to Season 0.
- **Explicit E-only Patterns**: `E01`-style filenames can match as season 1 when no season marker is present.

---

## 7. The Preprocessing Pipeline (Sequence)
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
