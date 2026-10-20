package io.github.natnaelmekuriaw.minisearch.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SnippetBuilderTest {
    private final SnippetBuilder builder = new SnippetBuilder();

    @Test
    void centersOnAQueryTermNearTheEndOfLongText() {
        String content = "plain ".repeat(50) + "CAFE\u0301 appears here";
        String snippet = builder.build(content, Set.of("café"));

        assertTrue(snippet.startsWith("…"));
        assertTrue(snippet.contains("CAFE\u0301"));
    }

    @Test
    void collapsesWhitespaceAndHandlesEmptyContent() {
        assertEquals("alpha beta", builder.build("alpha\n\t beta", Set.of("alpha")));
        assertEquals("", builder.build(" \n ", Set.of("alpha")));
    }

    @Test
    void usesFirstWordForFilterOnlyResults() {
        assertEquals("first second", builder.build(" first second ", Set.of()));
    }
}
