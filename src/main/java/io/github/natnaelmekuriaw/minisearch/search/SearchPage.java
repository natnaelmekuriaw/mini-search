package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;
import java.util.Objects;

public record SearchPage(List<SearchResult> results, int totalMatches, int offset, int limit) {
    public SearchPage {
        Objects.requireNonNull(results, "results");
        results = List.copyOf(results);
        if (totalMatches < 0 || offset < 0 || limit < 1 || results.size() > limit
                || results.size() > totalMatches) {
            throw new IllegalArgumentException("Invalid search page");
        }
    }
}
