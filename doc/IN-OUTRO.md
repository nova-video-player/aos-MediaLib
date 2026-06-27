# Intro/Outro Segments — Multi-Provider Fusion (MediaLib)

## Overview

MediaLib fetches crowd-sourced intro / recap / credits / outro / preview
timestamps for the currently playing video from **two independent, GET-only,
no-auth providers** and fuses their answers into a single provider-agnostic
model (`IntroSegments`). The player (Video module) consumes only that fused
model and never sees provider specifics or the merge policy.

This document covers the **query and fusion** half (MediaLib). The
**playback skipping strategy** is documented in `Video/doc/IN-OUTRO.md`.

## Providers

| Provider | Host | Endpoint | Keys it accepts | Segment types | Cardinality |
|----------|------|----------|-----------------|---------------|-------------|
| theintrodb.org | `api.theintrodb.org/v3/` | `GET /media` | `tmdb_id` → `tvdb_id` → `imdb_id` (+ `season`/`episode` for shows, optional `duration_ms`) | intro, recap, credits, preview | list per type |
| introdb.app | `api.introdb.app/` | `GET /segments` | `imdb_id` + `season` + `episode` (TV only) | intro, recap, outro | one segment per type |

Neither provider requires an API key for these read endpoints. Both send
`User-Agent: novavideoplayer`.

### Why two providers

They are complementary, not redundant:

- **introdb.app** has the largest collection for **intro / recap** and is the
  **only** source for **outro**, but is **TV-only** (needs show imdb id +
  season + episode).
- **theintrodb.org** is the **only** source for **credits / preview**, works
  for movies as well as shows, and serves as the **fallback** for intro/recap
  when introdb.app has nothing.

## Specification

### Inputs — `IntroDbQueryParams`

A single unified query object built by the player from scraped metadata,
carrying every identifier either provider might need:

| Field | Type | Used by | Notes |
|-------|------|---------|-------|
| `tmdbId` | Integer | theintrodb.org | preferred/canonical id |
| `imdbId` | String | both | show imdb id; **required** by introdb.app |
| `tvdbId` | Integer | theintrodb.org | fallback id |
| `isShow` | boolean | both | gates season/episode handling |
| `season` | Integer | both | shows only |
| `episode` | Integer | both | shows only |
| `durationMs` | Long | theintrodb.org | optional disambiguation hint |

- `hasIdentifier()` → true if any of tmdb/imdb/tvdb is present. A query with no
  identifier is rejected before any network call.

### Per-provider preconditions

`IntroDbManager` decides per provider whether it is even worth calling:

- **introdb.app** (`fetchApp`): requires `isShow == true`, a non-empty
  `imdbId`, and `season > 0` && `episode > 0`. Otherwise skipped (returns null).
- **theintrodb.org** (`fetchOrg`): requires `hasIdentifier()`. Movies are
  allowed (no season/episode). Otherwise skipped.

### Segment semantics (nullable bounds)

- `startMs == null` → "starts at the beginning" (treated as 0).
- `endMs == null` → "runs to end of media" (treated as +infinity).
- introdb.app always supplies concrete `start_ms`/`end_ms`; a segment is
  dropped at parse time if `start < 0 || end < 0 || end <= start`.
- theintrodb.org may omit either bound (e.g. credits with `end_ms == null`).

### Outputs — `IntroSegments` (normalized model)

```
enum Type { INTRO, RECAP, OUTRO, CREDITS, PREVIEW }

Segment {
    Long   startMs          // null = start of media
    Long   endMs            // null = end of media
    double confidence       // 0 when provider does not supply it
    int    submissionCount  // 0 when provider does not supply it
    String source           // provider host the segment came from (provenance)
}
```

- Stored as a `Map<Type, List<Segment>>` (a list per type, because
  theintrodb.org may return several per type).
- `confidence` / `submissionCount` are only populated by introdb.app; they are
  `0` for theintrodb.org segments.
- `OUTRO` (introdb.app) and `CREDITS` (theintrodb.org) are kept as **distinct
  types on purpose** to preserve provenance, even though they describe the same
  end-of-episode block. The player treats both as skippable.

## Fusion Policy

Implemented in `IntroDbManager.fetch()`. Both providers are queried (blocking,
must be off the main thread), then merged into one `IntroSegments`:

