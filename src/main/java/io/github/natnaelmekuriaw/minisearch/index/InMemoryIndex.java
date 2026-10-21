package io.github.natnaelmekuriaw.minisearch.index;

import io.github.natnaelmekuriaw.minisearch.text.Token;
import io.github.natnaelmekuriaw.minisearch.text.Tokenizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

public final class InMemoryIndex {
    private final List<IndexedDocument> documents;
    private final Map<String, List<Posting>> postings;
    private final Map<String, String> contents;
    private final NavigableSet<String> terms;
    private final Tokenizer tokenizer;

    InMemoryIndex(List<IndexedDocument> documents, Map<String, List<Posting>> postings,
                  Map<String, String> contents, Tokenizer tokenizer) {
        this.documents = List.copyOf(documents);
        this.postings = Map.copyOf(postings);
        this.terms = Collections.unmodifiableNavigableSet(new TreeSet<>(postings.keySet()));
        this.contents = Map.copyOf(contents);
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
    }

    public List<IndexedDocument> documents() {
        return documents;
    }

    public int termCount() {
        return postings.size();
    }

    Map<String, List<Posting>> allPostings() {
        return postings;
    }

    public Optional<String> content(String path) {
        return Optional.ofNullable(contents.get(Objects.requireNonNull(path, "path")));
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

    public List<String> termsStartingWith(String prefix, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String normalized = tokenizer.normalizeWord(prefix);
        List<String> matches = new ArrayList<>();
        for (String term : terms.tailSet(normalized, true)) {
            if (!term.startsWith(normalized) || matches.size() == limit) {
                break;
            }
            matches.add(term);
        }
        return List.copyOf(matches);
    }
}
