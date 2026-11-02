# Examples

The `documents/` directory contains five small documents for demonstrations and
regression checks. Keep saved indexes outside that directory.

After building MiniSearch, run the Java API example from the repository root:

```powershell
java --class-path target/minisearch-0.1.0-SNAPSHOT.jar examples/SearchExample.java examples/documents target/example-index.bin
```

Java compiles the example in source-file mode, so no additional project or library
dependency is needed. Expect two matching guides and the suggestions `java` and
`javascript`. The example refreshes its snapshot on each run.

For the CLI and REST demonstration, follow [the walkthrough](../docs/demo.md).
The `requests.http` collection can be opened in an editor with an HTTP client.

Inside an extracted binary distribution, use `lib/minisearch.jar` as the classpath
and choose a writable snapshot path such as `example-index.bin`.
