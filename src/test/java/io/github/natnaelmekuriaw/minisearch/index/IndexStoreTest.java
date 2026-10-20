package io.github.natnaelmekuriaw.minisearch.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexStoreTest {
    @TempDir
    Path directory;

    private final IndexStore store = new IndexStore();

    @Test
    void restoresDocumentsPostingsAndSearchResults() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Files.writeString(source.resolve("a.md"), "Java search java");
        Files.writeString(source.resolve("empty.txt"), "");
        InMemoryIndex original = new IndexBuilder().build(source);
        Path snapshot = directory.resolve("index.bin");

        store.save(snapshot, original);
        InMemoryIndex restored = store.load(snapshot);

        assertEquals(original.documents(), restored.documents());
        assertEquals(original.termCount(), restored.termCount());
        assertEquals(original.postings("java"), restored.postings("java"));
        assertEquals(original.content("a.md"), restored.content("a.md"));
        assertEquals(new SearchService(original).search("java"),
                new SearchService(restored).search("java"));
    }

    @Test
    void replacesAnExistingSnapshot() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path document = source.resolve("note.txt");
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(document, "old");
        store.save(snapshot, new IndexBuilder().build(source));

        Files.writeString(document, "new");
        store.save(snapshot, new IndexBuilder().build(source));

        assertEquals(List.of(), store.load(snapshot).postings("old"));
        assertEquals(List.of(new Posting("note.txt", List.of(0))),
                store.load(snapshot).postings("new"));
    }

    @Test
    void rejectsCorruptionAndLeavesTheSnapshotUntouched() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Files.writeString(source.resolve("note.txt"), "search");
        Path snapshot = directory.resolve("index.bin");
        store.save(snapshot, new IndexBuilder().build(source));
        byte[] bytes = Files.readAllBytes(snapshot);
        bytes[bytes.length - 1] ^= 1;
        Files.write(snapshot, bytes);

        assertThrows(IOException.class, () -> store.load(snapshot));
        assertArrayEquals(bytes, Files.readAllBytes(snapshot));
    }

    @Test
    void rejectsUnknownVersions() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = directory.resolve("index.bin");
        store.save(snapshot, new IndexBuilder().build(source));
        byte[] bytes = Files.readAllBytes(snapshot);
        bytes[7] = 2;
        Files.write(snapshot, bytes);

        IOException error = assertThrows(IOException.class, () -> store.load(snapshot));
        assertTrue(error.getMessage().contains("version"));
    }

    @Test
    void rejectsTruncatedSnapshots() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Files.writeString(source.resolve("note.txt"), "search");
        Path snapshot = directory.resolve("index.bin");
        store.save(snapshot, new IndexBuilder().build(source));
        byte[] bytes = Files.readAllBytes(snapshot);
        Files.write(snapshot, Arrays.copyOf(bytes, bytes.length - 10));

        assertThrows(IOException.class, () -> store.load(snapshot));
    }

    @Test
    void writesIdenticalBytesForIdenticalIndexes() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Files.writeString(source.resolve("b.txt"), "search search");
        Files.writeString(source.resolve("a.md"), "java search");
        Path first = directory.resolve("first.bin");
        Path second = directory.resolve("second.bin");

        store.save(first, new IndexBuilder().build(source));
        store.save(second, new IndexBuilder().build(source));

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void savesLongTermsAcceptedByTheScanner() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        String term = "a".repeat(9_000);
        Files.writeString(source.resolve("note.txt"), term);
        Path snapshot = directory.resolve("index.bin");

        store.save(snapshot, new IndexBuilder().build(source));

        assertEquals(List.of(new Posting("note.txt", List.of(0))),
                store.load(snapshot).postings(term));
    }
}
