package io.github.natnaelmekuriaw.minisearch.http;

import java.util.Map;
import java.util.Set;

record SuggestionRequest(String prefix, int limit) {
    SuggestionRequest {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Missing q parameter");
        }
        if (prefix.length() > 256) {
            throw new IllegalArgumentException("Suggestion prefix is too long");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("limit must be between 1 and 20");
        }
    }

    static SuggestionRequest parse(String rawQuery) {
        Map<String, String> parameters = ApiParameters.parse(rawQuery, Set.of("q", "limit"));
        int limit;
        try {
            limit = Integer.parseInt(parameters.getOrDefault("limit", "10"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit must be an integer", exception);
        }
        return new SuggestionRequest(parameters.get("q"), limit);
    }
}
