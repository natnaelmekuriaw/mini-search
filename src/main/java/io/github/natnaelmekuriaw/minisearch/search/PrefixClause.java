package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;
import java.util.Objects;

public record PrefixClause(String prefix) implements SearchClause {
    public PrefixClause {
        Objects.requireNonNull(prefix, "prefix");
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
    }

    @Override
    public List<String> terms() {
        return List.of(prefix);
    }
}
