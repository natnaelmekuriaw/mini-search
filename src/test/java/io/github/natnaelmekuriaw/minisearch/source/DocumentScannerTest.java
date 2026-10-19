package io.github.natnaelmekuriaw.minisearch.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentScannerTest {
    @TempDir
    Path directory;

    private final DocumentScanner scanner = new DocumentScanner();

    @Test
    void findsSupportedFilesRecursivelyInStablePathOrder() throws IOException {
        Files.createDirectories(directory.resolve("notes"));
        Files.writeString(directory.resolve("z.TXT"), "last");
        Files.writeString(directory.resolve("notes/a.md"), "first");
        Files.writeString(directory.resolve("notes/ignore.json"), "ignored");

        assertEquals(List.of(
                new SourceDocument("notes/a.md", "first"),
                new SourceDocument("z.TXT", "last")), scanner.scan(directory));
    }

    @Test
    void includesEmptyDocuments() throws IOException {
        Files.createFile(directory.resolve("empty.txt"));
        assertEquals(List.of(new SourceDocument("empty.txt", "")), scanner.scan(directory));
    }

    @Test
    void rejectsMissingRoots() {
        assertThrows(IOException.class, () -> scanner.scan(directory.resolve("missing")));
    }

    @Test
    void reportsInvalidUtf8() throws IOException {
        Files.write(directory.resolve("broken.txt"), new byte[] {(byte) 0xC3, (byte) 0x28});
        assertThrows(IOException.class, () -> scanner.scan(directory));
    }
}
