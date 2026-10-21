# MiniSearch

MiniSearch is a small Java document search engine. It builds a positional inverted
index for UTF-8 text and Markdown files and ranks query results with BM25. The index
can be saved and refreshed through Java or the CLI. A versioned HTTP API
serves searches from the saved index.

## Requirements

- JDK 21 or newer
- Internet access on the first verification run to download the pinned Maven
  distribution and project test dependencies

Run the project checks from PowerShell:

```powershell
.\scripts\verify.ps1
```

The script checks for the JDK, downloads a checksum-verified Maven distribution
when needed, and runs the Maven verification lifecycle.

## Command line

After verification, run the executable JAR from PowerShell:

```powershell
java -jar target/minisearch-0.1.0-SNAPSHOT.jar index --source documents --index search-index.bin
java -jar target/minisearch-0.1.0-SNAPSHOT.jar search --index search-index.bin --query 'java "search engine" type:md' --limit 10
```

Run `java -jar target/minisearch-0.1.0-SNAPSHOT.jar --help` for usage. `index`
creates or refreshes the snapshot and reports added, changed, removed, and unchanged
document counts. `search` reads the saved snapshot and prints ranked paths, scores,
and snippets. It defaults to 20 results. Command errors return exit code 2 for
invalid arguments or queries and 1 for file errors.

## HTTP API

Start the server after creating an index:

```powershell
java -jar target/minisearch-0.1.0-SNAPSHOT.jar serve --index search-index.bin
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/search?q=java&limit=10'
```

The server binds to `127.0.0.1:8080` by default. `--host` and `--port` override
those values. Stop it with Ctrl+C. It detects snapshots replaced by the `index`
command and loads the new version on the next request.

The read-only endpoints are `GET /api/v1/health`, `GET /api/v1/index`, and
`GET /api/v1/search`. Search requires `q` and accepts `offset` (default `0`) and
`limit` (default `20`, maximum `100`). Its JSON response contains `total`, `offset`,
`limit`, and ranked `results` with `path`, `score`, and `snippet`. Invalid requests
return JSON errors with HTTP 400; unknown paths return 404, unsupported methods
return 405, and an unavailable saved index returns 503. The HTTP service does not
modify source documents or the saved index.
The complete response schema is in [docs/openapi.yaml](docs/openapi.yaml).

## Index a directory

```java
import io.github.natnaelmekuriaw.minisearch.index.InMemoryIndex;
import io.github.natnaelmekuriaw.minisearch.index.IndexBuilder;
import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.nio.file.Path;

InMemoryIndex index = new IndexBuilder().build(Path.of("documents"));
System.out.println(index.documents());
System.out.println(index.postings("search"));
System.out.println(new SearchService(index).search("java \"search engine\" type:md", 10));

IndexRepository repository = new IndexRepository();
repository.refresh(Path.of("documents"), Path.of("search-index.bin"));
System.out.println(new SearchService(repository.open(Path.of("search-index.bin")))
        .search("java", 10));
```

## Search queries

Adjacent terms use AND matching. `OR` joins alternatives, parentheses group them,
and `-` or `NOT` excludes a term, phrase, or group. Negation binds first, then
AND, then OR. For example, `(java OR kotlin) "search engine" -draft` requires
the phrase and excludes documents containing `draft`. Quoted terms must occur
in order at adjacent token positions. `type:md` and `type:txt` restrict the file extension; `path:notes/`
restricts relative paths by prefix. Each filter can appear once. Path matching is
case-sensitive, while file extensions are case-insensitive. Filters apply to the
whole query, including grouped alternatives, so they must appear outside
parentheses and cannot be negated. A filter-only query
returns matching documents with score zero; an empty query returns no results.

Results contain the relative path, BM25 score, and a short plain-text snippet near
a matching term. They are sorted by descending score and then path. Repeated query
terms contribute to the score once. BM25 uses `k1 = 1.2`, `b = 0.75`, and document
lengths measured in tokens. Quoted phrases restrict matches but receive no extra
score. Only terms in matching positive branches contribute to score and snippets.
Escaping and highlighting are not supported yet.

The scanner recursively includes `.txt` and `.md` files, regardless of extension
case. It reads Markdown as source text, uses paths relative to the selected root,
and skips symbolic links. Documents over 8 MiB or files that cannot be decoded as
UTF-8 cause the build to fail. Empty documents remain in the document list.

Terms are sequences of Unicode letters and numbers, with combining marks allowed
after the first character. Each term is normalized with NFKC and lowercased with
`Locale.ROOT`. Positions are zero-based token offsets. `postings` accepts one term
and returns an empty list when that term is absent.

`IndexRepository.refresh` creates a snapshot on the first call. Later calls compare
source contents with the saved documents, retokenize only added or changed files,
remove deleted files, and reuse postings for unchanged files. It returns counts for
added, changed, removed, and unchanged documents. Refresh still reads each source
file to detect changes. Search loads the saved snapshot without scanning or
retokenizing the source directory.

The binary snapshot includes source text for snippets, a format version, and a
SHA-256 checksum. Writes use a temporary file and atomic replacement. A damaged or
unsupported snapshot causes an error rather than an automatic rebuild. Keep the
snapshot outside the document directory or give it an extension other than `.txt`
or `.md`. The format is currently version 1, and migration between versions is not
available. Snapshots and loaded indexes still require memory proportional to the
corpus size.
