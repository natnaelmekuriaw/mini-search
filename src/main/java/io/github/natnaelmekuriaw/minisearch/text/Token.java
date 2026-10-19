package io.github.natnaelmekuriaw.minisearch.text;

import java.util.Objects;

public record Token(String term, int position) {
    public Token {
        Objects.requireNonNull(term, "term");
        if (term.isBlank()) {
            throw new IllegalArgumentException("term must not be blank");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position must be nonnegative");
        }
    }
}
