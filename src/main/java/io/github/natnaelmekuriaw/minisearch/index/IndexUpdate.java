package io.github.natnaelmekuriaw.minisearch.index;

import java.util.Objects;

public record IndexUpdate(InMemoryIndex index, int added, int changed, int removed, int unchanged) {
    public IndexUpdate {
        Objects.requireNonNull(index, "index");
        if (added < 0 || changed < 0 || removed < 0 || unchanged < 0) {
            throw new IllegalArgumentException("update counts must be nonnegative");
        }
    }

    public boolean hasChanges() {
        return added != 0 || changed != 0 || removed != 0;
    }
}
