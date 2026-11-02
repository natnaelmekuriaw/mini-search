package io.github.natnaelmekuriaw.minisearch.source;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.StreamSupport;
import java.util.stream.Collectors;

public final class DocumentScanner {
    public static final long MAX_DOCUMENT_BYTES = 8L * 1024 * 1024;

    public List<SourceDocument> scan(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Path directory = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
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
                    String path = StreamSupport.stream(directory.relativize(file).spliterator(), false)
                            .map(Path::toString).collect(Collectors.joining("/"));
                    DocumentPaths.validate(path);
                    documents.add(new SourceDocument(path, readContent(file)));
                } catch (IOException | IllegalArgumentException exception) {
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

    private static String readContent(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes((int) MAX_DOCUMENT_BYTES + 1);
        }
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw new IOException("Document exceeds " + MAX_DOCUMENT_BYTES + " bytes: " + file);
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }
}
