package io.github.natnaelmekuriaw.minisearch.http;

import java.util.Map;
import java.util.Set;

record SearchRequest(String query, int limit) {
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
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    static SearchRequest parse(String rawQuery) {
        Map<String, String> parameters = ApiParameters.parse(rawQuery, Set.of("q", "limit"));
        String query = parameters.get("q");
        String rawLimit = parameters.get("limit");
        if (rawLimit == null) {
            return new SearchRequest(query, DEFAULT_LIMIT);
        }
        try {
            return new SearchRequest(query, Integer.parseInt(rawLimit));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT, exception);
        }
    }
}
