# Changelog

## Unreleased

### Search and interfaces

- UTF-8 text and Markdown scanning, Unicode normalization, and a positional index.
- BM25 ranking, phrases, Boolean grouping/exclusions, path and type filters.
- Bounded prefix search and indexed-term suggestions.
- Saved incremental indexes, CLI commands, and a versioned read-only REST API.

### Release readiness

- Cross-platform verification and Windows/Linux CI for Java 21 and 25.
- Packaged CLI smoke checks, runnable Java/CLI/API examples, and a sample corpus.
- ZIP and tar.gz distributions with launchers and SHA-256 checksums.
- CLI pagination, command-specific help, and packaged version reporting.
- Bounded source reads, portable path validation, deterministic score summation,
  and stricter stored-posting validation.
- Contribution, architecture, query, recovery, and operational documentation.

### Compatibility

- Snapshot version 2 replaces version 1. Rebuild existing snapshots from source
  documents because Unicode compatibility expansions now produce consistent
  token positions and phrase matching.
- Queries are limited to 4,096 UTF-16 code units and 256 searchable words.
- Repeated leading exclusions now behave consistently with repeated `NOT`.
- Path filters reject absolute, traversal, and ambiguous relative prefixes.

No tagged release has been recorded in this changelog yet.
