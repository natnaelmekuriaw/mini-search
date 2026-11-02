package io.github.natnaelmekuriaw.minisearch.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tokenizer {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*");

    public List<Token> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            Matcher normalized = WORD.matcher(normalize(matcher.group()));
            while (normalized.find()) {
                tokens.add(new Token(normalized.group(), tokens.size()));
            }
        }
        return List.copyOf(tokens);
    }

    public String normalizeWord(String word) {
        Objects.requireNonNull(word, "word");
        if (!WORD.matcher(word).matches()) {
            throw new IllegalArgumentException("Expected one searchable word");
        }
        String normalized = normalize(word);
        if (!WORD.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Word normalizes to multiple searchable terms");
        }
        return normalized;
    }

    public Optional<TokenSpan> firstMatch(String text, Set<String> terms) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(terms, "terms");
        Matcher matcher = WORD.matcher(text);
        int position = 0;
        while (matcher.find()) {
            Matcher normalized = WORD.matcher(normalize(matcher.group()));
            while (normalized.find()) {
                String term = normalized.group();
                if (terms.isEmpty() || terms.contains(term)) {
                    return Optional.of(new TokenSpan(term, position, matcher.start(), matcher.end()));
                }
                position++;
            }
        }
        return Optional.empty();
    }

    private static String normalize(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
