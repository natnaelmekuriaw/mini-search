package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;

public sealed interface SearchClause permits TermClause, PhraseClause, PrefixClause {
    List<String> terms();
}
