package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiParametersTest {
    private static final Set<String> SEARCH = Set.of("q", "limit");

    @Test
    void decodesUtf8SpacesAndEscapedSymbols() {
        assertEquals(Map.of("q", "café java + search", "limit", "10"),
                ApiParameters.parse("q=caf%C3%A9+java+%2B+search&limit=10", SEARCH));
    }

    @Test
    void rejectsDuplicateUnknownAndMalformedParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("q=java&q=search", SEARCH));
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("other=x", SEARCH));
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("q", SEARCH));
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("q=%GG", SEARCH));
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("q=%C3%28", SEARCH));
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("q=%00", SEARCH));
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("q=%١A", SEARCH));
    }

    @Test
    void rejectsLongQueryStrings() {
        assertThrows(IllegalArgumentException.class,
                () -> ApiParameters.parse("q=" + "a".repeat(16_384), SEARCH));
    }
}
