package io.github.natnaelmekuriaw.minisearch.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentPathsTest {
    @Test
    void rejectsAmbiguousAndAbsoluteDocumentPaths() {
        for (String path : List.of("", "/", "../note.md", "a/./b", "a//b",
                "C:/notes/a.md", "a\\b.md", "a\nb.md")) {
            assertThrows(IllegalArgumentException.class, () -> DocumentPaths.validate(path), path);
        }
        assertEquals("notes/", DocumentPaths.normalizePrefix("notes\\"));
        assertEquals("notes/java", DocumentPaths.normalizePrefix("notes/java"));
        assertThrows(IllegalArgumentException.class, () -> DocumentPaths.normalizePrefix("../"));
    }
}
