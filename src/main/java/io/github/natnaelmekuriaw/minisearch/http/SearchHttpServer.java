package io.github.natnaelmekuriaw.minisearch.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.nio.file.Path;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SearchHttpServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(SearchHttpServer.class.getName());
    private final HttpServer server;
    private final ExecutorService executor;
    private final IndexSnapshotProvider snapshots;

    public SearchHttpServer(Path snapshot, InetSocketAddress address) throws IOException {
        this.snapshots = new IndexSnapshotProvider(Objects.requireNonNull(snapshot, "snapshot"));
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
            IndexSnapshotProvider.View view;
            try {
                view = snapshots.current();
            } catch (IOException exception) {
                exchange.getResponseHeaders().set("Retry-After", "1");
                respond(exchange, 503, ApiJson.error("index_unavailable", "Saved index is unavailable"));
                return;
            }
            String body = switch (path) {
                case "/api/v1/health" -> {
                    ApiParameters.parse(rawQuery, Set.of());
                    yield ApiJson.health();
                }
                case "/api/v1/index" -> {
                    ApiParameters.parse(rawQuery, Set.of());
                    yield ApiJson.index(view.index().documents().size(), view.index().termCount());
                }
                case "/api/v1/search" -> {
                    SearchRequest request = SearchRequest.parse(rawQuery);
                    yield ApiJson.results(view.search().searchPage(request.query(),
                            request.offset(), request.limit()));
                }
                default -> throw new IllegalStateException("Unreachable endpoint");
            };
            respond(exchange, 200, body);
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, ApiJson.error("bad_request", exception.getMessage()));
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "HTTP request failed", exception);
            respond(exchange, 500, ApiJson.error("internal_error", "Internal server error"));
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
