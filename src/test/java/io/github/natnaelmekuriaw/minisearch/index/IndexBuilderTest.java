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
}
