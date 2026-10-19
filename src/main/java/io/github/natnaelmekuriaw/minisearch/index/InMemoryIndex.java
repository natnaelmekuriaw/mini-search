package io.github.natnaelmekuriaw.minisearch.index;

import io.github.natnaelmekuriaw.minisearch.text.Token;
import io.github.natnaelmekuriaw.minisearch.text.Tokenizer;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InMemoryIndex {
    private final List<IndexedDocument> documents;
    private final Map<String, List<Posting>> postings;
    private final Tokenizer tokenizer;

    InMemoryIndex(List<IndexedDocument> documents, Map<String, List<Posting>> postings,
                  Tokenizer tokenizer) {
        this.documents = List.copyOf(documents);
        this.postings = Map.copyOf(postings);
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
    }

    public List<IndexedDocument> documents() {
        return documents;
    }

    public int termCount() {
        return postings.size();
    }

    public List<Posting> postings(String term) {
        List<Token> tokens = tokenizer.tokenize(term);
        if (tokens.size() > 1) {
            throw new IllegalArgumentException("Expected one search term");
        }
        if (tokens.isEmpty()) {
            return List.of();
        }
        return postings.getOrDefault(tokens.getFirst().term(), List.of());
    }
}
