package io.github.natnaelmekuriaw.minisearch.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.natnaelmekuriaw.minisearch.index.InMemoryIndex;
import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SearchHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final InMemoryIndex index;
    private final SearchService search;

    public SearchHttpServer(InMemoryIndex index, InetSocketAddress address) throws IOException {
        this.index = Objects.requireNonNull(index, "index");
        this.search = new SearchService(index);
        this.server = HttpServer.create(Objects.requireNonNull(address, "address"), 0);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getRawPath();
            if (!Set.of("/api/v1/health", "/api/v1/index", "/api/v1/search").contains(path)) {
                respond(exchange, 404, ApiJson.error("not_found", "Unknown endpoint"));
                return;
            }
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                respond(exchange, 405, ApiJson.error("method_not_allowed", "Use GET"));
                return;
            }
            String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
            if ((contentLength != null && !contentLength.equals("0"))
                    || exchange.getRequestHeaders().containsKey("Transfer-Encoding")) {
                throw new IllegalArgumentException("GET requests must not contain a body");
            }
            String rawQuery = exchange.getRequestURI().getRawQuery();
            String body = switch (path) {
                case "/api/v1/health" -> {
                    ApiParameters.parse(rawQuery, Set.of());
                    yield ApiJson.health();
                }
                case "/api/v1/index" -> {
                    ApiParameters.parse(rawQuery, Set.of());
                    yield ApiJson.index(index.documents().size(), index.termCount());
                }
                case "/api/v1/search" -> {
                    SearchRequest request = SearchRequest.parse(rawQuery);
                    yield ApiJson.results(search.search(request.query(), request.limit()));
                }
                default -> throw new IllegalStateException("Unreachable endpoint");
            };
            respond(exchange, 200, body);
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, ApiJson.error("bad_request", exception.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
