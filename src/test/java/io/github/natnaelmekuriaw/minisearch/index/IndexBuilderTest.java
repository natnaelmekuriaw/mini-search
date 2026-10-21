package io.github.natnaelmekuriaw.minisearch.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexBuilderTest {
    @TempDir
    Path directory;

    private final IndexBuilder builder = new IndexBuilder();

    @Test
    void recordsDocumentLengthsAndTermPositions() throws IOException {
        Files.writeString(directory.resolve("b.md"), "Java search Java");
        Files.writeString(directory.resolve("a.txt"), "Search engines");

        InMemoryIndex index = builder.build(directory);

        assertEquals(List.of(
                new IndexedDocument("a.txt", 2),
                new IndexedDocument("b.md", 3)), index.documents());
        assertEquals(List.of(new Posting("b.md", List.of(0, 2))), index.postings("JAVA"));
        assertEquals(List.of(
                new Posting("a.txt", List.of(0)),
                new Posting("b.md", List.of(1))), index.postings("search"));
        assertEquals(3, index.termCount());
        assertEquals("Java search Java", index.content("b.md").orElseThrow());
        assertEquals(List.of("java"), index.termsStartingWith("JA", 10));
        assertEquals(List.of("search"), index.termsStartingWith("s", 1));
        assertEquals(List.of(), index.termsStartingWith("missing", 10));
        assertThrows(IllegalArgumentException.class, () -> index.termsStartingWith("ja va", 10));
        assertThrows(IllegalArgumentException.class, () -> index.termsStartingWith("ja", 0));
    }

    @Test
    void keepsEmptyDocumentsButCreatesNoPostings() throws IOException {
        Files.createFile(directory.resolve("empty.md"));

        InMemoryIndex index = builder.build(directory);

        assertEquals(List.of(new IndexedDocument("empty.md", 0)), index.documents());
        assertEquals(0, index.termCount());
        assertEquals(List.of(), index.postings("missing"));
    }

    @Test
    void rejectsQueriesWithMultipleTerms() throws IOException {
        InMemoryIndex index = builder.build(directory);
        assertThrows(IllegalArgumentException.class, () -> index.postings("two terms"));
    }

    @Test
    void updatesAddedChangedAndRemovedDocuments() throws IOException {
        Files.writeString(directory.resolve("keep.md"), "java search");
        Files.writeString(directory.resolve("change.txt"), "old term");
        Files.writeString(directory.resolve("remove.txt"), "gone term");
        InMemoryIndex initial = builder.build(directory);

        Files.writeString(directory.resolve("change.txt"), "new term new");
        Files.delete(directory.resolve("remove.txt"));
        Files.writeString(directory.resolve("add.md"), "fresh term");
        IndexUpdate update = builder.update(directory, initial);

        assertEquals(1, update.added());
        assertEquals(1, update.changed());
        assertEquals(1, update.removed());
        assertEquals(1, update.unchanged());
        assertEquals(List.of("add.md", "change.txt", "keep.md"),
                update.index().documents().stream().map(IndexedDocument::path).toList());
        assertEquals(List.of(new Posting("change.txt", List.of(0, 2))),
                update.index().postings("new"));
        assertEquals(List.of(), update.index().postings("old"));
        assertEquals(List.of(), update.index().postings("gone"));
        assertEquals(List.of(new Posting("keep.md", List.of(0))),
                update.index().postings("java"));
    }

    @Test
    void unchangedRefreshKeepsTheSameSearchData() throws IOException {
        Files.writeString(directory.resolve("note.md"), "java search java");
        InMemoryIndex initial = builder.build(directory);

        IndexUpdate update = builder.update(directory, initial);

        assertEquals(false, update.hasChanges());
        assertEquals(1, update.unchanged());
        assertEquals(initial.documents(), update.index().documents());
        assertEquals(initial.postings("java"), update.index().postings("java"));
    }
}
