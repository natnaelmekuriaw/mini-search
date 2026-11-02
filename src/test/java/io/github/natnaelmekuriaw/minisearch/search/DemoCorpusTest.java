package io.github.natnaelmekuriaw.minisearch.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoCorpusTest {
    @TempDir
    Path directory;

    @Test
    void savedDemoDocumentsProduceThePublishedResults() throws IOException {
        IndexRepository repository = new IndexRepository();
        Path snapshot = directory.resolve("demo.bin");
        assertEquals(5, repository.refresh(Path.of("examples/documents"), snapshot).added());
        SearchService search = new SearchService(repository.open(snapshot));

        assertEquals(List.of("guides/java.md", "drafts/roadmap.md"), paths(search.search("java")));
        assertEquals(Set.of("guides/java.md", "guides/kotlin.md"), Set.copyOf(paths(search.search(
                "(java OR kotlin) \"search engine\" -draft path:guides/"))));
        assertEquals(List.of("web.txt"), paths(search.search("jav* type:txt")));
        assertEquals(List.of("notes/unicode.txt"), paths(search.search("CAFE\u0301")));
        assertEquals(List.of("java", "javascript"), search.suggest("jav", 10));
        assertEquals(2, search.searchPage("java", 1, 1).totalMatches());
        assertEquals(5, repository.refresh(Path.of("examples/documents"), snapshot).unchanged());
    }

    private List<String> paths(List<SearchResult> results) {
        return results.stream().map(SearchResult::path).toList();
    }
}
