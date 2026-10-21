package io.github.natnaelmekuriaw.minisearch.cli;

import io.github.natnaelmekuriaw.minisearch.index.IndexRepository;
import io.github.natnaelmekuriaw.minisearch.index.IndexUpdate;
import io.github.natnaelmekuriaw.minisearch.http.SearchHttpServer;
import io.github.natnaelmekuriaw.minisearch.search.SearchResult;
import io.github.natnaelmekuriaw.minisearch.search.SearchService;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public final class MiniSearchCli {
    private static final String USAGE = """
            Usage:
              java -jar <jar-file> index --source <directory> --index <file>
              java -jar <jar-file> search --index <file> --query <text> [--limit <number>]
              java -jar <jar-file> suggest --index <file> --prefix <word> [--limit <number>]
              java -jar <jar-file> serve --index <file> [--host <address>] [--port <number>]
              java -jar <jar-file> --help
            """;

    private final IndexRepository repository = new IndexRepository();

    public static void main(String[] args) {
        int exitCode = new MiniSearchCli().run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("help"))) {
            out.print(USAGE);
            return 0;
        }
        if (args.length == 0) {
            err.print(USAGE);
            return 2;
        }
        if (args.length == 2 && args[1].equals("--help")
                && (args[0].equals("index") || args[0].equals("search")
                || args[0].equals("suggest") || args[0].equals("serve"))) {
            out.print(USAGE);
            return 0;
        }

        try {
            switch (args[0]) {
                case "index" -> index(args, out);
                case "search" -> search(args, out);
                case "suggest" -> suggest(args, out);
                case "serve" -> serve(args, out);
                default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
            }
            return 0;
        } catch (IllegalArgumentException exception) {
            err.println("Error: " + exception.getMessage());
            err.println("Run with --help for usage.");
            return 2;
        } catch (IOException exception) {
            err.println("Error: " + exception.getMessage());
            return 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    private void index(String[] args, PrintStream out) throws IOException {
        Map<String, String> options = parseOptions(args, Set.of("source", "index"));
        Path source = Path.of(required(options, "source"));
        Path snapshot = Path.of(required(options, "index"));
        IndexUpdate update = repository.refresh(source, snapshot);
        out.printf(Locale.ROOT, "Indexed %d documents: %d added, %d changed, %d removed, %d unchanged.%n",
                update.index().documents().size(), update.added(), update.changed(),
                update.removed(), update.unchanged());
    }

    private void search(String[] args, PrintStream out) throws IOException {
        Map<String, String> options = parseOptions(args, Set.of("index", "query", "limit"));
        Path snapshot = Path.of(required(options, "index"));
        String query = required(options, "query");
        int limit = parseLimit(options.getOrDefault("limit", "20"));
        List<SearchResult> results = new SearchService(repository.open(snapshot)).search(query, limit);
        out.printf(Locale.ROOT, "%d result%s%n", results.size(), results.size() == 1 ? "" : "s");
        for (SearchResult result : results) {
            out.printf(Locale.ROOT, "%s  %.3f%n", result.path(), result.score());
            if (!result.snippet().isEmpty()) {
                out.println("  " + result.snippet());
            }
        }
    }

    private void suggest(String[] args, PrintStream out) throws IOException {
        Map<String, String> options = parseOptions(args, Set.of("index", "prefix", "limit"));
        Path snapshot = Path.of(required(options, "index"));
        String prefix = required(options, "prefix");
        int limit = parseLimit(options.getOrDefault("limit", "10"));
        List<String> suggestions = new SearchService(repository.open(snapshot))
                .suggest(prefix, limit);
        for (String suggestion : suggestions) {
            out.println(suggestion);
        }
    }

    private void serve(String[] args, PrintStream out) throws IOException, InterruptedException {
        Map<String, String> options = parseOptions(args, Set.of("index", "host", "port"));
        Path snapshot = Path.of(required(options, "index"));
        String host = options.getOrDefault("host", "127.0.0.1");
        if (host.isBlank()) {
            throw new IllegalArgumentException("--host must not be blank");
        }
        int port = parsePort(options.getOrDefault("port", "8080"));
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("Unable to resolve --host: " + host);
        }
        try (SearchHttpServer server = new SearchHttpServer(snapshot, address)) {
            server.start();
            out.println("Serving " + serverUrl(host, server.address().getPort()));
            new CountDownLatch(1).await();
        }
    }

    private static Map<String, String> parseOptions(String[] args, Set<String> allowed) {
        Map<String, String> options = new HashMap<>();
        for (int position = 1; position < args.length; position += 2) {
            String option = args[position];
            if (!option.startsWith("--") || !allowed.contains(option.substring(2))) {
                throw new IllegalArgumentException("Unknown option: " + option);
            }
            if (position + 1 >= args.length || args[position + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            if (options.putIfAbsent(option.substring(2), args[position + 1]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + option);
            }
        }
        return Map.copyOf(options);
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing --" + name);
        }
        return value;
    }

    private static int parseLimit(String value) {
        int limit;
        try {
            limit = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--limit must be a positive integer", exception);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("--limit must be a positive integer");
        }
        return limit;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65_535) {
                return port;
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--port must be between 1 and 65535", exception);
        }
        throw new IllegalArgumentException("--port must be between 1 and 65535");
    }

    static String serverUrl(String host, int port) {
        String address = host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))
                ? "[" + host + "]" : host;
        return "http://" + address + ":" + port;
    }
}
