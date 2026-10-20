package io.github.natnaelmekuriaw.minisearch.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueryParserTest {
    private final QueryParser parser = new QueryParser();

    @Test
    void parsesTermsPhrasesAndFilters() {
        SearchQuery query = parser.parse("Java \"search engine\" type:MD path:notes/");

        assertEquals(List.of(
                new TermClause("java"),
                new PhraseClause(List.of("search", "engine"))), query.clauses());
        assertEquals(Optional.of("md"), query.fileType());
        assertEquals(Optional.of("notes/"), query.pathPrefix());
    }

    @Test
    void splitsPunctuationInPlainTerms() {
        assertEquals(List.of(new TermClause("java"), new TermClause("search")),
                parser.parse("java-search").clauses());
    }

    @Test
    void normalizesWordsInsidePhrases() {
        assertEquals(List.of(new PhraseClause(List.of("café", "search"))),
                parser.parse("\"CAFE\u0301 Search\"").clauses());
    }

    @Test
    void permitsFilterOnlyQueries() {
        assertEquals(new SearchQuery(List.of(), Optional.of("txt"), Optional.empty()),
                parser.parse("type:txt"));
    }

    @Test
    void distinguishesEmptyInput() {
        assertTrue(parser.parse(" \t ").isEmpty());
    }

    @Test
    void rejectsMalformedQueries() {
        for (String input : List.of("\"unfinished", "\"...\"", "type:pdf", "type:md type:txt",
                "path:", "path:/absolute", "path:a path:b", "unknown:value", "word\"phrase\"", "...")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(input), input);
        }
    }
}
