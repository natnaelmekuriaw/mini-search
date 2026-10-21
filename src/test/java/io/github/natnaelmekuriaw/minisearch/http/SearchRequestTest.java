package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SearchRequestTest {
    @Test
    void appliesDefaultAndExplicitLimits() {
        assertEquals(new SearchRequest("java search", 0, 20), SearchRequest.parse("q=java+search"));
        assertEquals(new SearchRequest("java", 2, 100),
                SearchRequest.parse("q=java&offset=2&limit=100"));
    }

    @Test
    void rejectsMissingQueriesAndInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.parse(null));
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.parse("q=+"));
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.parse("q=java&limit=0"));
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.parse("q=java&limit=101"));
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.parse("q=java&limit=abc"));
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.parse("q=java&offset=-1"));
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.parse("q=java&offset=abc"));
        assertThrows(IllegalArgumentException.class,
                () -> SearchRequest.parse("q=" + "a".repeat(4_097)));
    }
}
