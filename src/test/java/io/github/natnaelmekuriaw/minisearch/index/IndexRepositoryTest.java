package io.github.natnaelmekuriaw.minisearch.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.natnaelmekuriaw.minisearch.search.SearchResult;
import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexRepositoryTest {
    @TempDir
    Path directory;

    private final IndexRepository repository = new IndexRepository();

    @Test
    void createsAndReopensASearchableSnapshot() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = directory.resolve("saved/index.bin");
        Files.writeString(source.resolve("guide.md"), "Java search engine");

        IndexUpdate created = repository.refresh(source, snapshot);
        InMemoryIndex reopened = repository.open(snapshot);

        assertEquals(1, created.added());
        assertEquals(List.of("guide.md"), paths(new SearchService(reopened).search("java")));
        assertEquals(created.index().postings("search"), reopened.postings("search"));
    }

    @Test
    void refreshesAddedChangedAndRemovedDocumentsAcrossReopens() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(source.resolve("keep.md"), "java search");
        Files.writeString(source.resolve("change.txt"), "old term");
        Files.writeString(source.resolve("remove.txt"), "removed term");
        repository.refresh(source, snapshot);

        Files.writeString(source.resolve("change.txt"), "new term");
        Files.delete(source.resolve("remove.txt"));
        Files.writeString(source.resolve("add.md"), "fresh term");
        IndexUpdate update = repository.refresh(source, snapshot);
        InMemoryIndex reopened = repository.open(snapshot);

        assertEquals(1, update.added());
        assertEquals(1, update.changed());
        assertEquals(1, update.removed());
        assertEquals(1, update.unchanged());
        assertEquals(List.of("change.txt"), paths(new SearchService(reopened).search("new")));
        assertEquals(List.of(), reopened.postings("old"));
        assertEquals(List.of(), reopened.postings("removed"));
        assertEquals(List.of("keep.md"), paths(new SearchService(reopened).search("java")));
    }

    @Test
    void leavesSnapshotUnchangedWhenDocumentsDoNotChange() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(source.resolve("note.md"), "search");
        repository.refresh(source, snapshot);
        byte[] before = Files.readAllBytes(snapshot);

        IndexUpdate update = repository.refresh(source, snapshot);

        assertFalse(update.hasChanges());
        assertEquals(1, update.unchanged());
        assertArrayEquals(before, Files.readAllBytes(snapshot));
    }

    @Test
    void keepsThePriorSnapshotWhenScanningFails() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(source.resolve("note.md"), "search");
        repository.refresh(source, snapshot);
        byte[] before = Files.readAllBytes(snapshot);
        Files.write(source.resolve("bad.txt"), new byte[] {(byte) 0xc3, (byte) 0x28});

        assertThrows(IOException.class, () -> repository.refresh(source, snapshot));
        assertArrayEquals(before, Files.readAllBytes(snapshot));
        assertEquals(List.of("note.md"), paths(new SearchService(repository.open(snapshot)).search("search")));
    }

    @Test
    void rejectsSnapshotNamesThatTheScannerWouldIndex() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = source.resolve("index.TXT");

        assertThrows(IllegalArgumentException.class, () -> repository.refresh(source, snapshot));
        assertFalse(Files.exists(snapshot));
    }

    private static List<String> paths(List<SearchResult> results) {
        return results.stream().map(SearchResult::path).toList();
    }
}
