# MiniSearch

A lightweight Java document search engine for local UTF-8 text and Markdown.
MiniSearch builds a positional inverted index, ranks matches with BM25, and
exposes one search core through a Java API, CLI, and read-only REST service.

It supports phrases, Boolean queries, path/type filters, prefix search,
suggestions, saved snapshots, and incremental updates. It has no external runtime
library dependencies and is currently a `0.1.0-SNAPSHOT` development version.

## Build and try it

Install JDK 21 or newer. Use Windows PowerShell 5.1 or PowerShell 7 on Windows,
or PowerShell 7 on Linux. The Maven wrapper downloads a checksum-pinned Maven
distribution and test dependencies on the first build.

```powershell
git clone https://github.com/natnaelmekuriaw/MiniSearch.git
cd MiniSearch
./scripts/verify.ps1
./scripts/demo.ps1
```

The demo indexes five sample files, searches and suggests terms, then adds a
document and refreshes its saved index. It keeps its working files under
`target/` and prints a command to start the API.
Follow the [demonstration walkthrough](docs/demo.md) for expected results and
copyable HTTP requests.

## Command line

```powershell
$jar = 'target/minisearch-0.1.0-SNAPSHOT.jar'
java -jar $jar index --source examples/documents --index target/search-index.bin
java -jar $jar search --index target/search-index.bin --query '(java OR kotlin) -draft path:guides/'
java -jar $jar search --index target/search-index.bin --query 'jav* type:txt' --limit 10 --offset 0
java -jar $jar suggest --index target/search-index.bin --prefix jav
java -jar $jar --version
```

Search prints ranked relative paths, scores, and snippets. Its default limit is
20 and offset is 0. Suggestions print up to 10 terms by default, with a maximum
of 20. Use `<command> --help` for that command's syntax and behavior.

Run `index` again after editing documents. It reports added, changed, removed,
and unchanged files. It still reads source files to detect changes, but retokenizes
only added or changed documents. Searches read the saved index without rescanning
the source directory.

## REST API

Start the service and keep its terminal open:

```powershell
java -jar $jar serve --index target/search-index.bin
```

From another PowerShell window:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health'
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/index'
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/search?q=jav*&limit=10' | ConvertTo-Json -Depth 6
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/suggest?q=jav' | ConvertTo-Json
```

Search responses contain `total`, `offset`, `limit`, and `results` with `path`,
`score`, and `snippet`. The server loads atomic snapshot replacements on later
requests. Stop it with Ctrl+C. `--host` and `--port` override the default
`127.0.0.1:8080` binding. The API has no authentication or TLS termination.
See the [OpenAPI contract](docs/openapi.yaml) and [operations guide](docs/operations.md).

## Java API

```java
IndexRepository repository = new IndexRepository();
repository.refresh(Path.of("documents"), Path.of("search-index.bin"));
SearchService search = new SearchService(repository.open(Path.of("search-index.bin")));
SearchPage page = search.searchPage("(java OR kotlin) -draft", 0, 10);
List<String> suggestions = search.suggest("jav", 10);
```

The [runnable Java example](examples/README.md) includes imports and a complete
entry point. The [architecture guide](docs/architecture.md) explains the index,
tokenizer, ranking, persistence, and request handling.

## Binary distributions

To build a release candidate, run `./scripts/verify.ps1 -Release`. This uses the
same verification entry point, packages ZIP and tar.gz archives, checks their
launchers, and writes SHA-256 checksums. Release verification also requires `tar`.

An extracted distribution needs Java 21 or newer. Use `bin/minisearch.cmd` on
Windows or `bin/minisearch` on Linux. You can also run `java -jar lib/minisearch.jar`.
Samples and documentation are included; no Java runtime is bundled.
See [release preparation](docs/releasing.md) for candidate artifacts and publishing.

## Limits and compatibility

- Input is strict UTF-8; `.txt` and `.md` extensions are case-insensitive.
  Each document is limited to 8 MiB. Symbolic links are skipped.
- Markdown is indexed as source text. The index and original document content
  remain in memory, so capacity depends on available heap.
- Queries allow 4,096 UTF-16 code units and 256 searchable words. Prefix expansion
  is capped at 64 terms. Wildcards inside phrases, escaping, and fuzzy matching
  are unsupported.
- Snapshots use format version 2. Version 1 must be rebuilt from source because
  tokenization of Unicode compatibility expansions changed.
- Use one snapshot writer at a time. Snapshots contain original document text
  and should receive the same access controls as the source documents.

CI verifies Windows/Linux builds with Java 21 and 25 using the same script.
No performance benchmark results are claimed for this version.

## Documentation

| Guide | Purpose |
| --- | --- |
| [Demonstration](docs/demo.md) | Run and present a practical example |
| [Query language](docs/query-language.md) | Syntax, precedence, limits, and ranking |
| [Architecture](docs/architecture.md) | Package responsibilities and data flow |
| [Snapshot format](docs/index-format.md) | Compatibility, consistency, and recovery |
| [Operations](docs/operations.md) | Deployment boundaries and troubleshooting |
| [Release preparation](docs/releasing.md) | Build, inspect, and publish artifacts |
| [Contributing](CONTRIBUTING.md) | Develop and submit focused changes |
| [Changelog](CHANGELOG.md) | Features and compatibility changes |

MiniSearch is distributed under the [MIT license](LICENSE).
