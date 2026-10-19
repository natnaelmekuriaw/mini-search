package io.github.natnaelmekuriaw.minisearch.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DocumentScanner {
    public static final long MAX_DOCUMENT_BYTES = 8L * 1024 * 1024;

    public List<SourceDocument> scan(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Path directory = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Document root is not a directory: " + directory);
        }

        List<SourceDocument> documents = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || !isSupported(file)) {
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.size() > MAX_DOCUMENT_BYTES) {
                    throw new IOException("Document exceeds " + MAX_DOCUMENT_BYTES + " bytes: " + file);
                }
                try {
                    String path = directory.relativize(file).toString().replace('\\', '/');
                    documents.add(new SourceDocument(path, Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException exception) {
                    throw new IOException("Cannot read document: " + file, exception);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                throw new IOException("Cannot visit path: " + file, exception);
            }
        });
        documents.sort(Comparator.comparing(SourceDocument::path));
        return List.copyOf(documents);
    }

    private static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".md");
    }
}
