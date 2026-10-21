package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexSnapshotProviderTest {
    @TempDir
    Path directory;

    @Test
    void reusesUnchangedIndexesAndLoadsAtomicReplacements() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = directory.resolve("index.bin");
        Path document = source.resolve("note.md");
        Files.writeString(document, "alpha");
        IndexRepository repository = new IndexRepository();
        repository.refresh(source, snapshot);
        IndexSnapshotProvider provider = new IndexSnapshotProvider(snapshot);
        IndexSnapshotProvider.View original = provider.current();

        assertSame(original, provider.current());
        Files.writeString(document, "bravo");
        repository.refresh(source, snapshot);

        assertEquals(0, provider.current().search().search("alpha").size());
        assertEquals(1, provider.current().search().search("bravo").size());
    }

    @Test
    void rejectsDamagedReplacementWithoutDiscardingTheLastValidIndex() throws IOException {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(source.resolve("note.md"), "alpha");
        new IndexRepository().refresh(source, snapshot);
        IndexSnapshotProvider provider = new IndexSnapshotProvider(snapshot);
        byte[] damaged = Files.readAllBytes(snapshot);
        damaged[damaged.length - 1] ^= 1;
        Files.write(snapshot, damaged);

        assertThrows(IOException.class, provider::current);
    }
}
