# Demonstrate MiniSearch

Run from the repository root in PowerShell. The first build needs JDK 21 or newer
and internet access for Maven dependencies.

```powershell
./scripts/verify.ps1
./scripts/demo.ps1
```

The demo copies five sample documents into a new directory under `target/`.
It leaves the original samples unchanged and retains its saved index for later
searches. You can choose a new destination with `-Directory <path>` or an existing
executable JAR with `-Jar <path>`.

## Explain each result

| Demonstration | Expected result | What it shows |
| --- | --- | --- |
| Initial indexing | 5 added | Text and Markdown become a saved positional index |
| `java` | `guides/java.md`, then `drafts/roadmap.md` | BM25 ranks evidence and returns snippets |
| `(java OR kotlin) -draft path:guides/` | Both guides | Alternatives, exclusions, and global path filtering |
| `jav* type:txt` | `web.txt` | Prefix expansion includes `javascript` |
| Suggestions for `jav` | `java`, `javascript` | Completions come from indexed terms |
| Add Rust and refresh | 1 added, 5 unchanged | Incremental updates preserve unchanged documents |

Scores depend on the corpus and can change when documents are added. Markdown is
indexed as source text, so headings and punctuation can appear in snippets.

## Show the REST API

Copy and run the server command printed at the end of the demo. Keep that
terminal open. In a second PowerShell window:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health'
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/index'

$query = [uri]::EscapeDataString('(java OR kotlin) "search engine" -draft path:guides/')
Invoke-RestMethod "http://127.0.0.1:8080/api/v1/search?q=$query" |
    ConvertTo-Json -Depth 6

Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/suggest?q=jav' |
    ConvertTo-Json
```

Health returns `ok`. Index metadata reports six documents after the Rust update.
The phrase query returns the two guides, with total count, paging information,
scores, and snippets. Suggestions return `java` and `javascript`.

`examples/requests.http` contains equivalent requests for an editor's HTTP client.
Stop the server with Ctrl+C.

## Show pagination and errors

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/search?q=java&offset=1&limit=1' |
    ConvertTo-Json -Depth 6
```

This returns one result and retains the full match count. An invalid request such
as `GET /api/v1/search?q=java&limit=0` returns HTTP 400 with a JSON explanation.
PowerShell displays HTTP errors as exceptions; an editor HTTP client shows the
status and body directly.

PowerShell 5.1 can strip embedded quotes when passing arguments to native
programs. Use the URL-encoded HTTP example for phrase searches, or run the CLI
from PowerShell 7.3 or newer with its standard native argument passing mode.

## Describe the project

"MiniSearch indexes local text and Markdown, ranks results with BM25, and exposes
the same search core through Java, a command line, and a REST API. It supports
phrases, Boolean queries, filters, prefix search, and saved incremental indexes."
