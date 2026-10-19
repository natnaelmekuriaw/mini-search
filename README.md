# MiniSearch

MiniSearch is a small Java document search engine. It currently builds an in-memory
positional inverted index for UTF-8 text and Markdown files. Ranking, persistence,
the CLI, and the REST API are planned for later stages.

## Requirements

- JDK 21 or newer
- Maven 3.6.3 or newer

Run the project checks from PowerShell:

```powershell
.\scripts\verify.ps1
```

The script checks for the required tools and runs the Maven verification lifecycle.

## Index a directory

```java
import io.github.natnaelmekuriaw.minisearch.index.InMemoryIndex;
import io.github.natnaelmekuriaw.minisearch.index.IndexBuilder;
import java.nio.file.Path;

InMemoryIndex index = new IndexBuilder().build(Path.of("documents"));
System.out.println(index.documents());
System.out.println(index.postings("search"));
```

The scanner recursively includes `.txt` and `.md` files, regardless of extension
case. It reads Markdown as source text, uses paths relative to the selected root,
and skips symbolic links. Documents over 8 MiB or files that cannot be decoded as
UTF-8 cause the build to fail. Empty documents remain in the document list.

Terms are sequences of Unicode letters and numbers, with combining marks allowed
after the first character. Each term is normalized with NFKC and lowercased with
`Locale.ROOT`. Positions are zero-based token offsets. `postings` accepts one term
and returns an empty list when that term is absent.

The build currently reads the corpus into memory and creates a fresh index each
time. It has no incremental update or saved-index format yet.
