package io.github.natnaelmekuriaw.minisearch.http;

import java.util.Map;
import java.util.Set;

record SearchRequest(String query, int offset, int limit) {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_QUERY_LENGTH = 4_096;

    SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Missing q parameter");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search query is too long");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be nonnegative");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    static SearchRequest parse(String rawQuery) {
        Map<String, String> parameters = ApiParameters.parse(rawQuery, Set.of("q", "limit", "offset"));
        String query = parameters.get("q");
        int offset = parseInteger(parameters.getOrDefault("offset", "0"), "offset");
        int limit = parseInteger(parameters.getOrDefault("limit", Integer.toString(DEFAULT_LIMIT)), "limit");
        return new SearchRequest(query, offset, limit);
    }

    private static int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }
}
