package io.github.natnaelmekuriaw.minisearch.index;

import java.util.List;
import java.util.Objects;

public record Posting(String path, List<Integer> positions) {
    public Posting {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(positions, "positions");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        positions = List.copyOf(positions);
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("positions must not be empty");
        }
        int previous = -1;
        for (int position : positions) {
            if (position <= previous) {
                throw new IllegalArgumentException("positions must be strictly increasing");
            }
            previous = position;
        }
    }

    public int frequency() {
        return positions.size();
    }
}
