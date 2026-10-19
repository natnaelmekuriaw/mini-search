package io.github.natnaelmekuriaw.minisearch.index;

import java.util.Objects;

public record IndexedDocument(String path, int tokenCount) {
    public IndexedDocument {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be nonnegative");
        }
    }
}
