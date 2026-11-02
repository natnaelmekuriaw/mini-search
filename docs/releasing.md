# Prepare a release

Release preparation creates local artifacts. Publishing a tag or GitHub release
is a separate maintainer action. The release-candidate workflow also creates
downloadable artifacts without publishing a release.

## Choose a version

The project currently uses `0.1.0-SNAPSHOT`; this is a development version.
For a tagged release, change the Maven project version, move the relevant
changelog entries into a dated release section, and update version-specific
JAR paths in documentation. Keep API version `v1` and snapshot format version `2`
separate from the application version; change them only for their own contracts.

Use a clean checkout of the intended release commit. Review dependency changes,
compatibility notes, and the sample walkthrough before building.

## Verify and package

Run the single verification entry point with release packaging enabled:

```powershell
./scripts/verify.ps1 -Release
```

This requires JDK 21 or newer, PowerShell, and `tar` on PATH. Windows 10/11 and
GitHub-hosted Windows/Linux runners provide `tar`. The script:

1. Locates matching Java and javac installations.
2. Runs a clean Maven verification with the release profile.
3. Exercises the packaged JAR using the sample corpus and incremental refresh.
4. Extracts both archives, checks their launchers and saved-index commands, then
   writes `target/SHA256SUMS`.

For the current development version, outputs are:

```text
target/minisearch-0.1.0-SNAPSHOT.jar
target/minisearch-0.1.0-SNAPSHOT-bin.zip
target/minisearch-0.1.0-SNAPSHOT-bin.tar.gz
target/SHA256SUMS
```

Archives contain a versioned top-level directory with `bin/`, `lib/`, `docs/`,
`examples/`, the README, changelog, and MIT license. They do not include a Java
runtime. The archive launchers use `JAVA_HOME` when set, or Java from PATH.

Use a consistent JDK and the pinned Maven wrapper for repeatability. Artifact
timestamps are fixed by `project.build.outputTimestamp`; update that value when
preparing a release. Text line endings and archive permissions are explicit.
This configuration is not a claim that every JDK emits identical class files.

## Build a candidate in GitHub Actions

After pushing the intended commit, open Actions, select **release candidate**,
and run it against the intended branch. The workflow verifies and packages on
Linux with Java 21. Download the `minisearch-<commit>` artifact and inspect its
contents. The normal PR workflow verifies Windows/Linux with Java 21 and 25.

## Publish and verify downloads

After checks pass, the maintainer can create the chosen version tag and publish
the JAR, both binary archives, and `SHA256SUMS` in a GitHub release. Include the
snapshot rebuild notice in release notes. Verify the downloaded files rather
than only the files still present in the build directory.

On Linux, place all four downloaded files together and run:

```sh
sha256sum -c SHA256SUMS
```

On PowerShell, use `Get-FileHash -Algorithm SHA256 <file>` and compare each hash
with its matching line in `SHA256SUMS`. Hashes check file integrity, not publisher
identity. Start the extracted launcher with `--version` and follow the sample
walkthrough before distributing the release link.
