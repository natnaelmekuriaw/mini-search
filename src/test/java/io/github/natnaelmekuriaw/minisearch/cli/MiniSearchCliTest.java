package io.github.natnaelmekuriaw.minisearch.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
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
    void searchesBooleanQueriesFromSavedSnapshots() throws IOException {
        Path source = Files.createDirectory(directory.resolve("docs"));
        Files.writeString(source.resolve("java.md"), "java search engine");
        Files.writeString(source.resolve("kotlin.md"), "kotlin search engine");
        Files.writeString(source.resolve("draft.md"), "java search engine draft");
        Path snapshot = directory.resolve("index.bin");
        assertEquals(0, run("index", "--source", source.toString(),
                "--index", snapshot.toString()).code());

        Result searched = run("search", "--index", snapshot.toString(),
                "--query", "(java OR kotlin) \"search engine\" -draft");

        assertEquals(0, searched.code());
        assertTrue(searched.out().contains("java.md"));
        assertTrue(searched.out().contains("kotlin.md"));
        assertFalse(searched.out().contains("draft.md"));
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

    @Test
    void servesTheSavedIndexUntilInterrupted() throws Exception {
        Path source = Files.createDirectory(directory.resolve("docs"));
        Files.writeString(source.resolve("note.md"), "java search");
        Path snapshot = directory.resolve("index.bin");
        assertEquals(0, run("index", "--source", source.toString(),
                "--index", snapshot.toString()).code());
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        AtomicInteger exitCode = new AtomicInteger(-1);
        Thread server = Thread.ofVirtual().start(() -> exitCode.set(cli.run(
                new String[] {"serve", "--index", snapshot.toString(), "--port", Integer.toString(port)},
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8))));
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/search?q=java");
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < 40 && response == null; attempt++) {
                try {
                    response = client.send(HttpRequest.newBuilder(uri).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                } catch (IOException exception) {
                    Thread.sleep(50);
                }
            }
            assertNotNull(response, errors.toString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode(), errors.toString(StandardCharsets.UTF_8));
            assertTrue(response.body().contains("note.md"));
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("Serving http://127.0.0.1:"));
        } finally {
            server.interrupt();
            server.join(5_000);
        }
        assertEquals(130, exitCode.get());
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
