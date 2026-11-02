package io.github.natnaelmekuriaw.minisearch.source;

import java.util.Arrays;
import java.util.Objects;

public final class DocumentPaths {
    private DocumentPaths() {
    }

    public static void validate(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":")
                || path.codePoints().anyMatch(Character::isISOControl)
                || Arrays.stream(path.split("/", -1)).anyMatch(part ->
                        part.isEmpty() || part.equals(".") || part.equals(".."))) {
            throw new IllegalArgumentException("Document path must be a portable relative path");
        }
    }

    public static String normalizePrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        String normalized = prefix.replace('\\', '/');
        validate(normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1) : normalized);
        return normalized;
    }
}
