package io.github.natnaelmekuriaw.minisearch.text;

import java.util.Objects;

public record TokenSpan(String term, int position, int startOffset, int endOffset) {
    public TokenSpan {
        Objects.requireNonNull(term, "term");
        if (term.isBlank() || position < 0 || startOffset < 0 || endOffset <= startOffset) {
            throw new IllegalArgumentException("Invalid token span");
        }
    }
}
