package io.github.natnaelmekuriaw.minisearch.search;

import java.util.Objects;

public record SearchResult(String path, double score, String snippet) {
    public SearchResult {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(snippet, "snippet");
        if (path.isBlank() || !Double.isFinite(score) || score < 0) {
            throw new IllegalArgumentException("Invalid search result");
        }
    }
}
