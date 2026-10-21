package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SearchQuery(List<SearchClause> clauses, Optional<String> fileType,
                          Optional<String> pathPrefix, Optional<QueryExpression> expression) {
    public SearchQuery {
        Objects.requireNonNull(clauses, "clauses");
        clauses = List.copyOf(clauses);
        Objects.requireNonNull(fileType, "fileType");
        Objects.requireNonNull(pathPrefix, "pathPrefix");
        Objects.requireNonNull(expression, "expression");
    }

    public SearchQuery(List<SearchClause> clauses, Optional<String> fileType,
                       Optional<String> pathPrefix) {
        this(clauses, fileType, pathPrefix, clauses.isEmpty() ? Optional.empty()
                : Optional.of(new QueryExpression.And(clauses.stream()
                        .map(QueryExpression.Clause::new).map(QueryExpression.class::cast).toList())));
    }

    public boolean isEmpty() {
        return clauses.isEmpty() && fileType.isEmpty() && pathPrefix.isEmpty();
    }
}
