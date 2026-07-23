# Nova Video Player - NFO Processing: Noticed Improvements

This document records issues and improvement opportunities found while reviewing
the NFO import/export pipeline (see `NFO.md` for the current architecture). It
consolidates two independent reviews (Claude + Codex); items validated by both are
marked. Some items have since been implemented and are tagged **[DONE]** with the
status noted in their section; the rest remain a backlog of observations with
concrete pointers.

Priority legend: **P1 = high**, **P2 = medium**, **P3 = low/cleanup**.

Status legend: **[DONE]** committed, **[IN PROGRESS]**, untagged = not started.

---

## Issue #1782 archive findings (X-Men: The Animated Series)

The reporter supplied the full sidecar archive: 76 episode NFOs plus a
`tvshow.nfo`. Verified facts from the archive (input incompatibilities are
confirmed; full runtime outcome cannot be proven without the matching videos,
database state, and scrape log):

- Every file identifies content with `<uniqueid type="tmdb" default="true">`
  (show `1423`, e.g. S01E01 `76118`) and contains **no** legacy `<id>`/`<tmdbid>`.
  Nova's handlers previously read only the legacy tags, so all TMDb ids were
  silently dropped (now fixed — see the `<uniqueid>` read item).
- `tvshow.nfo` has `<year>1992</year>` and **no** `<premiered>`.
- No artwork (poster or fanart) exists anywhere in the archive.
- Episode files use matching `{video}.nfo` names, so discovery and the
  `tvshow.nfo` fallback work correctly — discovery is not the problem.
- A successful NFO save was followed by online identification whenever no poster
  existed, allowing the online result to overwrite curated season/episode values
  (now fixed — see P1 artwork-gating).

**Resolving #1782 required BOTH** (either one alone is insufficient):

1. Parse type-aware `<uniqueid>` — **DONE** (see the group-A `<uniqueid>` item).
2. Treat a successfully persisted NFO as authoritative for identity/ordering
   regardless of artwork — **DONE** (see P1 artwork-gating). Online identification
   is skipped entirely after a valid NFO save, including under "rescrape all"; an
   artwork-only fetch can be designed later.

The year/fanart symptoms the reporter mentioned are addressed by the
already-committed Phase 1 fixes below.

**Commit sequence:**

1. **DONE** - Order-independent, type-aware `<uniqueid>` support in the
   movie/show/episode handlers (tmdb→`setOnlineId`, imdb→`setImdbId`), preferred
   over legacy `<id>`/`<tmdbid>`, with modern-and-legacy precedence tests.
2. **DONE** - Remove artwork as an identification-success condition; a persisted
   NFO suppresses online identification (`shouldIdentifyOnline`), with decision
   tests for the posterless, rescrape-all, no-NFO and failed-save cases.
3. Add slim fixtures derived from the archive — excluding the copyrighted plot
   text — plus a regression proving curated season/episode values remain
   authoritative when no poster is present.

---

## P1 - Artwork presence wrongly gates NFO import success [DONE]

**Where:** `AutoScrapeService.java` no-poster branch after the NFO save.

A valid NFO was saved first, but the absence of artwork marked the file as
"not scraped" (`notScraped = true`), which then triggered an online scrape that
could overwrite the curated NFO metadata. Released code made the episode case
worse by building the fallback query from the **episode** title (for example
`Pilot.mp4`) rather than show/season/episode.

