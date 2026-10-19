package io.github.natnaelmekuriaw.minisearch.index;

import io.github.natnaelmekuriaw.minisearch.source.DocumentScanner;
import io.github.natnaelmekuriaw.minisearch.source.SourceDocument;
import io.github.natnaelmekuriaw.minisearch.text.Token;
import io.github.natnaelmekuriaw.minisearch.text.Tokenizer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class IndexBuilder {
    private final DocumentScanner scanner = new DocumentScanner();
    private final Tokenizer tokenizer = new Tokenizer();

    public InMemoryIndex build(Path root) throws IOException {
        List<IndexedDocument> documents = new ArrayList<>();
        Map<String, Map<String, List<Integer>>> terms = new TreeMap<>();

        for (SourceDocument source : scanner.scan(root)) {
            List<Token> tokens = tokenizer.tokenize(source.content());
            documents.add(new IndexedDocument(source.path(), tokens.size()));
            for (Token token : tokens) {
                terms.computeIfAbsent(token.term(), ignored -> new TreeMap<>())
                        .computeIfAbsent(source.path(), ignored -> new ArrayList<>())
                        .add(token.position());
            }
        }

        Map<String, List<Posting>> postings = new TreeMap<>();
        for (Map.Entry<String, Map<String, List<Integer>>> term : terms.entrySet()) {
            List<Posting> termPostings = new ArrayList<>();
            for (Map.Entry<String, List<Integer>> document : term.getValue().entrySet()) {
                termPostings.add(new Posting(document.getKey(), document.getValue()));
            }
            postings.put(term.getKey(), List.copyOf(termPostings));
        }
        return new InMemoryIndex(documents, postings, tokenizer);
    }
}
