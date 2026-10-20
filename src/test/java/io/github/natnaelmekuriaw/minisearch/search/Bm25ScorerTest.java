package io.github.natnaelmekuriaw.minisearch.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Bm25ScorerTest {
    private final Bm25Scorer scorer = new Bm25Scorer();

    @Test
    void usesTermFrequencyAndLengthNormalization() {
        double score = scorer.score(3, 1, 2, 3, 2);
        assertEquals(Math.log1p(2.5 / 1.5) * 4.4 / 3.65, score, 1e-12);
        assertTrue(scorer.score(3, 1, 2, 2, 2) > score);
        assertTrue(scorer.score(3, 1, 3, 3, 2) > score);
    }

    @Test
    void commonTermsScoreLessThanRareTerms() {
        assertTrue(scorer.score(10, 1, 1, 5, 5)
                > scorer.score(10, 8, 1, 5, 5));
    }

    @Test
    void rejectsImpossibleStatistics() {
        assertThrows(IllegalArgumentException.class, () -> scorer.score(0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> scorer.score(2, 3, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> scorer.score(2, 1, 2, 1, 1));
    }
}
