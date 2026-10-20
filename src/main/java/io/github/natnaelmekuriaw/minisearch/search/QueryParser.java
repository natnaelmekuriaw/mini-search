package io.github.natnaelmekuriaw.minisearch.search;

import io.github.natnaelmekuriaw.minisearch.text.Token;
import io.github.natnaelmekuriaw.minisearch.text.Tokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class QueryParser {
    private final Tokenizer tokenizer = new Tokenizer();

    public SearchQuery parse(String input) {
        Objects.requireNonNull(input, "input");
        List<SearchClause> clauses = new ArrayList<>();
        String fileType = null;
        String pathPrefix = null;

        int cursor = 0;
        while (cursor < input.length()) {
            if (Character.isWhitespace(input.charAt(cursor))) {
                cursor++;
                continue;
            }
            if (input.charAt(cursor) == '"') {
                int end = input.indexOf('"', cursor + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed quoted phrase");
                }
                List<String> terms = tokenizer.tokenize(input.substring(cursor + 1, end))
                        .stream().map(Token::term).toList();
                if (terms.isEmpty()) {
                    throw new IllegalArgumentException("Quoted phrase has no searchable terms");
                }
                clauses.add(new PhraseClause(terms));
                cursor = end + 1;
                if (cursor < input.length() && !Character.isWhitespace(input.charAt(cursor))) {
                    throw new IllegalArgumentException("A phrase must be separated by whitespace");
                }
                continue;
            }

            int end = cursor;
            while (end < input.length() && !Character.isWhitespace(input.charAt(end))) {
                end++;
            }
            String part = input.substring(cursor, end);
            if (part.indexOf('"') >= 0) {
                throw new IllegalArgumentException("Unexpected quote in query term");
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.startsWith("type:")) {
                if (fileType != null) {
                    throw new IllegalArgumentException("Only one type filter is allowed");
                }
                fileType = lower.substring("type:".length());
                if (!fileType.equals("md") && !fileType.equals("txt")) {
                    throw new IllegalArgumentException("File type must be md or txt");
                }
            } else if (lower.startsWith("path:")) {
                if (pathPrefix != null) {
                    throw new IllegalArgumentException("Only one path filter is allowed");
                }
                pathPrefix = part.substring("path:".length()).replace('\\', '/');
                if (pathPrefix.isEmpty() || pathPrefix.startsWith("/")) {
                    throw new IllegalArgumentException("Path filter must be a relative prefix");
                }
            } else {
                if (part.indexOf(':') >= 0) {
                    throw new IllegalArgumentException("Unknown query filter: " + part);
                }
                List<Token> tokens = tokenizer.tokenize(part);
                if (tokens.isEmpty()) {
                    throw new IllegalArgumentException("Query term has no searchable text");
                }
                for (Token token : tokens) {
                    clauses.add(new TermClause(token.term()));
                }
            }
            cursor = end;
        }
        return new SearchQuery(clauses, Optional.ofNullable(fileType),
                Optional.ofNullable(pathPrefix));
    }
}
