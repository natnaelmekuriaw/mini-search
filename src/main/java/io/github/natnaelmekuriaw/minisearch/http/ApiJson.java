package io.github.natnaelmekuriaw.minisearch.http;

import io.github.natnaelmekuriaw.minisearch.search.SearchResult;
import java.util.List;
import java.util.Objects;

final class ApiJson {
    private ApiJson() {
    }

    static String results(List<SearchResult> results) {
        Objects.requireNonNull(results, "results");
        StringBuilder json = new StringBuilder("{\"results\":[");
        for (int position = 0; position < results.size(); position++) {
            if (position > 0) {
                json.append(',');
            }
            SearchResult result = results.get(position);
            json.append("{\"path\":").append(JsonStrings.quote(result.path()))
                    .append(",\"score\":").append(result.score())
                    .append(",\"snippet\":").append(JsonStrings.quote(result.snippet()))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    static String error(String code, String message) {
        return "{\"error\":{\"code\":" + JsonStrings.quote(code)
                + ",\"message\":" + JsonStrings.quote(message) + "}}";
    }

    static String index(int documents, int terms) {
        return "{\"documents\":" + documents + ",\"terms\":" + terms + '}';
    }

    static String health() {
        return "{\"status\":\"ok\"}";
    }
}
