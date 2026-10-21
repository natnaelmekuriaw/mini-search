package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SuggestionRequestTest {
    @Test
    void parsesDefaultsAndBounds() {
        assertEquals(new SuggestionRequest("jav", 10), SuggestionRequest.parse("q=jav"));
        assertEquals(new SuggestionRequest("jav", 2), SuggestionRequest.parse("q=jav&limit=2"));
        for (String query : new String[] {"", "q=", "q=jav&limit=0", "q=jav&limit=21",
                "q=jav&limit=bad", "q=jav&offset=1", "q=jav&q=java"}) {
            assertThrows(IllegalArgumentException.class, () -> SuggestionRequest.parse(query),
                    query);
        }
    }
}
