package io.github.natnaelmekuriaw.minisearch.search;

import io.github.natnaelmekuriaw.minisearch.index.Posting;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

final class PhraseMatcher {
    boolean matches(PhraseClause phrase, Map<String, Posting> documentPostings) {
        Objects.requireNonNull(phrase, "phrase");
        Objects.requireNonNull(documentPostings, "documentPostings");
        Posting first = documentPostings.get(phrase.terms().getFirst());
        if (first == null) {
            return false;
        }

        for (int start : first.positions()) {
            boolean matches = true;
            for (int offset = 1; offset < phrase.terms().size(); offset++) {
                Posting next = documentPostings.get(phrase.terms().get(offset));
                if (next == null || Collections.binarySearch(next.positions(), start + offset) < 0) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
