# MediaLib developer documentation

Use this index to find the contract that owns the code you are changing. Update the
relevant document when behavior, invariants, failure handling, schema, or required
validation changes.

## Database

- [`DATABASE_UPDATE.md`](DATABASE_UPDATE.md) - adding and validating schema
  migrations in `VideoOpenHelper` and `ScraperTables`, including backward
  compatibility with every supported field version and old Android SQLite pitfalls.
- [`DATABASE_RUNTIME.md`](DATABASE_RUNTIME.md) - shared connection ownership,
  `DbHolder` recovery, destructive backup/restore locking, provider transaction
  boundaries, and runtime database failure handling.

## Indexing and metadata

- [`SCANNING.md`](SCANNING.md) - network refresh batches, per-folder indexing,
  failure aggregation, and the handoff from scanning to autoscrape.
- [`SCRAPE.md`](SCRAPE.md) - filename preprocessing, movie/TV detection, search
  candidates, and scraper matching behavior.
- [`IN-OUTRO.md`](IN-OUTRO.md) - intro/outro segment providers, fusion, persistence,
  and playback-facing behavior.

## Synchronization

- [`TRAKT.md`](TRAKT.md) - Trakt synchronization strategy, directionality, conflict
  handling, retry state, and account behavior.

## Validation

- [`TEST.md`](TEST.md) - complete MediaLib unit test command, every focused test
  class/method, fixture ownership, and Robolectric/device-validation limitations.

## Documentation rules

- Keep schema migration rules separate from runtime connection and transaction rules.
- State atomicity boundaries explicitly: a provider batch, folder scan, refresh batch,
  and process lifetime are different scopes.
- Document expected failure behavior, observability, and retry ownership, not only the
  successful path.
- Treat old Android SQLite and real filesystem behavior as device-validation concerns;
  Robolectric coverage alone is not sufficient.
- Cross-reference the focused tests that enforce each contract.
