package io.github.natnaelmekuriaw.minisearch.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tokenizer {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*");

    public List<Token> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String term = Normalizer.normalize(matcher.group(), Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT);
            tokens.add(new Token(term, tokens.size()));
        }
        return List.copyOf(tokens);
    }
}
