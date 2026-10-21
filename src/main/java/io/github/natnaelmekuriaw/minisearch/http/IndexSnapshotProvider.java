package io.github.natnaelmekuriaw.minisearch.http;

import io.github.natnaelmekuriaw.minisearch.index.InMemoryIndex;
import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

final class IndexSnapshotProvider {
    private final Path snapshot;
    private final IndexRepository repository = new IndexRepository();
    private volatile State state;

    IndexSnapshotProvider(Path snapshot) throws IOException {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot").toAbsolutePath().normalize();
        this.state = load(stamp());
    }

    View current() throws IOException {
        Stamp latest = stamp();
        State observed = state;
        if (observed.stamp().equals(latest)) {
            return observed.view();
        }
        synchronized (this) {
            latest = stamp();
            if (!state.stamp().equals(latest)) {
                state = load(latest);
            }
            return state.view();
        }
    }

    private State load(Stamp stamp) throws IOException {
        InMemoryIndex index = repository.open(snapshot);
        return new State(stamp, new View(index, new SearchService(index)));
    }

    private Stamp stamp() throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(snapshot, BasicFileAttributes.class);
        return new Stamp(attributes.fileKey(), attributes.size(), attributes.lastModifiedTime());
    }

    record View(InMemoryIndex index, SearchService search) {
    }

    private record State(Stamp stamp, View view) {
    }

    private record Stamp(Object fileKey, long size, FileTime modified) {
    }
}
