package io.github.natnaelmekuriaw.minisearch.http;

import java.util.Objects;

final class JsonStrings {
    private JsonStrings() {
    }

    static String quote(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder json = new StringBuilder(value.length() + 2).append('"');
        for (int offset = 0; offset < value.length(); offset++) {
            char character = value.charAt(offset);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u");
                        appendHex(json, character);
                    } else if (Character.isHighSurrogate(character)) {
                        if (offset + 1 < value.length()
                                && Character.isLowSurrogate(value.charAt(offset + 1))) {
                            json.append(character).append(value.charAt(++offset));
                        } else {
                            json.append("\\ufffd");
                        }
                    } else if (Character.isLowSurrogate(character)) {
                        json.append("\\ufffd");
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private static void appendHex(StringBuilder json, char character) {
        for (int shift = 12; shift >= 0; shift -= 4) {
            json.append(Character.forDigit((character >>> shift) & 0xf, 16));
        }
    }
}
