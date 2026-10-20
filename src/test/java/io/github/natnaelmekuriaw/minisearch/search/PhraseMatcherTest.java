package io.github.natnaelmekuriaw.minisearch.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.natnaelmekuriaw.minisearch.index.Posting;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhraseMatcherTest {
    private final PhraseMatcher matcher = new PhraseMatcher();

    @Test
    void matchesAdjacentTermsAtAnyPosition() {
        Map<String, Posting> postings = Map.of(
                "java", new Posting("a.txt", List.of(0, 4)),
                "search", new Posting("a.txt", List.of(2, 5)));

        assertTrue(matcher.matches(new PhraseClause(List.of("java", "search")), postings));
        assertFalse(matcher.matches(new PhraseClause(List.of("search", "java")), postings));
    }

    @Test
    void respectsRepeatedTerms() {
        Map<String, Posting> postings = Map.of(
                "go", new Posting("a.txt", List.of(0, 1, 3)));

        assertTrue(matcher.matches(new PhraseClause(List.of("go", "go")), postings));
        assertFalse(matcher.matches(new PhraseClause(List.of("go", "go", "go")), postings));
    }

    @Test
    void rejectsMissingAndNonadjacentTerms() {
        Map<String, Posting> postings = Map.of(
                "java", new Posting("a.txt", List.of(0)),
                "search", new Posting("a.txt", List.of(2)));

        assertFalse(matcher.matches(new PhraseClause(List.of("java", "search")), postings));
        assertFalse(matcher.matches(new PhraseClause(List.of("missing")), postings));
    }
}
