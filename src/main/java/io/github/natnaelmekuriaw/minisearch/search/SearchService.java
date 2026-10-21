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
import java.util.Optional;
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
        Set<String> candidates = query.expression().isEmpty()
                ? new HashSet<>(documents.keySet())
                : candidates(query.expression().orElseThrow(), byTerm);
        if (candidates.isEmpty()) {
            return new SearchPage(List.of(), 0, offset, limit);
        }

        List<ScoredPath> matches = new ArrayList<>();
        for (String path : candidates) {
            if (!passesFilters(path, query)) {
                continue;
            }
            Set<String> matchedTerms = query.expression().isEmpty() ? Set.of()
                    : matchingTerms(query.expression().orElseThrow(), path, byTerm).orElseThrow();
            double score = 0;
            for (String term : matchedTerms) {
                score += scorer.score(documents.size(), byTerm.get(term).size(),
                        byTerm.get(term).get(path).frequency(),
                        documents.get(path).tokenCount(), averageDocumentLength);
            }
            matches.add(new ScoredPath(path, score, matchedTerms));
        }

        matches.sort(Comparator.comparingDouble(ScoredPath::score).reversed()
                .thenComparing(ScoredPath::path));
        List<SearchResult> results = new ArrayList<>();
        for (int position = offset; position < Math.min((long) offset + limit, matches.size()); position++) {
            ScoredPath match = matches.get(position);
            String content = index.content(match.path()).orElseThrow();
            results.add(new SearchResult(match.path(), match.score(),
                    snippetBuilder.build(content, match.terms())));
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

    private Set<String> candidates(QueryExpression expression,
                                   Map<String, Map<String, Posting>> byTerm) {
        return switch (expression) {
            case QueryExpression.Clause leaf -> clauseCandidates(leaf.value(), byTerm);
            case QueryExpression.And and -> {
                Set<String> paths = null;
                for (QueryExpression child : and.children()) {
                    if (paths == null) {
                        paths = candidates(child, byTerm);
                    } else {
                        paths.retainAll(candidates(child, byTerm));
                    }
                    if (paths.isEmpty()) {
                        break;
                    }
                }
                yield paths;
            }
            case QueryExpression.Or or -> {
                Set<String> paths = new HashSet<>();
                for (QueryExpression child : or.children()) {
                    paths.addAll(candidates(child, byTerm));
                }
                yield paths;
            }
            case QueryExpression.Not not -> {
                Set<String> paths = new HashSet<>(documents.keySet());
                paths.removeAll(candidates(not.child(), byTerm));
                yield paths;
            }
        };
    }

    private Set<String> clauseCandidates(SearchClause clause,
                                         Map<String, Map<String, Posting>> byTerm) {
        Set<String> paths = null;
        for (String term : clause.terms()) {
            if (paths == null) {
                paths = new HashSet<>(byTerm.get(term).keySet());
            } else {
                paths.retainAll(byTerm.get(term).keySet());
            }
        }
        if (clause instanceof PhraseClause phrase) {
            paths.removeIf(path -> !phraseMatcher.matches(phrase, postingsFor(path,
                    clause.terms(), byTerm)));
        }
        return paths;
    }

    private Optional<Set<String>> matchingTerms(QueryExpression expression, String path,
                                                 Map<String, Map<String, Posting>> byTerm) {
        return switch (expression) {
            case QueryExpression.Clause leaf -> clauseMatches(leaf.value(), path, byTerm)
                    ? Optional.of(Set.copyOf(leaf.value().terms())) : Optional.empty();
            case QueryExpression.And and -> {
                Set<String> terms = new LinkedHashSet<>();
                boolean allMatch = true;
                for (QueryExpression child : and.children()) {
                    Optional<Set<String>> match = matchingTerms(child, path, byTerm);
                    if (match.isEmpty()) {
                        allMatch = false;
                        break;
                    }
                    terms.addAll(match.orElseThrow());
                }
                yield allMatch ? Optional.of(terms) : Optional.empty();
            }
            case QueryExpression.Or or -> {
                Set<String> terms = new LinkedHashSet<>();
                boolean anyMatch = false;
                for (QueryExpression child : or.children()) {
                    Optional<Set<String>> match = matchingTerms(child, path, byTerm);
                    if (match.isPresent()) {
                        anyMatch = true;
                        terms.addAll(match.orElseThrow());
                    }
                }
                yield anyMatch ? Optional.of(terms) : Optional.empty();
            }
            case QueryExpression.Not not -> matchingTerms(not.child(), path, byTerm).isEmpty()
                    ? Optional.of(Set.of()) : Optional.empty();
        };
    }

    private boolean clauseMatches(SearchClause clause, String path,
                                  Map<String, Map<String, Posting>> byTerm) {
        if (clause.terms().stream().anyMatch(term -> !byTerm.get(term).containsKey(path))) {
            return false;
        }
        return !(clause instanceof PhraseClause phrase) || phraseMatcher.matches(phrase,
                postingsFor(path, clause.terms(), byTerm));
    }

    private Map<String, Posting> postingsFor(String path, List<String> terms,
                                             Map<String, Map<String, Posting>> byTerm) {
        Map<String, Posting> postings = new HashMap<>();
        for (String term : terms) {
            postings.put(term, byTerm.get(term).get(path));
        }
        return postings;
    }

    private boolean passesFilters(String path, SearchQuery query) {
        if (query.fileType().isPresent() && !path.toLowerCase(Locale.ROOT)
                .endsWith("." + query.fileType().orElseThrow())) {
            return false;
        }
        return query.pathPrefix().isEmpty() || path.startsWith(query.pathPrefix().orElseThrow());
    }

    private record ScoredPath(String path, double score, Set<String> terms) {
    }
}
