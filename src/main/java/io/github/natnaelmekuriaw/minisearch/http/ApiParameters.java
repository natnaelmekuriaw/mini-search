package io.github.natnaelmekuriaw.minisearch.http;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class ApiParameters {
    private static final int MAX_RAW_LENGTH = 16_384;

    private ApiParameters() {
    }

    static Map<String, String> parse(String raw, Set<String> allowed) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        if (raw.length() > MAX_RAW_LENGTH) {
            throw new IllegalArgumentException("Query string is too long");
        }
        Map<String, String> parameters = new HashMap<>();
        for (String pair : raw.split("&", -1)) {
            int separator = pair.indexOf('=');
            if (separator < 1) {
                throw new IllegalArgumentException("Invalid query parameter");
            }
            String name = decode(pair.substring(0, separator));
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("Unknown query parameter: " + name);
            }
            String value = decode(pair.substring(separator + 1));
            if (parameters.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate query parameter: " + name);
            }
        }
        return Map.copyOf(parameters);
    }

    private static String decode(String raw) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length());
        for (int offset = 0; offset < raw.length();) {
            int codepoint = raw.codePointAt(offset);
            if (codepoint == '%') {
                if (offset + 2 >= raw.length()) {
                    throw new IllegalArgumentException("Invalid percent encoding");
                }
                int high = Character.digit(raw.charAt(offset + 1), 16);
                int low = Character.digit(raw.charAt(offset + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Invalid percent encoding");
                }
                bytes.write((high << 4) | low);
                offset += 3;
            } else {
                if (codepoint >= Character.MIN_SURROGATE && codepoint <= Character.MAX_SURROGATE) {
                    throw new IllegalArgumentException("Invalid query encoding");
                }
                bytes.writeBytes((codepoint == '+' ? " " : new String(Character.toChars(codepoint)))
                        .getBytes(StandardCharsets.UTF_8));
                offset += Character.charCount(codepoint);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 query encoding", exception);
        }
    }
}
