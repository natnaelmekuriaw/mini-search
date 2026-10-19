package io.github.natnaelmekuriaw.minisearch.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokenizerTest {
    private final Tokenizer tokenizer = new Tokenizer();

    @Test
    void assignsConsecutivePositionsAcrossPunctuationAndWhitespace() {
        assertEquals(List.of(new Token("hello", 0), new Token("world", 1)),
                tokenizer.tokenize("Hello,\n world!"));
    }

    @Test
    void normalizesUnicodeAndCaseWithoutDependingOnDefaultLocale() {
        assertEquals(List.of(new Token("café", 0), new Token("hello", 1)),
                tokenizer.tokenize("CAFE\u0301 ＨＥＬＬＯ"));
    }

    @Test
    void retainsNumbersAndRepeatedWords() {
        assertEquals(List.of(new Token("42", 0), new Token("go", 1), new Token("go", 2)),
                tokenizer.tokenize("42 go GO"));
    }

    @Test
    void returnsAnImmutableEmptyListForNonWords() {
        List<Token> tokens = tokenizer.tokenize("  ...  ");
        assertEquals(List.of(), tokens);
        assertThrows(UnsupportedOperationException.class, () -> tokens.add(new Token("x", 0)));
    }

    @Test
    void rejectsNullText() {
        assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
    }
}
