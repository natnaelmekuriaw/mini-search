package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchHttpServerTest {
    @TempDir
    Path directory;

    @Test
    void servesHealthIndexAndRankedSearch() throws Exception {
        Files.writeString(directory.resolve("guide.md"), "Java search engine");
        try (SearchHttpServer server = server()) {
            assertEquals(200, get(server, "/api/v1/health").statusCode());
            assertEquals("{\"status\":\"ok\"}", get(server, "/api/v1/health").body());
            assertEquals("{\"documents\":1,\"terms\":3}", get(server, "/api/v1/index").body());
            HttpResponse<String> response = get(server, "/api/v1/search?q=java+%22search+engine%22");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"path\":\"guide.md\""));
            assertTrue(response.body().contains("\"snippet\":\"Java search engine\""));
            assertEquals("application/json; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
        }
    }

    @Test
    void returnsJsonForBadRequestsAndUnknownEndpoints() throws Exception {
        try (SearchHttpServer server = server()) {
            assertEquals(400, get(server, "/api/v1/search").statusCode());
            assertTrue(get(server, "/api/v1/search?q=java&limit=0").body().contains("bad_request"));
            assertEquals(404, get(server, "/missing").statusCode());
            HttpRequest post = HttpRequest.newBuilder(uri(server, "/api/v1/search"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(post, HttpResponse.BodyHandlers.ofString());
            assertEquals(405, response.statusCode());
            assertEquals("GET", response.headers().firstValue("Allow").orElseThrow());
        }
    }

    @Test
    void rejectsBodiesOnGetRequests() throws Exception {
        try (SearchHttpServer server = server()) {
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/api/v1/health"))
                    .method("GET", HttpRequest.BodyPublishers.ofString("unexpected"))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("must not contain a body"));
        }
    }

    @Test
    void pagesSearchResultsInStableOrder() throws Exception {
        Files.writeString(directory.resolve("a.md"), "java");
        Files.writeString(directory.resolve("b.md"), "java");
        try (SearchHttpServer server = server()) {
            HttpResponse<String> response = get(server, "/api/v1/search?q=java&offset=1&limit=1");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"total\":2"));
            assertTrue(response.body().contains("\"offset\":1"));
            assertTrue(response.body().contains("\"path\":\"b.md\""));
            assertFalse(response.body().contains("\"path\":\"a.md\""));
        }
    }

    @Test
    void servesTheLatestSavedIndexAfterRefresh() throws Exception {
        Path document = directory.resolve("note.md");
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(document, "alpha");
        try (SearchHttpServer server = server()) {
            Files.writeString(document, "bravo");
            new IndexRepository().refresh(directory, snapshot);

            assertTrue(get(server, "/api/v1/search?q=bravo").body()
                    .contains("\"path\":\"note.md\""));
            assertTrue(get(server, "/api/v1/search?q=alpha").body()
                    .contains("\"results\":[]"));
        }
    }

    private SearchHttpServer server() throws IOException {
        Path snapshot = directory.resolve("index.bin");
        new IndexRepository().refresh(directory, snapshot);
        SearchHttpServer server = new SearchHttpServer(snapshot,
                new InetSocketAddress("127.0.0.1", 0));
        server.start();
        return server;
    }

    private static HttpResponse<String> get(SearchHttpServer server, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(server, path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(SearchHttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.address().getPort() + path);
    }
}
