package io.github.natnaelmekuriaw.minisearch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonStringsTest {
    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001\"",
                JsonStrings.quote("a\"b\\c\n\t\u0001"));
    }

    @Test
    void preservesUnicodeAndReplacesUnpairedSurrogates() {
        assertEquals("\"café 😀\\ufffd\"", JsonStrings.quote("café 😀\ud800"));
    }
}
