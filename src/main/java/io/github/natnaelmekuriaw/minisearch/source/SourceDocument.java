package io.github.natnaelmekuriaw.minisearch.source;

import java.util.Objects;

public record SourceDocument(String path, String content) {
    public SourceDocument {
        DocumentPaths.validate(path);
        Objects.requireNonNull(content, "content");
    }
}
