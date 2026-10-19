package io.github.natnaelmekuriaw.minisearch.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostingTest {
    @Test
    void copiesPositionsAndExposesFrequency() {
        List<Integer> positions = new ArrayList<>(List.of(0, 3));
        Posting posting = new Posting("a.txt", positions);
        positions.add(4);

        assertEquals(List.of(0, 3), posting.positions());
        assertEquals(2, posting.frequency());
        assertThrows(UnsupportedOperationException.class, () -> posting.positions().add(5));
    }

    @Test
    void rejectsInvalidPositionOrder() {
        assertThrows(IllegalArgumentException.class, () -> new Posting("a.txt", List.of(2, 2)));
        assertThrows(IllegalArgumentException.class, () -> new Posting("a.txt", List.of(2, 1)));
        assertThrows(IllegalArgumentException.class, () -> new Posting("a.txt", List.of()));
    }
}
