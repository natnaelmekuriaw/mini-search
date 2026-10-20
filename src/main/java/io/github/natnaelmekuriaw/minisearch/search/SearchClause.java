package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;

public sealed interface SearchClause permits TermClause, PhraseClause {
    List<String> terms();
}
