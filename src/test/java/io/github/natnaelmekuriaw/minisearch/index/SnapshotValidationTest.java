package io.github.natnaelmekuriaw.minisearch.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotValidationTest {
    @TempDir
    Path directory;

    @Test
    void rejectsInconsistentPostingsEvenWithAValidChecksum() throws Exception {
        IndexStore store = new IndexStore();
        assertEquals(2, store.load(snapshot(Map.of("alpha", List.of(0), "beta", List.of(1))))
                .termCount());
        assertThrows(IOException.class,
                () -> store.load(snapshot(Map.of("alpha", List.of(0), "beta", List.of(0)))));
        assertThrows(IOException.class, () -> store.load(snapshot(Map.of("alpha", List.of(0)))));
        assertThrows(IOException.class,
                () -> store.load(snapshot(Map.of("ALPHA", List.of(0, 1)))));
        assertThrows(IOException.class,
                () -> store.load(snapshot(Map.of("two words", List.of(0, 1)))));
    }

    private Path snapshot(Map<String, List<Integer>> postings) throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(payload)) {
            data.writeInt(0x4d534958);
            data.writeInt(2);
            data.writeInt(1);
            string(data, "a.txt");
            data.writeInt(2);
            string(data, "alpha beta");
            data.writeInt(postings.size());
            for (var entry : new TreeMap<>(postings).entrySet()) {
                string(data, entry.getKey());
                data.writeInt(1);
                string(data, "a.txt");
                data.writeInt(entry.getValue().size());
                for (int position : entry.getValue()) {
                    data.writeInt(position);
                }
            }
        }
        byte[] bytes = payload.toByteArray();
        payload.writeBytes(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path file = directory.resolve("index.bin");
        Files.write(file, payload.toByteArray());
        return file;
    }

    private void string(DataOutputStream data, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }
}
