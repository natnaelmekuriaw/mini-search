package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    void searchesBooleanQueriesAndRejectsMalformedGroups() throws Exception {
        Files.writeString(directory.resolve("java.md"), "java search engine");
        Files.writeString(directory.resolve("kotlin.md"), "kotlin search engine");
        Files.writeString(directory.resolve("draft.md"), "java search engine draft");
        try (SearchHttpServer server = server()) {
            String query = URLEncoder.encode("(java OR kotlin) \"search engine\" -draft",
                    StandardCharsets.UTF_8);
            HttpResponse<String> response = get(server, "/api/v1/search?q=" + query);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"total\":2"));
            assertTrue(response.body().contains("\"path\":\"java.md\""));
            assertTrue(response.body().contains("\"path\":\"kotlin.md\""));
            assertFalse(response.body().contains("\"path\":\"draft.md\""));
            assertEquals(400, get(server, "/api/v1/search?q=java+OR").statusCode());
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

    @Test
    void reportsUnavailableSnapshotsAndRecoversAfterReplacement() throws Exception {
        Path document = directory.resolve("note.md");
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(document, "alpha");
        try (SearchHttpServer server = server()) {
            byte[] damaged = Files.readAllBytes(snapshot);
            damaged[damaged.length - 1] ^= 1;
            Files.write(snapshot, damaged);

            HttpResponse<String> unavailable = get(server, "/api/v1/search?q=alpha");
            assertEquals(503, unavailable.statusCode());
            assertTrue(unavailable.body().contains("index_unavailable"));
            assertEquals("1", unavailable.headers().firstValue("Retry-After").orElseThrow());

            Files.delete(snapshot);
            new IndexRepository().refresh(directory, snapshot);
            assertEquals(200, get(server, "/api/v1/search?q=alpha").statusCode());
        }
    }

    @Test
    void searchesUnicodePhrasesAndFiltersWithEscapedSnippets() throws Exception {
        Path docs = Files.createDirectory(directory.resolve("docs"));
        Files.writeString(docs.resolve("guide.md"), "café \"search engine\"");
        Files.writeString(directory.resolve("other.txt"), "café search engine");
        try (SearchHttpServer server = server()) {
            String query = URLEncoder.encode("café \"search engine\" type:md path:docs/",
                    StandardCharsets.UTF_8);
            HttpResponse<String> response = get(server, "/api/v1/search?q=" + query);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"total\":1"));
            assertTrue(response.body().contains("\"path\":\"docs/guide.md\""));
            assertTrue(response.body().contains("\\\"search engine\\\""));
        }
    }

    @Test
    void keepsConcurrentSearchesAvailableDuringSnapshotReplacement() throws Exception {
        Path document = directory.resolve("note.md");
        Path snapshot = directory.resolve("index.bin");
        Files.writeString(document, "alpha");
        try (SearchHttpServer server = server(); HttpClient client = HttpClient.newHttpClient()) {
            List<CompletableFuture<HttpResponse<String>>> requests = new ArrayList<>();
            for (int request = 0; request < 20; request++) {
                HttpRequest search = HttpRequest.newBuilder(uri(server, "/api/v1/search?q=alpha"))
                        .GET().build();
                requests.add(client.sendAsync(search, HttpResponse.BodyHandlers.ofString()));
            }
            Files.writeString(document, "bravo");
            new IndexRepository().refresh(directory, snapshot);

            for (CompletableFuture<HttpResponse<String>> request : requests) {
                assertEquals(200, request.join().statusCode());
            }
            assertTrue(get(server, "/api/v1/search?q=bravo").body()
                    .contains("\"path\":\"note.md\""));
        }
    }

    @Test
    void validatesMalformedAndRepeatedHttpParameters() throws Exception {
        try (SearchHttpServer server = server()) {
            for (String query : List.of("q=%C3%28", "q=java&q=search",
                    "q=java&unknown=x", "q=java&offset=-1", "q=java&limit=101")) {
                HttpResponse<String> response = get(server, "/api/v1/search?" + query);
                assertEquals(400, response.statusCode(), query);
                assertTrue(response.body().contains("\"code\":\"bad_request\""), query);
            }
            assertEquals(400, get(server, "/api/v1/health?unexpected=1").statusCode());
        }
    }

    @Test
    void marksResponsesAsJsonAndDisablesCachingAndSniffing() throws Exception {
        try (SearchHttpServer server = server()) {
            for (String path : List.of("/api/v1/health", "/api/v1/search?q=java", "/missing")) {
                HttpResponse<String> response = get(server, path);
                assertEquals("application/json; charset=utf-8",
                        response.headers().firstValue("Content-Type").orElseThrow());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                assertEquals("nosniff",
                        response.headers().firstValue("X-Content-Type-Options").orElseThrow());
            }
        }
    }

    @Test
    void servesEmptyIndexesWithoutSpecialCases() throws Exception {
        try (SearchHttpServer server = server()) {
            assertEquals("{\"documents\":0,\"terms\":0}",
                    get(server, "/api/v1/index").body());
            assertEquals("{\"total\":0,\"offset\":0,\"limit\":20,\"results\":[]}",
                    get(server, "/api/v1/search?q=java").body());
            assertEquals("{\"total\":0,\"offset\":5,\"limit\":2,\"results\":[]}",
                    get(server, "/api/v1/search?q=java&offset=5&limit=2").body());
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
