package io.github.natnaelmekuriaw.minisearch.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class IndexRepository {
    private final IndexBuilder builder = new IndexBuilder();
    private final IndexStore store = new IndexStore();

    public InMemoryIndex open(Path snapshot) throws IOException {
        return store.load(snapshot);
    }

    public IndexUpdate refresh(Path root, Path snapshot) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(snapshot, "snapshot");
        Path directory = root.toAbsolutePath().normalize();
        Path target = snapshot.toAbsolutePath().normalize();
        String filename = target.getFileName().toString().toLowerCase(Locale.ROOT);
        if (target.startsWith(directory) && (filename.endsWith(".txt") || filename.endsWith(".md"))) {
            throw new IllegalArgumentException("Index snapshot must not be a searchable document");
        }

        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            InMemoryIndex index = builder.build(directory);
            store.save(target, index);
            return new IndexUpdate(index, index.documents().size(), 0, 0, 0);
        }
        InMemoryIndex previous = store.load(target);
        IndexUpdate update = builder.update(directory, previous);
        if (update.hasChanges()) {
            store.save(target, update.index());
        }
        return update;
    }
}
