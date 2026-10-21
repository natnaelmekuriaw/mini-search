package io.github.natnaelmekuriaw.minisearch.search;

import io.github.natnaelmekuriaw.minisearch.index.IndexedDocument;
import io.github.natnaelmekuriaw.minisearch.index.InMemoryIndex;
import io.github.natnaelmekuriaw.minisearch.index.Posting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SearchService {
    private static final int DEFAULT_LIMIT = 20;

    private final InMemoryIndex index;
    private final Map<String, IndexedDocument> documents;
    private final double averageDocumentLength;
    private final QueryParser parser = new QueryParser();
    private final PhraseMatcher phraseMatcher = new PhraseMatcher();
    private final Bm25Scorer scorer = new Bm25Scorer();
    private final SnippetBuilder snippetBuilder = new SnippetBuilder();

    public SearchService(InMemoryIndex index) {
        this.index = Objects.requireNonNull(index, "index");
        Map<String, IndexedDocument> byPath = new HashMap<>();
        long totalTokens = 0;
        for (IndexedDocument document : index.documents()) {
            byPath.put(document.path(), document);
            totalTokens += document.tokenCount();
        }
        this.documents = Map.copyOf(byPath);
        this.averageDocumentLength = byPath.isEmpty() ? 0 : (double) totalTokens / byPath.size();
    }

    public List<SearchResult> search(String input) {
        return search(input, DEFAULT_LIMIT);
    }

    public List<SearchResult> search(String input, int limit) {
        return searchPage(input, 0, limit).results();
    }

    public SearchPage searchPage(String input, int offset, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be nonnegative");
        }
        SearchQuery query = parser.parse(input);
        if (query.isEmpty() || documents.isEmpty()) {
            return new SearchPage(List.of(), 0, offset, limit);
        }

        Set<String> terms = new LinkedHashSet<>();
        for (SearchClause clause : query.clauses()) {
            terms.addAll(clause.terms());
        }
        Map<String, Map<String, Posting>> byTerm = loadPostings(terms);
        Set<String> candidates = candidates(byTerm);
        if (candidates.isEmpty()) {
            return new SearchPage(List.of(), 0, offset, limit);
        }

        List<ScoredPath> matches = new ArrayList<>();
        for (String path : candidates) {
            if (!passesFilters(path, query)) {
                continue;
            }
            Map<String, Posting> documentPostings = new HashMap<>();
            for (String term : terms) {
                documentPostings.put(term, byTerm.get(term).get(path));
            }
            if (!passesPhrases(query, documentPostings)) {
                continue;
            }
            double score = 0;
            for (String term : terms) {
                score += scorer.score(documents.size(), byTerm.get(term).size(),
                        documentPostings.get(term).frequency(),
                        documents.get(path).tokenCount(), averageDocumentLength);
            }
            matches.add(new ScoredPath(path, score));
        }

        matches.sort(Comparator.comparingDouble(ScoredPath::score).reversed()
                .thenComparing(ScoredPath::path));
        List<SearchResult> results = new ArrayList<>();
        for (int position = offset; position < Math.min((long) offset + limit, matches.size()); position++) {
            ScoredPath match = matches.get(position);
            String content = index.content(match.path()).orElseThrow();
            results.add(new SearchResult(match.path(), match.score(),
                    snippetBuilder.build(content, terms)));
        }
        return new SearchPage(results, matches.size(), offset, limit);
    }

    private Map<String, Map<String, Posting>> loadPostings(Set<String> terms) {
        Map<String, Map<String, Posting>> byTerm = new HashMap<>();
        for (String term : terms) {
            Map<String, Posting> byPath = new HashMap<>();
            for (Posting posting : index.postings(term)) {
                byPath.put(posting.path(), posting);
            }
            byTerm.put(term, byPath);
        }
        return byTerm;
    }

    private Set<String> candidates(Map<String, Map<String, Posting>> byTerm) {
        if (byTerm.isEmpty()) {
            return new HashSet<>(documents.keySet());
        }
        Set<String> paths = null;
        for (Map<String, Posting> postings : byTerm.values()) {
            if (paths == null) {
                paths = new HashSet<>(postings.keySet());
            } else {
                paths.retainAll(postings.keySet());
            }
            if (paths.isEmpty()) {
                return Set.of();
            }
        }
        return paths;
    }

    private boolean passesFilters(String path, SearchQuery query) {
        if (query.fileType().isPresent() && !path.toLowerCase(Locale.ROOT)
                .endsWith("." + query.fileType().orElseThrow())) {
            return false;
        }
        return query.pathPrefix().isEmpty() || path.startsWith(query.pathPrefix().orElseThrow());
    }

    private boolean passesPhrases(SearchQuery query, Map<String, Posting> documentPostings) {
        for (SearchClause clause : query.clauses()) {
            if (clause instanceof PhraseClause phrase
                    && !phraseMatcher.matches(phrase, documentPostings)) {
                return false;
            }
        }
        return true;
    }

    private record ScoredPath(String path, double score) {
    }
}
