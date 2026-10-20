# MiniSearch

MiniSearch is a small Java document search engine. It builds a positional inverted
index for UTF-8 text and Markdown files and ranks query results with BM25. The index
can be saved and refreshed through Java or the CLI. The REST API is planned
for a later stage.

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

Plain terms use AND matching. Quoted terms must occur in order at adjacent token
positions. `type:md` and `type:txt` restrict the file extension; `path:notes/`
restricts relative paths by prefix. Each filter can appear once. Path matching is
case-sensitive, while file extensions are case-insensitive. A filter-only query
returns matching documents with score zero; an empty query returns no results.

Results contain the relative path, BM25 score, and a short plain-text snippet near
a matching term. They are sorted by descending score and then path. Repeated query
terms contribute to the score once. BM25 uses `k1 = 1.2`, `b = 0.75`, and document
lengths measured in tokens. Quoted phrases restrict matches but receive no extra
score. Boolean OR, exclusions, escaping, and highlighting are not supported yet.

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
