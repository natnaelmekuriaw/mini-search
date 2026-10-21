package io.github.natnaelmekuriaw.minisearch.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiniSearchCliTest {
    @TempDir
    Path directory;

    private final MiniSearchCli cli = new MiniSearchCli();

    @Test
    void indexesAndSearchesSavedDocuments() throws IOException {
        Path source = Files.createDirectory(directory.resolve("docs"));
        Files.writeString(source.resolve("guide.md"), "Java search engine");
        Files.writeString(source.resolve("other.txt"), "Java search");
        Path snapshot = directory.resolve("index.bin");

        Result indexed = run("index", "--source", source.toString(), "--index", snapshot.toString());
        Result searched = run("search", "--index", snapshot.toString(),
                "--query", "java \"search engine\" type:md", "--limit", "1");

        assertEquals(0, indexed.code());
        assertTrue(indexed.out().contains("2 added"));
        assertEquals(0, searched.code());
        assertTrue(searched.out().startsWith("1 result"));
        assertTrue(searched.out().contains("guide.md"));
        assertTrue(searched.out().contains("Java search engine"));
        assertFalse(searched.out().contains("other.txt"));
    }

    @Test
    void reportsRefreshCountsAndSearchesChanges() throws IOException {
        Path source = Files.createDirectory(directory.resolve("docs"));
        Path document = source.resolve("note.md");
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(document, "old term");
        run("index", "--source", source.toString(), "--index", snapshot.toString());
        Files.writeString(document, "new term");

        Result refreshed = run("index", "--source", source.toString(), "--index", snapshot.toString());
        Result searched = run("search", "--index", snapshot.toString(), "--query", "new");

        assertEquals(0, refreshed.code());
        assertTrue(refreshed.out().contains("1 changed"));
        assertEquals(0, searched.code());
        assertTrue(searched.out().contains("note.md"));
        assertTrue(run("search", "--index", snapshot.toString(), "--query", "old")
                .out().startsWith("0 results"));
    }

    @Test
    void printsHelpAndRejectsInvalidOptions() {
        assertEquals(0, run("--help").code());
        assertTrue(run("--help").out().contains("<jar-file> search"));
        assertEquals(2, run().code());
        assertEquals(2, run("unknown").code());
        assertTrue(run("search", "--index", "x", "--query").err().contains("Missing value"));
        assertTrue(run("search", "--index", "x", "--query", "java", "--limit", "0")
                .err().contains("positive integer"));
        assertTrue(run("search", "--index", "x", "--query", "java", "--index", "x")
                .err().contains("Duplicate option"));
    }

    @Test
    void reportsMissingSourceAndSnapshotWithoutStackTraces() {
        Result index = run("index", "--source", directory.resolve("missing").toString(),
                "--index", directory.resolve("index.bin").toString());
        Result search = run("search", "--index", directory.resolve("missing.bin").toString(),
                "--query", "java");

        assertEquals(1, index.code());
        assertEquals(1, search.code());
        assertTrue(index.err().startsWith("Error: "));
        assertTrue(search.err().startsWith("Error: "));
        assertFalse(search.err().contains("Exception"));
    }

    @Test
    void limitsResultsAndReportsInvalidQueries() throws IOException {
        Path source = Files.createDirectory(directory.resolve("docs"));
        Files.writeString(source.resolve("a.md"), "java");
        Files.writeString(source.resolve("b.md"), "java");
        Path snapshot = directory.resolve("index.bin");
        run("index", "--source", source.toString(), "--index", snapshot.toString());

        Result limited = run("search", "--index", snapshot.toString(),
                "--query", "java", "--limit", "1");
        Result invalid = run("search", "--index", snapshot.toString(),
                "--query", "type:pdf");

        assertEquals(0, limited.code());
        assertTrue(limited.out().startsWith("1 result"));
        assertTrue(limited.out().contains("a.md"));
        assertFalse(limited.out().contains("b.md"));
        assertEquals(2, invalid.code());
        assertTrue(invalid.err().startsWith("Error: "));
        assertEquals("", invalid.out());
    }

    @Test
    void validatesServeOptionsBeforeStarting() {
        assertTrue(run("serve", "--help").out().contains("serve --index"));
        assertTrue(run("serve", "--index", "missing.bin", "--port", "0")
                .err().contains("--port"));
        assertTrue(run("serve", "--index", "missing.bin", "--port", "65536")
                .err().contains("--port"));
        assertEquals(1, run("serve", "--index", directory.resolve("missing.bin").toString()).code());
        assertEquals("http://[::1]:8080", MiniSearchCli.serverUrl("::1", 8080));
    }

    private Result run(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int code = cli.run(args, new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        return new Result(code, output.toString(StandardCharsets.UTF_8),
                errors.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}
