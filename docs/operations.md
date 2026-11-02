# Running MiniSearch

Build an index before starting the HTTP service. Use JDK 21 or newer for source
builds and Java 21 or newer to run the executable JAR or extracted distribution.
No database or Docker setup is required.

```powershell
java -jar target/minisearch-0.1.0-SNAPSHOT.jar index --source documents --index search-index.bin
java -jar target/minisearch-0.1.0-SNAPSHOT.jar serve --index search-index.bin
```

The default binding is `127.0.0.1:8080`. `--host` and `--port` override it.
Ctrl+C stops the service. Keep the serving process and source/index paths under
an account with the minimum filesystem access needed for that deployment.

## Refresh while serving

Run the `index` command again against the same source root and snapshot. The
service notices atomic replacements on subsequent requests. Existing requests
can finish against their prior immutable view. No restart is needed for a normal
refresh. Keep one indexing writer per snapshot.

`GET /api/v1/health` returns `{"status":"ok"}` only when the saved index is
available. A missing, corrupt, or unsupported replacement returns HTTP 503 with
`Retry-After: 1`. Restore a valid snapshot or follow the [recovery guide](index-format.md).

## Deployment boundaries

The API is read-only and serves JSON over plain HTTP. It has no authentication,
TLS termination, CORS policy, or request-rate controls. Keep the loopback default
for local use. Shared deployments need access control and resource limits in
their surrounding network or reverse proxy.

Snapshots contain original document content for snippets. Do not publish private
snapshots, upload them in bug reports, or store them in a public web directory.
Search results expose relative filenames and text excerpts.

The index is held in memory and is proportional to corpus size. For a larger
corpus, choose a heap budget appropriate for the host, for example
`java -Xmx1g -jar ...`. That example is a heap setting, not a measured capacity
claim. No benchmark results are published for this version.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Java not found or too old | Set `JAVA_HOME` to a Java 21+ installation; use a JDK for building |
| First verification cannot download Maven | Check access to Maven Central and proxy settings |
| `Unsupported index version` | Rebuild a new version 2 snapshot from the source root |
| `Cannot read document` | Check UTF-8 encoding, permissions, and the 8 MiB document limit |
| Invalid document path | Use portable relative names without drive prefixes, backslashes, or control characters |
| Port already in use | Stop the conflicting process or choose another `--port` |
| Prefix expands past 64 terms | Use a longer prefix or request suggestions |
| No results after editing files | Refresh the saved index; the server does not scan source directories |
| Phrase behaves differently in PowerShell | Use URL-encoded HTTP requests or PowerShell 7.3+ native argument passing |

CLI exit codes are `0` for success, `1` for file or network failures, `2` for
invalid arguments or queries, and `130` when the serving command is interrupted.
HTTP errors contain a JSON `error` with `code` and `message`; unexpected failures
are logged through the JDK logging facility without returning stack traces.
