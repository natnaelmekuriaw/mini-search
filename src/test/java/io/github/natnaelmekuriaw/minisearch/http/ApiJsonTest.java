package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.natnaelmekuriaw.minisearch.search.SearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiJsonTest {
    @Test
    void serializesRankedResultsAndEscapesContent() {
        assertEquals("{\"results\":[{\"path\":\"a.md\",\"score\":1.5,\"snippet\":\"say \\\"hi\\\"\"},"
                        + "{\"path\":\"b.txt\",\"score\":0.0,\"snippet\":\"\"}]}",
                ApiJson.results(List.of(new SearchResult("a.md", 1.5, "say \"hi\""),
                        new SearchResult("b.txt", 0, ""))));
        assertEquals("{\"results\":[]}", ApiJson.results(List.of()));
    }

    @Test
    void serializesErrorsAndIndexMetadata() {
        assertEquals("{\"error\":{\"code\":\"bad_request\",\"message\":\"bad \\\"query\\\"\"}}",
                ApiJson.error("bad_request", "bad \"query\""));
        assertEquals("{\"documents\":2,\"terms\":5}", ApiJson.index(2, 5));
        assertEquals("{\"status\":\"ok\"}", ApiJson.health());
    }
}
