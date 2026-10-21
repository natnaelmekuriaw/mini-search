package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.natnaelmekuriaw.minisearch.index.IndexBuilder;
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

    private SearchHttpServer server() throws IOException {
        SearchHttpServer server = new SearchHttpServer(new IndexBuilder().build(directory),
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
