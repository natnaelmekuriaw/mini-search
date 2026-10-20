package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;
import java.util.Objects;

public record PhraseClause(List<String> terms) implements SearchClause {
    public PhraseClause {
        Objects.requireNonNull(terms, "terms");
        terms = List.copyOf(terms);
        if (terms.isEmpty() || terms.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("phrase must contain terms");
        }
    }
}
