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
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class IndexBuilder {
    private final DocumentScanner scanner = new DocumentScanner();
    private final Tokenizer tokenizer = new Tokenizer();

    public InMemoryIndex build(Path root) throws IOException {
        return assemble(scanner.scan(root), null).index();
    }

    public IndexUpdate update(Path root, InMemoryIndex previous) throws IOException {
        Objects.requireNonNull(previous, "previous");
        return assemble(scanner.scan(root), previous);
    }

    private IndexUpdate assemble(List<SourceDocument> sources, InMemoryIndex previous) {
        List<IndexedDocument> documents = new ArrayList<>();
        Map<String, Map<String, List<Integer>>> terms = new TreeMap<>();
        Map<String, String> contents = new TreeMap<>();
        Map<String, IndexedDocument> oldDocuments = new TreeMap<>();
        if (previous != null) {
            for (IndexedDocument document : previous.documents()) {
                oldDocuments.put(document.path(), document);
            }
        }
        Set<String> unchangedPaths = new TreeSet<>();
        int added = 0;
        int changed = 0;

        for (SourceDocument source : sources) {
            IndexedDocument old = oldDocuments.remove(source.path());
            if (old != null && previous.content(source.path()).orElseThrow().equals(source.content())) {
                unchangedPaths.add(source.path());
                documents.add(old);
                contents.put(source.path(), source.content());
                continue;
            }
            if (old == null) {
                added++;
            } else {
                changed++;
            }
            List<Token> tokens = tokenizer.tokenize(source.content());
            documents.add(new IndexedDocument(source.path(), tokens.size()));
            contents.put(source.path(), source.content());
            for (Token token : tokens) {
                terms.computeIfAbsent(token.term(), ignored -> new TreeMap<>())
                        .computeIfAbsent(source.path(), ignored -> new ArrayList<>())
                        .add(token.position());
            }
        }

        if (previous != null) {
            for (Map.Entry<String, List<Posting>> term : previous.allPostings().entrySet()) {
                for (Posting posting : term.getValue()) {
                    if (unchangedPaths.contains(posting.path())) {
                        terms.computeIfAbsent(term.getKey(), ignored -> new TreeMap<>())
                                .put(posting.path(), posting.positions());
                    }
                }
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
        return new IndexUpdate(new InMemoryIndex(documents, postings, contents, tokenizer),
                added, changed, oldDocuments.size(), unchangedPaths.size());
    }
}
