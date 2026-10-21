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
    void parsesBooleanPrecedenceAndGroupedExclusions() {
        SearchQuery query = parser.parse("(java OR kotlin) \"search engine\" -draft");

        assertEquals(new QueryExpression.And(List.of(
                new QueryExpression.Or(List.of(
                        new QueryExpression.Clause(new TermClause("java")),
                        new QueryExpression.Clause(new TermClause("kotlin")))),
                new QueryExpression.Clause(new PhraseClause(List.of("search", "engine"))),
                new QueryExpression.Not(new QueryExpression.Clause(new TermClause("draft"))))),
                query.expression().orElseThrow());
        assertEquals(new QueryExpression.Or(List.of(
                new QueryExpression.Clause(new TermClause("java")),
                new QueryExpression.And(List.of(
                        new QueryExpression.Clause(new TermClause("kotlin")),
                        new QueryExpression.Clause(new TermClause("search")))))),
                parser.parse("java OR kotlin search").expression().orElseThrow());
    }

    @Test
    void rejectsIncompleteBooleanExpressions() {
        for (String input : List.of("OR java", "java OR", "java AND", "()", "(java",
                "java)", "java OR ()", "-", "java NOT", "java OR AND kotlin",
                "-type:md java", "NOT path:notes/ java", "(java type:md)")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(input), input);
        }
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("(".repeat(65) + "java" + ")".repeat(65)));
    }

    @Test
    void rejectsMalformedQueries() {
        for (String input : List.of("\"unfinished", "\"...\"", "type:pdf", "type:md type:txt",
                "path:", "path:/absolute", "path:a path:b", "unknown:value", "word\"phrase\"", "...")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(input), input);
        }
    }
}
