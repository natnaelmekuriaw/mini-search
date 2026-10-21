package io.github.natnaelmekuriaw.minisearch.http;

import io.github.natnaelmekuriaw.minisearch.search.SearchPage;
import io.github.natnaelmekuriaw.minisearch.search.SearchResult;
import java.util.Objects;

final class ApiJson {
    private ApiJson() {
    }

    static String results(SearchPage page) {
        Objects.requireNonNull(page, "page");
        StringBuilder json = new StringBuilder("{\"total\":").append(page.totalMatches())
                .append(",\"offset\":").append(page.offset())
                .append(",\"limit\":").append(page.limit())
                .append(",\"results\":[");
        var results = page.results();
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
