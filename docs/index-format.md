# Saved indexes and recovery

Snapshots contain relative document paths, original source text, token counts,
and positional postings. They are portable across supported operating systems
when document paths satisfy the portable path rules. Paths use `/` and contain
no absolute roots, drive prefixes, backslashes, control characters, or `.`/`..`
components. The selected source root is not stored.

## Format version 2

All integers are signed 32-bit big-endian values. Strings contain a byte count
followed by strict UTF-8. The layout is:

1. Magic `MSIX` (`0x4d534958`) and format version `2`.
2. Document count, followed by documents in ascending path order. Each contains
   its path, token count, and original content.
3. Term count, followed by normalized terms in ascending order. Each term contains
   a posting count, then postings in ascending document-path order. A posting
   contains its path, frequency, and strictly increasing token positions.
4. A 32-byte SHA-256 digest of all preceding bytes. Additional trailing data is
   rejected.

Loading checks counts, UTF-8, ordering, normalized terms, document references,
position bounds, complete token coverage, and the checksum. A token position
cannot belong to two different terms. The checksum detects corruption; it is not
a signature or proof that the file came from a trusted publisher.

The format limits include 8 MiB of UTF-8 content per document, 64 KiB per path,
one million documents, and ten million terms. Practical limits are lower and
depend on available heap memory; the entire index and source content are loaded.

## Compatibility

Version 2 fixes Unicode compatibility characters that normalize into several
searchable words. This changes token positions and can change phrase matching.
Version 1 snapshots must be rebuilt from source documents. They are rejected
instead of being silently interpreted with different tokenization rules.
There is no in-place migration utility or automatic rebuild on load failure.

## Write and refresh guarantees

The writer creates a temporary file beside the destination and atomically
replaces the snapshot only after serialization completes. Unsupported atomic
replacement causes an error; there is no partial-copy fallback. Use one writer
per snapshot. Concurrent writers are not coordinated.

Failed scanning or serialization leaves the prior snapshot intact. Temporary
files from ordinary failures are cleaned up. A process or machine crash may
leave an unreferenced temporary file. Do not treat that file as a valid backup.

Refresh reads all supported source files, compares content, and retokenizes only
added or changed documents. Deleted documents are removed. A no-change refresh
does not replace the saved file. Store snapshots outside the source directory,
or use an extension other than `.md` or `.txt` to avoid self-indexing.

## Recover an unavailable index

Preserve the damaged or old snapshot if you need to diagnose it. Build a new
snapshot at another path from the same source root:

```powershell
java -jar target/minisearch-0.1.0-SNAPSHOT.jar index --source documents --index recovered.bin
java -jar target/minisearch-0.1.0-SNAPSHOT.jar search --index recovered.bin --query java
```

After validating the replacement, stop the server and point it at the new file,
or replace its configured snapshot atomically using your filesystem tooling.
Rebuilding requires the original source documents; the service never attempts
to reconstruct missing source files. Snapshots retain original text and should
receive the same access controls as the documents they contain.
