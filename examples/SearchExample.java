import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import io.github.natnaelmekuriaw.minisearch.index.IndexUpdate;
import io.github.natnaelmekuriaw.minisearch.search.SearchPage;
import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.nio.file.Path;

public final class SearchExample {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: SearchExample.java <documents> <snapshot>");
        }
        IndexRepository repository = new IndexRepository();
        IndexUpdate update = repository.refresh(Path.of(args[0]), Path.of(args[1]));
        System.out.printf("Indexed %d documents; %d changed.%n",
                update.index().documents().size(), update.changed());

        SearchService search = new SearchService(repository.open(Path.of(args[1])));
        SearchPage page = search.searchPage(
                "(java OR kotlin) \"search engine\" -draft path:guides/", 0, 10);
        System.out.println("Matches: " + page.totalMatches());
        page.results().forEach(result -> System.out.printf("%s: %.3f%n  %s%n",
                result.path(), result.score(), result.snippet()));
        System.out.println("Suggestions: " + search.suggest("jav", 10));
    }
}
