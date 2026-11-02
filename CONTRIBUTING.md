# Contributing to MiniSearch

MiniSearch is a Java 21 document search engine with one search core shared by
Java callers, the CLI, and a read-only HTTP service. Small, focused contributions
are easier to review. Discuss changes to query semantics, storage, dependencies,
or public APIs in an issue before implementing them.

## Work locally

Install JDK 21 or newer. Verification uses Windows PowerShell 5.1 or PowerShell 7
on Windows, and PowerShell 7 on Linux. Maven is downloaded by the checksum-pinned
wrapper on the first build; a separate Maven installation is unnecessary.

```powershell
./scripts/verify.ps1
```

This is the complete verification entry point. It builds the JAR, runs tests,
and exercises saved-index commands. For a focused iteration on Windows:

```powershell
./mvnw.cmd -o '-Dtest=QueryParserTest' test
```

On Linux, use `./mvnw` instead. Offline execution requires already-cached
dependencies. Run the complete verification script before submitting a PR.

## Make a focused change

- Use `feat/descriptive-name` for features and `fix/descriptive-name` for fixes.
- Keep commit subjects short and lowercase. Each commit should express a useful
  change; do not split work solely to increase the commit count.
- Follow the existing four-space Java style and preserve UTF-8 text.
- Add regression coverage for changed behavior, especially query precedence,
  ordering, persistence, and error responses. Avoid timing-dependent assertions.
- Keep CLI, Java, and REST behavior consistent where they expose the same
  operation. Update the query guide or OpenAPI schema when their contract changes.
- Explain why a dependency is necessary before adding it.

The [architecture guide](docs/architecture.md) explains the package boundaries.
The [demonstration](docs/demo.md) provides a small corpus for manual exploration.

## Submit a pull request

Describe the problem, observable behavior, and checks you ran. Include the command
and relevant output for a failure, with private document contents and paths
removed. State any compatibility changes, including snapshot rebuilds.

CI runs the same verification script on Windows and Linux with Java 21 and 25.
If it fails, check the job log and downloadable test reports. Keep unrelated
cleanup out of a bug fix. Respect third-party license headers and credit actual
human collaborators when their contribution warrants co-authorship.
