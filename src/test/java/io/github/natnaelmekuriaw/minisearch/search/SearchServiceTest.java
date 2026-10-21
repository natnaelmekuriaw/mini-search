package io.github.natnaelmekuriaw.minisearch.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.natnaelmekuriaw.minisearch.index.IndexBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchServiceTest {
    @TempDir
    Path directory;

    @Test
    void ranksDocumentsWithMoreMatchingEvidenceFirst() throws IOException {
        write("docs/guide.md", "java search engine java search");
        write("docs/other.txt", "java engine search");
        write("misc.md", "search engine");
        SearchService service = service();

        List<SearchResult> results = service.search("java search");

        assertEquals(List.of("docs/guide.md", "docs/other.txt"), paths(results));
        assertTrue(results.get(0).score() > results.get(1).score());
        assertTrue(results.get(0).snippet().contains("java search"));
    }

    @Test
    void requiresEveryPhraseInTheCorrectOrder() throws IOException {
        write("adjacent.md", "java search engine");
        write("reversed.txt", "search java engine");
        SearchService service = service();

        assertEquals(List.of("adjacent.md"), paths(service.search("\"java search\"")));
        assertEquals(List.of("adjacent.md"),
                paths(service.search("\"java search\" engine")));
        assertEquals(List.of(), service.search("\"java search\" missing"));
        assertEquals(List.of(), service.search("\"engine java\""));
    }

    @Test
    void supportsRepeatedTermsInsidePhrases() throws IOException {
        write("repeated.txt", "go go search");
        write("separated.txt", "go search go");

        assertEquals(List.of("repeated.txt"), paths(service().search("\"go go\"")));
    }

    @Test
    void appliesTypeAndPathFilters() throws IOException {
        write("docs/one.md", "java search");
        write("docs/two.TXT", "java search");
        write("other.md", "java search");
        SearchService service = service();

        assertEquals(List.of("docs/one.md"),
                paths(service.search("java type:md path:docs/")));
        assertEquals(List.of("docs/two.TXT"),
                paths(service.search("search type:txt path:docs/")));
    }

    @Test
    void returnsFilteredDocumentsWithZeroScoreWhenNoTermsAreGiven() throws IOException {
        write("a.md", "some text");
        write("b.txt", "other text");
        write("c.md", "");

        List<SearchResult> results = service().search("type:md");

        assertEquals(List.of("a.md", "c.md"), paths(results));
        assertEquals(0, results.get(0).score());
        assertEquals("", results.get(1).snippet());
    }

    @Test
    void breaksScoreTiesByPathAndHonorsLimit() throws IOException {
        write("b.txt", "java");
        write("a.txt", "java");

        assertEquals(List.of("a.txt"), paths(service().search("java", 1)));
    }

    @Test
    void countsRepeatedQueryTermsOnceForRanking() throws IOException {
        write("a.txt", "java java");
        SearchService service = service();

        assertEquals(service.search("java").getFirst().score(),
                service.search("java java").getFirst().score());
    }

    @Test
    void returnsNoResultsForEmptyOrUnmatchedQueries() throws IOException {
        write("a.txt", "java");
        SearchService service = service();

        assertEquals(List.of(), service.search(" "));
        assertEquals(List.of(), service.search("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.search("type:pdf"));
    }

    @Test
    void handlesAnEmptyCorpus() throws IOException {
        SearchService service = service();
        assertEquals(List.of(), service.search("java"));
        assertEquals(List.of(), service.search("type:md"));
    }

    @Test
    void rejectsNonpositiveLimits() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> service().search("java", 0));
    }

    @Test
    void pagesResultsAndRetainsTheTotalMatchCount() throws IOException {
        write("a.txt", "java");
        write("b.txt", "java");
        write("c.txt", "java");
        SearchService service = service();

        SearchPage page = service.searchPage("java", 1, 1);

        assertEquals(3, page.totalMatches());
        assertEquals(1, page.offset());
        assertEquals(List.of("b.txt"), paths(page.results()));
        assertEquals(List.of(), service.searchPage("java", 10, 1).results());
        assertThrows(IllegalArgumentException.class, () -> service.searchPage("java", -1, 1));
    }

    private SearchService service() throws IOException {
        return new SearchService(new IndexBuilder().build(directory));
    }

    private void write(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static List<String> paths(List<SearchResult> results) {
        return results.stream().map(SearchResult::path).toList();
    }
}
