package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SearchQuery(List<SearchClause> clauses, Optional<String> fileType,
                          Optional<String> pathPrefix) {
    public SearchQuery {
        Objects.requireNonNull(clauses, "clauses");
        clauses = List.copyOf(clauses);
        Objects.requireNonNull(fileType, "fileType");
        Objects.requireNonNull(pathPrefix, "pathPrefix");
    }

    public boolean isEmpty() {
        return clauses.isEmpty() && fileType.isEmpty() && pathPrefix.isEmpty();
    }
}
