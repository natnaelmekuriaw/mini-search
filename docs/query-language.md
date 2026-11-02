# Query language

Java, CLI, and REST search use the same parser. Terms are case-insensitive after
Unicode NFKC and lowercase normalization; document paths remain case-sensitive.

| Syntax | Example | Meaning |
| --- | --- | --- |
| Adjacent terms or `AND` | `java search` | Both terms must occur |
| `OR` | `java OR kotlin` | Either alternative may match |
| `NOT` or leading `-` | `java -draft` | Exclude matching documents |
| Parentheses | `(java OR kotlin) search` | Group an expression |
| Quoted phrase | `"search engine"` | Words must be adjacent and in order |
| Trailing wildcard | `jav*` | Match indexed words starting with `jav` |
| Type filter | `type:md` | Restrict to Markdown files; `txt` is also supported |
| Path filter | `path:guides/` | Restrict relative paths by textual prefix |

Negation binds first, then explicit or implicit AND, then OR. Operators are
case-insensitive. Quote an operator to search for that word: `"and"`.
Repeated negation behaves logically: `--java` and `NOT NOT java` match `java`.
Hyphens inside plain words are punctuation: `search-engine` means `search engine`.

```text
(java OR kotlin) "search engine" -draft type:md path:guides/
```

This requires the phrase, at least one language term, no `draft` term, a Markdown
extension, and a path beginning with `guides/`.

## Filters and prefixes

Each filter can appear once. Filters affect the complete expression and must stay
outside parentheses; they cannot be negated. `path:guides` also matches a path
starting with `guides-old`; use a trailing slash to restrict to a directory.
Backslashes in path filters are normalized to `/`. Absolute paths, drive prefixes,
empty components, and `.` or `..` components are rejected.

Prefix expansion includes the exact word if indexed. `jav*` can therefore match
both `java` and `javascript`. Expansions over 64 terms fail instead of silently
dropping possible matches. Use a longer prefix or request suggestions.
Wildcards are allowed only at the end of one unquoted word. Wildcards inside
phrases, leading wildcards, escaping, and fuzzy matching are unsupported.

Suggestions take one word without `*` and return normalized terms in lexicographic
order. They are not ranked by document frequency and do not accept query operators.

## Ranking and empty queries

BM25 uses `k1 = 1.2` and `b = 0.75`. Each matching positive term contributes once,
including concrete terms matched by a prefix. Phrases restrict matching without
an additional phrase boost. Results are ordered by descending score, then path.

A filter-only query returns matching documents with score zero. An exclusion-only
query returns documents outside that match, including empty documents, with score
zero. Blank input returns no results in Java and CLI search; HTTP requires a
nonblank `q` parameter. An unmatched word or prefix returns no results.

## Limits and errors

| Limit | Value |
| --- | --- |
| Query length | 4,096 UTF-16 code units |
| Searchable words, including repetitions and phrase words | 256 |
| Recursive group/negation depth | 64 levels |
| Expansion per prefix | 64 indexed terms |
| REST search page size | 1–100; default 20 |
| Suggestions | 1–20; CLI/REST default 10 |
| REST suggestion prefix length | 256 UTF-16 code units |

Java and CLI search require a positive limit and nonnegative offset; the REST
page-size maximum applies only to HTTP. Malformed syntax throws
`IllegalArgumentException` in Java, returns CLI exit code 2, or HTTP 400 with a
JSON error. Examples include `java OR`, `()`, `type:pdf`, `*java`, and an unclosed
quote. The [OpenAPI schema](openapi.yaml) specifies request and response shapes.
