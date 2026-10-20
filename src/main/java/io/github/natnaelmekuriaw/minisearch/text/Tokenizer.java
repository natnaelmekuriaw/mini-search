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
        List<Token> tokens = new ArrayList<>();
        for (TokenSpan span : locate(text)) {
            tokens.add(new Token(span.term(), span.position()));
        }
        return List.copyOf(tokens);
    }

    public List<TokenSpan> locate(String text) {
        Objects.requireNonNull(text, "text");
        List<TokenSpan> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String term = normalize(matcher.group());
            tokens.add(new TokenSpan(term, tokens.size(), matcher.start(), matcher.end()));
        }
        return List.copyOf(tokens);
    }

    public Optional<TokenSpan> firstMatch(String text, Set<String> terms) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(terms, "terms");
        Matcher matcher = WORD.matcher(text);
        int position = 0;
        while (matcher.find()) {
            String term = normalize(matcher.group());
            if (terms.isEmpty() || terms.contains(term)) {
                return Optional.of(new TokenSpan(term, position, matcher.start(), matcher.end()));
            }
            position++;
        }
        return Optional.empty();
    }

    private static String normalize(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
