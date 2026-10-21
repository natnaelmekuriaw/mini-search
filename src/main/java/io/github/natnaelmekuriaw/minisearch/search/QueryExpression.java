package io.github.natnaelmekuriaw.minisearch.search;

import java.util.List;
import java.util.Objects;

public sealed interface QueryExpression {
    record Clause(SearchClause value) implements QueryExpression {
        public Clause {
            Objects.requireNonNull(value, "value");
        }
    }

    record And(List<QueryExpression> children) implements QueryExpression {
        public And {
            children = List.copyOf(children);
            if (children.isEmpty()) {
                throw new IllegalArgumentException("AND needs an operand");
            }
        }
    }

    record Or(List<QueryExpression> children) implements QueryExpression {
        public Or {
            children = List.copyOf(children);
            if (children.isEmpty()) {
                throw new IllegalArgumentException("OR needs an operand");
            }
        }
    }

    record Not(QueryExpression child) implements QueryExpression {
        public Not {
            Objects.requireNonNull(child, "child");
        }
    }
}
