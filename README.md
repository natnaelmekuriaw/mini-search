# MiniSearch

MiniSearch is a small Java document search engine. It builds an in-memory positional
inverted index for UTF-8 text and Markdown files and ranks query results with BM25.
Persistence, the CLI, and the REST API are planned for later stages.

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

## Index a directory

```java
import io.github.natnaelmekuriaw.minisearch.index.InMemoryIndex;
import io.github.natnaelmekuriaw.minisearch.index.IndexBuilder;
import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.nio.file.Path;

InMemoryIndex index = new IndexBuilder().build(Path.of("documents"));
System.out.println(index.documents());
System.out.println(index.postings("search"));
System.out.println(new SearchService(index).search("java \"search engine\" type:md", 10));
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

The build currently reads the corpus and retains source text in memory for snippets.
It creates a fresh index each time and has no incremental update or saved-index
format yet.