This was not theoretical: [aos-AVP issue #1782](https://github.com/nova-video-player/aos-AVP/issues/1782)
uses episode NFOs to impose an alternate order for *X-Men: The Animated Series*.
When the NFO/show had no usable poster, Nova saved the NFO and then performed the
online fallback, allowing TMDb's ordering to overwrite the locally curated
season/episode mapping. A backdrop did not satisfy the poster-presence test.

**Fix:** a successfully persisted NFO is now authoritative for identity and
season/episode ordering. The no-poster branch no longer flips `notScraped`/
`noScrapeError` back on after a valid save, so the online re-identify+save path
cannot run and overwrite curated data. As Codex advised, online identification is
skipped entirely after a valid NFO save; an artwork-only fetch can be designed
later. The now-unused `getMissingNfoPosterScrapeUri()` helper (and its tests) were
removed; `hasNoUsableNfoPoster()` is retained for diagnostic logging and coverage.

---

## P1 - `.archos.nfo` episodes cannot fall back to `tvshow.nfo` [DONE]

**Status:** implemented in the NFO hardening commit; the custom branch now
performs the `tvshow.nfo` same/parent-folder lookup. Covered by
`NfoParserTest.customEpisodeFindsTvShowNfoIn{Same,Parent}Folder`.

**Where:** `NfoParser.java:188` (discovery) and `NfoParser.java:280` (episode
resolution)

When `{episode}.archos.nfo` exists, discovery takes the custom branch and **never**
searches for `tvshow.nfo` (that lookup only happens in the plain `.nfo` branch).
Episode resolution then only tries `{show}-tvshow.archos.nfo`; if that custom show
file is absent, `nfo.isShow()` is false and the episode is discarded — even though
a valid `tvshow.nfo` sits alongside it or in the parent folder.

**Suggested fix:** perform the `tvshow.nfo` (same + parent folder) lookup in the
custom branch too, so episode resolution can fall back to it.

---

## P1 - Parser is vulnerable to XXE / entity expansion (validated by both) [DONE]

**Status:** implemented. DOCTYPE/external general+parameter entities and external
DTD loading are disabled in `getNewParser()`. Covered by
`NfoParserTest.rejectsDoctypeAndExternalEntities`.

**Where:** `NfoParser.getNewParser()` (`NfoParser.java:143`)

The SAX factory parses untrusted sidecars (SMB/network/USB) with no protections:
DOCTYPEs, external general/parameter entities, and external DTD loading are all
enabled. A malicious NFO can attempt local file disclosure (XXE), SSRF, or
billion-laughs DoS.

**Suggested hardening** (wrap each `setFeature` defensively; not all are supported
on every Android XML stack):

- `http://apache.org/xml/features/disallow-doctype-decl` = `true`
- `http://xml.org/sax/features/external-general-entities` = `false`
- `http://xml.org/sax/features/external-parameter-entities` = `false`
- `http://apache.org/xml/features/nonvalidating/load-external-dtd` = `false`

NFO files have no legitimate need for DTDs/external entities.

---

## P2 - Malformed NFO can contaminate the next import in a shared context [DONE]

**Status:** implemented. Handlers are cleared before each parse (and the root
handler defensively). Covered by
`NfoParserTest.malformedMovieDoesNotContaminateFollowingEpisode`.

**Where:** `NfoParser.java:253` (parse then `clear()` after success) and
`NfoRootHandler.java:70` (`getResult` checks movie before episode)

Handlers are reused within an `ImportContext`. `rootHandler.clear()` only runs
**after** a successful parse. Sub-handler `clear()` is wired to `startFile()`, but
in SAX sub-parse mode `startDocument` is not re-fired on the sub-handler, so a
`SAXException` mid-parse can leave a partial `mMovie`/`mResult` set. Because
`getResult()` checks the movie handler first, a malformed movie followed by a valid
episode (in the same reused context) can return the stale movie.

This is mitigated on the `AutoScrapeService` path because it uses a fresh
`ImportContext` per file (see P2-performance below), but it is real on batch /
network-scanner paths that reuse the context.

**Suggested fix:** clear handlers before parsing and again in a `finally` block.

---

## P2 - Concurrent same-file export writes; `ExportContext` cache unused [DONE]

**Status:** implemented. Exports are serialized on a single-thread executor with a
FIFO `awaitPendingExports()` completion barrier; show-NFO writes are deduplicated
only after a successful write. Covered by
`NfoWriterTest.awaitPendingExportsIsAFifoBarrier` and
`failedShowWriteIsNotDeduplicatedAndCanRetry`.

**Where:** `NfoWriter.java:54` (`ExportContext`) and `NfoWriter.java:395`
(`export` spawns a new thread)

Every `export()` starts a new unmanaged thread. When exporting episodes of one
show, multiple threads concurrently write the **same** show NFO and show poster.
`ExportContext` claims to prevent repeated show exports, but its cache is never
consulted or populated, so the de-duplication does not actually happen.

**Suggested fix:** serialize exports (or per-target locking) and actually populate
/ consult the `ExportContext` cache before writing a show NFO/poster.

---

## P2 - Episode `runtime` does not round-trip [DONE]

**Status:** implemented. `NfoEpisodeHandler` now has a `runtime` (minutes) key.
Covered by `NfoEpisodeHandlerTest.importsRuntimeInMinutes` and
`NfoWriterTest.episodeWritesRuntimeInMinutes`.

**Where:** `NfoWriter.java:212` (writes `<runtime>` in minutes) and
`NfoEpisodeHandler.java:85` (only recognizes nested `<durationinseconds>`)

The episode writer emits `<runtime>` in minutes, but `NfoEpisodeHandler` has no
`runtime` key — it only reads
`<fileinfo><streamdetails><video><durationinseconds>`. Nova's own exported episode
runtime is therefore lost on re-import.

**Suggested fix:** add a `runtime` (minutes) key to `NfoEpisodeHandler`, matching
the movie handler.

---

## P2 - TV show `<year>` is ignored [DONE]

**Status:** implemented. `NfoShowHandler` now has a `year` key; `<year>` sets a
synthetic `YYYY-01-01` premiered date only when no usable `<premiered>` was
parsed (order-independent). Covered by `NfoShowHandlerTest`.

**Where:** `NfoShowHandler` has a `premiered` key but no `year` key;
`ShowTags` derives the displayed year from `mPremiered`.

Issue [#1782](https://github.com/nova-video-player/aos-AVP/issues/1782) reports a
correct `<year>1992</year>` in `tvshow.nfo` but no year in Nova. This follows
directly from the handler: the element is ignored, leaving `mPremiered` unset.
Kodi-style NFOs may contain both fields, and sparse/generated files frequently
contain only `<year>`.

**Suggested fix:** continue preferring a valid ISO `<premiered>` date, but accept
`<year>` as a fallback when `premiered` is absent. Since `ShowTags` currently only
stores a date, either represent a year-only value explicitly in the model/DB or
use a documented synthetic date such as `YYYY-01-01` solely as a compatibility
fallback. The former avoids pretending an exact premiere date is known.

---

## P2 - Local show backdrop discovery is broken for standard filenames [DONE]

**Status:** implemented. `findBackdrop()` now probes static names directly,
adds `background.jpg`/`.png`, and retries the parent show folder only for
shows/episodes (new `searchParentFolder` flag) so a movie does not inherit a
sibling-spanning `Movies/fanart.jpg`. Covered by `LocalImagesTest`.
`banner.jpg`/`backdrop.jpg` remain intentionally unsupported.

**Where:** `LocalImages.findBackdrop()` and
`LocalImages.MATCH_LIST_BD_STATIC`.

The static list contains `fanart.jpg`/`.png`, but the lookup incorrectly calls
`getIfAvailable(parent, nameNoExt + extension)`. For an episode named
`X-Men.S01E01.mkv`, it therefore probes `X-Men.S01E01fanart.jpg` rather than
`fanart.jpg`. Unlike `findShowPoster()`, backdrop discovery also does not retry the
parent show folder when the episode is in a season subdirectory. These two defects
explain the missing background in issue #1782 even though `fanart.jpg` was
provided.

`banner.jpg` and `backdrop.jpg` are not currently recognized either. A banner is
not semantically a backdrop and should not be silently treated as one, but
`background.jpg` is Plex's documented backdrop name and `backdrop.jpg` is a common
compatible alias.

**Suggested fix:**

1. Probe static names directly (`getIfAvailable(parent, extension)`).
2. For show artwork, repeat the static lookup in the parent show folder, matching
   `findShowPoster()` behavior.
3. Add `background.jpg`/`.png`; consider `backdrop.jpg`/`.png` as explicit aliases.
4. Keep banner handling separate unless Nova gains a banner artwork type.

Add SMB tests for an episode in `Show/Season 01/` with `fanart.jpg` and
`background.jpg` stored in `Show/`.

---

## P2 - Season poster discovery misses the modern `seasonNN-poster.jpg` convention [DONE]

**Status:** implemented. `findSeasonPoster()` now also probes
`season{NN}-poster.jpg`/`.png` in the episode's own folder and, for episodes in
season subfolders, in the show root (mirroring `findBackdrop()`'s parent-folder
retry). The legacy `season{NN}.tbn`/`.jpg`/`.png` names are now also retried in
the show root as a fallback. Covered by `LocalImagesTest.findsSeasonPosterInVideoFolder`
and `findsShowRootSeasonPosterFromSeasonFolder`.

**Where:** `LocalImages.findSeasonPoster()`.

Reported by a user: `season01-poster.jpg` did not work. Nova only recognized its
own `{ShowTitle}-season{NN}.archos.jpg` and the legacy XBMC `season{NN}.tbn`/`.jpg`/
`.png` names (no `-poster` suffix), checked only in the episode's own folder. Kodi's
current documented convention is `season{NN}-poster.jpg`, stored in the **show
root** even when episodes sit in per-season subfolders — a location Nova never
checked for season posters.

---

## P2 - No-artwork fallback test mishandles empty (non-null) lists (validated by both) [DONE]

**Status:** implemented. The fallback decision now treats null and empty
equivalently via `AutoScrapeService.hasNoUsableNfoPoster(...)`. Note this only
fixes the empty-vs-null detection; the broader P1 (artwork must not gate import
success at all) is still open.

**Where:** `NfoMovieHandler.java:503` (poster list build) and
`AutoScrapeService.java:861`

The fallback decision tests `tags.getPosters() == null`. Non-HTTP `<thumb>`
entries can produce an **empty but non-null** poster list, so the intended
"no poster → fallback" path never fires.

**Suggested fix:** treat null and empty equivalently
(`posters == null || posters.isEmpty()`). Note this interacts with P1: once import
success no longer depends on artwork, this branch's role shrinks to artwork-only
fetch.

---

## P2 - Performance: per-file `ImportContext` defeats the caches

**Where:** `NfoParser.getTagForFile(Uri, Context)` (`NfoParser.java:235`) called
from `AutoScrapeService.java:816` (and `IndexHelper`)

The one-arg entry point creates a fresh `ImportContext` per call, so `showCache`
and `seasonPosterCache` (`NfoParser.java:139-140`) never survive between episodes:
`tvshow.nfo` is re-parsed and show/season posters re-searched per episode, which
multiplies network round-trips for large seasons.

Batch and network-scanner paths already reuse an `ImportContext`; the fresh-context
problem specifically remains in `AutoScrapeService` and `IndexHelper`.

**Suggested fix:** thread a single `ImportContext` through the per-directory loop
and pass it to the existing `getTagForFile(NfoFile, Context, ImportContext)`
overload. The plumbing already exists.

---

## P3 - Performance: redundant existence stats in discovery

**Where:** `NfoParser.determineNfoFile()` + `fileOk()` (`NfoParser.java:175-233`)

Discovery issues up to ~4 sequential `fileOk()` stats per video, each a network
round-trip on remote sources. Lower priority than the caching fix, which removes
most of the per-episode repetition.

**Suggested fix:** list the parent directory once and resolve candidates from the
listing where the source supports it.

---

## P3 - `<set>` collection block always emitted on export [DONE]

**Status:** implemented. The `<set>` block is only written when the movie has a
collection id or name. Covered by `NfoWriterTest.movieWithoutCollectionOmitsSet`
and `movieWithCollectionWritesSet`.

**Where:** `NfoWriter.writeXmlInner(MovieTags)` (`NfoWriter.java:147-157`)

The `<set>` block is always written, even when the movie has no collection,
producing a hollow element.

**Suggested fix:** only open `<set>` when a collection id or name is present.

---

## P3 - Inconsistent error reporting

**Where:** `NfoParser.fileOk()` (`NfoParser.java:230`) and
`NfoWriter.exportInternal(...)` (`NfoWriter.java:322, 355`)

A few paths use `e.printStackTrace()` instead of the SLF4J `log` used elsewhere,
bypassing the app's logging/crash configuration.

**Suggested fix:** route through `log`.

---

# Standard compliance (Kodi / Plex / Jellyfin / Emby interop)

The items above concern Nova's internal import/export correctness. This section
records where Nova's on-disk format diverges from the production Kodi-style NFO
formats read by Kodi, Plex's NFO agent, Jellyfin and Emby. These consumers overlap,
but they are not one perfectly versioned standard: field and watch-state support
differs between products. Kodi and Plex are the primary compatibility targets
below; Jellyfin explicitly documents additional provider-id and legacy tags, while
Emby compatibility must be tested rather than assumed.

Nova's `*.archos.nfo` profile is an internal, round-trippable backup format. It is
not currently a portable Kodi/Plex profile, even if standard tags are added to its
XML, because third-party scanners do not discover Nova's custom filenames.

Reference: [Kodi NFO/Movies](https://kodi.wiki/view/NFO_files/Movies),
[Kodi NFO/TV shows](https://kodi.wiki/view/NFO_files/TV_shows),
[Kodi NFO/Episodes](https://kodi.wiki/view/NFO_files/Episodes),
[Plex NFO metadata](https://support.plex.tv/articles/using-nfo-metadata-files-with-plex/),
[Jellyfin local NFO metadata](https://jellyfin.org/docs/general/server/metadata/nfo/).

The gaps split into three groups: **(A)** data already present in Nova tags and
therefore available to a compatibility writer/parser; **(B)** media-library data
that needs additional plumbing into `NfoWriter`; and **(C)** metadata not currently
stored, requiring tag-model, scraper and database work.

## P1 - Standard files are not discoverable outside Nova (biggest interop gap)

**Where:** `NfoParser`/`NfoWriter` custom extensions and
`NfoWriter.exportInternal(...)` target construction.

Nova exports `{video}.archos.nfo` and `{encodedShow}-tvshow.archos.nfo`. Kodi,
Plex and Jellyfin discover standard names such as `{video}.nfo`, `movie.nfo`,
`tvshow.nfo` in the show root, and `{episode}.nfo`. A file named `.archos.nfo`
will not become consumable merely by adding `<uniqueid>` or other standard tags.
The show export is additionally written beside the episode passed to the writer,
which may be a season directory rather than the show root expected for
`tvshow.nfo`.

**Suggested fix:** keep the current Archos profile unchanged for safe internal
round-trip, and add an explicit **Kodi/Plex-compatible export profile** that writes
standard filenames/locations. It must not silently overwrite an existing
user-managed plain `.nfo`; require an explicit overwrite policy or skip with a
clear result. Import should continue accepting both profiles.

## P1 - `<uniqueid>` read [DONE]; write still pending

**#1782 relevance:** the reporter's archive uses `<uniqueid type="tmdb">`
*exclusively* with no legacy `<id>`/`<tmdbid>`, so before this fix every TMDb id in
those files was dropped on import. Reading type-aware `<uniqueid>` was the first of
the two changes required for #1782 (the second is the P1 artwork-gating fix).

**Read side (DONE):** the movie/show/episode handlers now parse type-aware
`<uniqueid>` (`tmdb` -> online id, `imdb` -> imdb id), preferring it over legacy
`<id>`/`<tmdbid>`/`<imdbid>` regardless of element order, ignoring unknown types,
with modern/legacy precedence tests.

**Write side (still pending):** `NfoWriter.java:133-134, 215, 250-252` still writes
only `<id>` plus provider-specific `<tmdbid>`/`<imdbid>`; no `<uniqueid>` is emitted.

Kodi's modern standard and Plex's NFO agent key off
`<uniqueid type="tmdb" default="true">`, `type="imdb"`, `type="tvdb"`. Nova writes
legacy/untyped `<id>` plus provider-specific `<tmdbid>`/`<imdbid>`. Some consumers
(notably Jellyfin) accept provider-specific tags, but Kodi and Plex recommend
typed `<uniqueid>`, and Plex uses it to build a stable GUID. Nova already holds
`getOnlineId()` (TMDb) and `getImdbId()` for movie/show/episode — group **A**.

**Suggested fix:** emit `<uniqueid type="tmdb" default="true">` and
`<uniqueid type="imdb">` for all three types (keep `<id>`/`<tmdbid>` for back-compat),
and add a `uniqueid` parser that honours the `type` attribute. Only mark TMDb as
default when it is present; otherwise IMDb can be default. Do not infer TVDB from
Nova's generic online id: the current scraper data is TMDb.

## P1 - Movie plot written to `<outline>` instead of `<plot>`

**Where:** `NfoWriter.java:123` (`textTag(serializer, "outline", tag.getPlot())`).

Movies write the synopsis into `<outline>` only; episodes and shows correctly use
`<plot>`. Kodi/Plex use `<plot>` as the main description and treat `<outline>` as
a short summary, so the movie description is not reliably imported externally.
Group **A**.

**Suggested fix:** write the plot to `<plot>` for movies (optionally keep
`<outline>` too). Nova's own movie handler already reads both.

## P2 - Modern rating block not written or read

**Where:** all writers emit/read a scalar `<rating>`.

Nova already stores one rating value (`BaseTags.mRating`), so the nested structure
is group **A**, not group C. Kodi and Plex use:

```xml
<ratings>
  <rating name="themoviedb" max="10" default="true">
    <value>7.5</value>
  </rating>
</ratings>
```

Vote counts are unavailable, but they are not required to emit the value. The
provider can be identified as `themoviedb` for online-scraped Nova tags; imported
tags without provenance may need an `unknown`/legacy path rather than being
mislabelled.

**Suggested fix:** write the modern block in the compatibility profile and retain
the scalar rating in the Archos profile. Parse both, preferring the declared
default rating, then TMDb, then the first usable value.

## P2 - Writers emitted as `<writer>`; Kodi/Plex use `<credits>`

**Where:** `NfoWriter.java:131-132, 223-224, 257-258`.

For movies and episodes, Plex and Kodi read writing credits from repeated
`<credits>` tags; Nova emits `<writer>`, which round-trips internally. Their
documented TV-show profiles do not list show-level writer/director fields.
Jellyfin accepts both `<writer>` and `<credits>`, illustrating why the products
cannot be treated as identical. Group **A**.

**Suggested fix:** emit `<credits>` alongside `<writer>` for movies and episodes;
add a `credits` key to those handlers. Retain show-level `<writer>` only as Archos
metadata unless a target consumer is verified.

## P2 - Movie release date needs the Kodi/Plex `<premiered>` alias

**Where:** `NfoWriter.java:122` (`<releasedate>`); movies also write `<year>`.

Kodi v20+ and Plex use `<premiered>` (full `YYYY-MM-DD`) for movies. Jellyfin and
the experimental NFOStandard v2 also accept `<releasedate>`, so `<releasedate>` is
not Nova-invented, but it is insufficient for the primary compatibility profile.
Nova holds the release date string — group **A**.

**Suggested fix:** also emit `<premiered>` from the release date; keep
`<releasedate>` for back-compat.

## P2 - Episode artwork is neither written nor read

**Where:** `EpisodeTags.getEpisodePicture()`, episode writer, and
`NfoEpisodeHandler`.

Nova stores a distinct episode still (`mEpisodePicture`) but does not emit an
episode `<thumb>` or export a standard `{episode}-thumb.jpg`; the episode SAX
handler also has no `<thumb>` key. Kodi and Plex both support episode artwork.
Group **A**.

**Suggested fix:** emit `<thumb aspect="thumb">` when an HTTP episode-picture URL
exists, optionally export the cached image using the standard sidecar name in the
compatibility profile, and parse episode thumbs on import. Keep this distinct from
the season poster currently exposed through `EpisodeTags.getPosters()`.

## P2 - Untyped TV show `<id>` is ambiguous

**Where:** `NfoWriter.java:250` (`<id>` = TMDb `getOnlineId()`).

`<id>` is deprecated/generic in current Kodi and is only a fallback in Plex.
Writing a bare numeric TMDb id loses its provider identity; it should not be
described categorically as a TVDB id because consumer behavior varies. Typed
`<uniqueid type="tmdb">` resolves the ambiguity. Group **A**.

## P3 - Poster `<thumb>` lacks `aspect="poster"`

**Where:** `NfoWriter.java:170-173, 274-282` (movie/show posters).

Kodi uses `<thumb aspect="poster" preview="...">`. Nova omits `aspect`; tools can
still use the image but `aspect` disambiguates poster vs other artwork. Group **A**,
trivial.

## P3 - Plex `background.jpg` is not discovered as local fanart

**Where:** `LocalImages.MATCH_LIST_BD_STATIC`.

Nova checks `fanart.jpg/png`, while Plex documents `background.jpg` for movie and
show backgrounds. This alias is part of the broader confirmed
`LocalImages.findBackdrop()` correctness defect above: the current static lookup
is malformed and lacks the show-parent fallback. Fix the lookup first, then add
`background.jpg`/`.png`. Group **B** (filesystem plumbing, no scraper database
migration).

## P3 - Playback state is Archos-specific, not a standard representation

**Where:** `NfoWriter.java:125-127, 216-218` (`<lastplayed>`, `<bookmark>`,
scalar `<resume>`).

Kodi uses a formatted `<lastplayed>` value, `<playcount>`, and nested
`<resume><position>/<total></resume>`. Nova writes epoch seconds and flat scalar
values; `<bookmark>` is not a portable replacement. Plex's NFO agent does not
provide normal watch-state/rating synchronization, and Jellyfin user-state import
has its own constraints. These fields should remain explicitly Archos-only unless
a compatibility serializer is given access to total duration/play count and emits
the consumer-specific structure. Group **B**, not merely a writer alias.

## P3 - Technical stream details are available but low value to export

Kodi can import `<fileinfo><streamdetails>` (codec, dimensions, audio and subtitle
languages). Nova has much of this in the media database, not in `BaseTags`, so it
would require group-B plumbing through the export API. Plex/Jellyfin scan the media
file themselves and stale technical metadata is worse than omission; defer unless
offline Kodi library reconstruction is a stated goal.

## P3 - Fields not stored, so not writable without scraper/DB work (group C)

Nova does not scrape/store these, so emitting them needs upstream work first:
`<originaltitle>`, `<sorttitle>`, `<tagline>`, `<country>`, `<originallanguage>`,
show `<status>`, user `<tag>`, rating votes/provider provenance, actor `<thumb>`
and `<order>`, and named seasons. `dateadded`, play count and some stream data may
exist elsewhere in the media database, so those are group B rather than truly
unstored. Original title, tagline, country/language and show status are the most
useful future TMDb/tag/database additions; `<top250>` is low value.

## P3 - Multi-episode files are not representable in Nova's current model

Plex currently accepts multiple `<episodedetails>` blocks in one NFO. Kodi v22
instead documents separate `-SxxEyy.nfo` sidecars and no longer supports stacked
episode blocks. Nova stores one episode row per video (`EPISODE.video_id` is
unique), and `NfoRootHandler` returns one result. Supporting either external model
requires an explicit database/import design; document the limitation rather than
partially parsing the first episode.

## Separate concern - NFOStandard v2 is not the Kodi/Plex wire format

The bundled `external/NFOStandard` v2.0 uses a namespaced
`<root><media><movie|tvshow>...` document, structured people/content ratings and
attribute-based media elements. Its README states that v1 had no known production
implementations. Nova/Kodi/Plex use flat `<movie>`, `<tvshow>` and
`<episodedetails>` roots. Do not mix NFOStandard-v2 elements into the existing SAX
profile or claim schema conformance; add a separate profile/parser only if there
is a concrete consumer.

**Compliance summary:** first add a discoverable compatibility export profile.
Within that profile, the cheap high-impact group-A set is typed `<uniqueid>`, movie
`<plot>`, modern ratings, `<credits>`, movie `<premiered>`, episode artwork and
`<thumb aspect>`. Parser support for the same standard tags improves imports from
other tools. Additive XML inside `.archos.nfo` preserves Nova compatibility but,
by itself, does **not** provide third-party interoperability.

Compatibility work needs golden-file tests for each profile: standard filename
and show-root placement, typed-ID precedence, modern and scalar ratings,
`credits`/`writer` aliases, `premiered`/`releasedate` aliases, movie plot,
movie/show/episode artwork, and old-Archos-to-new-Nova round-trip. At least one
Kodi/Plex-style fixture should be imported, and compatibility exports should be
asserted not to overwrite a pre-existing plain `.nfo`.

## Requirement - smooth evolution / backward compatibility

Any change in this section **must provide a smooth upgrade path** from the current
release and from NFOs already exported by older Nova versions. A user updating Nova
must not lose metadata or be forced to re-scrape an existing library.

Concretely, a new Nova version **must**:

1. **Read the old format unchanged.** All Nova-specific tags that older releases
   wrote — `<tmdbid>`, movie `<outline>`, `<writer>`, `<releasedate>`, TV show
   `<id>` (TMDb numeric), the extended `<set>` block, flat `<resume>`/`<bookmark>`/
   `<lastplayed>` — must continue to be parsed. Existing SAX keys for these stay in
   place; new keys are *added*, never *replaced*.

2. **Keep the Archos writer additive.** If standard aliases are added to
   `.archos.nfo`, emit them in addition to legacy tags (for example typed
   `<uniqueid>` plus `<tmdbid>`, `<plot>` plus `<outline>`, `<credits>` plus
   `<writer>`, and `<premiered>` plus `<releasedate>`). This preserves old Nova
   readers, but is not a substitute for the separately named compatibility export.

3. **Prefer standard on read, fall back to legacy.** When both a standard and a
   legacy tag are present, prefer the standard one (e.g. `<uniqueid type="tmdb">`
   over `<tmdbid>`) but fall back to the legacy tag when the standard is absent, so
   old hand-crafted and Nova-exported files still resolve fully.

4. **Not require re-export.** Old on-disk NFOs remain valid; upgrading Nova does not
   invalidate or rewrite them. Re-export only happens through the normal
   user-triggered/auto-export flow. Standard-profile export must never overwrite a
   user-owned plain `.nfo` without an explicit policy.

This requirement is a hard constraint on the group-A work above: implement import
as *read old + new*, preserve the Archos profile, and expose standard export as an
intentional profile rather than silently switching filenames or schemas.

---

## Resolved / non-issues

- **Non-null `ShowTags` deref** (`AutoScrapeService.java:832, 861`): currently safe
  because `NfoParser` only returns an `EpisodeTags` when its `ShowTags` is non-null
  (`NfoParser.java:291-303`). The invariant is undocumented at the call site; a
  guard or comment would harden it, but it is not a live bug.

---

## Test coverage gap

NFO-focused tests now exist under `test/` (`NfoMovieHandlerTest`,
`NfoShowHandlerTest`, `NfoEpisodeHandlerTest`, `LocalImagesTest`,
`AutoScrapeServicePersistenceTest`), covering the fixes already landed. The
following behaviors remain unprotected and would benefit from further regression
tests, ideally added alongside the fixes above:

- Discovery precedence (`.archos.nfo` vs `.nfo` vs `movie.nfo`; `tvshow.nfo`
  same/parent folder fallback, including the P1 custom-episode case).
- Malformed-parser recovery in a reused `ImportContext` (P2 contamination).
- Import/export round-trips per type (esp. episode `runtime`, P2).
- Posterless-NFO behavior (P1 + empty-list, P2).
- Issue #1782 regression: show `<year>` fallback and show-root
  `fanart.jpg`/`background.jpg` for an episode inside a season folder are covered
  (`NfoShowHandlerTest`, `LocalImagesTest`); type-aware `<uniqueid>` precedence is
  covered (`NfoMovieHandlerTest`, `NfoShowHandlerTest`, `NfoEpisodeHandlerTest`);
  and the online-identification decision that keeps a posterless saved NFO
  authoritative (including under "rescrape all") is covered by
  `AutoScrapeServicePersistenceTest.shouldIdentifyOnline*`. Still needed: an
  end-to-end fixture proving the curated season/episode values themselves survive
  a posterless import (commit 3).

---

## Notes

- **P1 items are the priority.** The `tvshow.nfo` fallback, XXE, type-aware
  `<uniqueid>` reading and artwork-gating items are now done; together the latter
  two resolve #1782 by stopping a curated NFO from being re-identified online
  (even during "rescrape all"). The remaining live P1 work is interop-facing:
  writing `<uniqueid>` and a discoverable standard export profile.
- Items are largely independent and can be addressed in isolation.
- The internal correctness fixes can remain backward compatible. Standard
  interoperability does affect filenames and/or XML, so it must follow the
  separate-profile and smooth-evolution requirements above.