```
introdb.app  (preferred for intro/recap, sole source for outro)
  ├─ INTRO   → added if present            (appIntro flag set)
  ├─ RECAP   → added if present            (appRecap flag set)
  └─ OUTRO   → added if present

theintrodb.org (fallback for intro/recap, sole source for credits/preview)
  ├─ INTRO   → added ONLY IF !appIntro     (fallback)
  ├─ RECAP   → added ONLY IF !appRecap      (fallback)
  ├─ CREDITS → always added (when present)
  └─ PREVIEW → always added (when present)
```

Rules:

1. **introdb.app wins intro/recap** when it returns them; theintrodb.org's
   intro/recap are only added when introdb.app returned nothing for that type.
2. **outro** comes solely from introdb.app.
3. **credits / preview** come solely from theintrodb.org.
4. The merge is **per type**, not per provider: a fused result can mix sources
   (e.g. introdb.app intro + theintrodb.org credits).
5. If neither provider yields any usable segment, `fetch()` returns **null**.

### Failure isolation

Each provider call is wrapped independently:

- A network `IOException` from one provider is logged (`warn`) and treated as
  "no result" from that provider; the other provider's result still flows
  through. The fusion never throws on a single-provider failure.
- HTTP status is normalized per helper (`200` OK, `400` bad request, `404` not
  found → quietly "no data", `429` too many requests, `5xx` server issue).

## Implementation

### Components

| Class | Role |
|-------|------|
| `IntroDbManager` | Multi-provider facade: per-provider preconditions, dual fetch, fusion policy. The only class the player calls. |
| `IntroDbQueryParams` | Unified input identifiers (built by the player from scraper metadata). |
| `IntroDbApiHelper` | theintrodb.org `GET /media` client + JSON parsing → `IntroDbResult`. |
| `IntroDbResult` | theintrodb.org parsed response (lists per type, nullable bounds). |
| `IntroDbAppApiHelper` | introdb.app `GET /segments` client + JSON parsing → `IntroDbAppResult`. |
| `IntroDbAppQueryParams` | introdb.app-specific input (imdb id + season + episode). |
| `IntroDbAppResult` | introdb.app parsed response (one segment per type, confidence/count). |
| `IntroSegments` | Provider-agnostic fused model returned to the player. |

### HTTP clients

Both helpers are singletons sharing the scraper's OkHttp **disk cache**
(`ScraperCache.getCache(context)`), wired the same way as the TMDb/MyTmdb
helpers:

- `init(Context)` is called once (by the player's `PlayerService.onCreate`,
  via `IntroDbManager.init`). Without it, a no-cache client is lazily created.
- A `ScraperCache.CacheInterceptor` rewrites cache-control and skips caching
  error responses (e.g. 404/401).
- When **TRACE** logging is on, an HTTP body logger and a cache-hit logger are
  attached for debugging.
- Connect/read timeouts come from `ScraperCache`.

### Request construction

- **theintrodb.org** (`getMedia`): adds `tmdb_id`, `tvdb_id`, `imdb_id` when
  present; adds `season`/`episode` only for shows; adds `duration_ms` when > 0.
- **introdb.app** (`getSegments`): always adds `imdb_id`, `season`, `episode`
  (validated by `IntroDbAppQueryParams.isValid()` before the call).

### Parsing

- theintrodb.org: `intro`/`recap`/`credits`/`preview` are JSON **arrays**;
  each element yields a `Segment(start_ms, end_ms)` with nullable longs.
- introdb.app: `intro`/`recap`/`outro` are single JSON **objects**; each yields
  a `Segment(start_ms, end_ms, confidence, submission_count)` or null if the
  bounds are missing/invalid.

### Threading

`IntroDbManager.fetch()` is **blocking** and must run on a background thread.
The player runs it once per video on a dedicated `IntroDbFetch` thread, stores
the fused `IntroSegments` (or null), and discards it if the video changed
meanwhile.

### Debug / display helpers (on `IntroSegments`)

- `toDebugString(labels)` — multi-line consolidated dump (header `IntroDB`),
  used for the on-screen debug toast (TRACE only).
- `toSummaryString(labels, endLabel)` — compact one-line `/`-separated summary
  (e.g. `Intro: 0'30'' → 1'45'' / Credits: 42'31'' → end`) shown in the Play
  mode tile/menu. Labels and the end marker are caller-supplied (translatable
  in the app module), so this model hardcodes no user-facing strings.

The skip-decision API (`findSkip`) lives on `IntroSegments` but its policy
(which types, ordering, overlap merge, recap gating) is described in
`Video/doc/IN-OUTRO.md`.
