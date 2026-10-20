package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;
import java.util.Objects;

public record TermClause(String term) implements SearchClause {
    public TermClause {
        Objects.requireNonNull(term, "term");
        if (term.isBlank()) {
            throw new IllegalArgumentException("term must not be blank");
        }
    }

    @Override
    public List<String> terms() {
        return List.of(term);
    }
}
