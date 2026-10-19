package io.github.natnaelmekuriaw.minisearch.source;

import java.util.Objects;

public record SourceDocument(String path, String content) {
    public SourceDocument {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
    }
}
