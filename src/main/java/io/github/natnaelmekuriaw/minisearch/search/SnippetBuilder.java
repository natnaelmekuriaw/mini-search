package io.github.natnaelmekuriaw.minisearch.search;

import io.github.natnaelmekuriaw.minisearch.text.TokenSpan;
import io.github.natnaelmekuriaw.minisearch.text.Tokenizer;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class SnippetBuilder {
    private static final int WIDTH = 160;
    private static final int LEAD = 60;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Tokenizer tokenizer = new Tokenizer();

    String build(String content, Set<String> queryTerms) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(queryTerms, "queryTerms");
        if (content.isBlank()) {
            return "";
        }

        int anchor = firstMatch(content, queryTerms);
        int start = Math.max(0, anchor - LEAD);
        int end = Math.min(content.length(), start + WIDTH);
        if (start > 0 && Character.isLowSurrogate(content.charAt(start))) {
            start--;
        }
        if (end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) {
            end--;
        }

        String excerpt = WHITESPACE.matcher(content.substring(start, end)).replaceAll(" ").trim();
        return (start > 0 ? "…" : "") + excerpt + (end < content.length() ? "…" : "");
    }

    private int firstMatch(String content, Set<String> queryTerms) {
        for (TokenSpan span : tokenizer.locate(content)) {
            if (queryTerms.isEmpty() || queryTerms.contains(span.term())) {
                return span.startOffset();
            }
        }
        for (int position = 0; position < content.length(); position++) {
            if (!Character.isWhitespace(content.charAt(position))) {
                return position;
            }
        }
        return 0;
    }
}
