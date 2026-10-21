package io.github.natnaelmekuriaw.minisearch.search;

import io.github.natnaelmekuriaw.minisearch.text.Token;
import io.github.natnaelmekuriaw.minisearch.text.Tokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class QueryParser {
    private static final int MAX_EXPRESSION_DEPTH = 64;
    private final Tokenizer tokenizer = new Tokenizer();

    public SearchQuery parse(String input) {
        Objects.requireNonNull(input, "input");
        List<String> tokens = lex(input);
        List<String> expressionTokens = new ArrayList<>();
        String fileType = null;
        String pathPrefix = null;
        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.startsWith("type:")) {
                if (fileType != null) {
                    throw new IllegalArgumentException("Only one type filter is allowed");
                }
                fileType = lower.substring(5);
                if (!fileType.equals("md") && !fileType.equals("txt")) {
                    throw new IllegalArgumentException("File type must be md or txt");
                }
            } else if (lower.startsWith("path:")) {
                if (pathPrefix != null) {
                    throw new IllegalArgumentException("Only one path filter is allowed");
                }
                pathPrefix = token.substring(5).replace('\\', '/');
                if (pathPrefix.isEmpty() || pathPrefix.startsWith("/")) {
                    throw new IllegalArgumentException("Path filter must be a relative prefix");
                }
            } else {
                expressionTokens.add(token);
            }
        }
        QueryExpression expression = expressionTokens.isEmpty()
                ? null : new ExpressionParser(expressionTokens).parse();
        List<SearchClause> clauses = new ArrayList<>();
        if (expression != null) {
            collectClauses(expression, clauses);
        }
        return new SearchQuery(clauses, Optional.ofNullable(fileType),
                Optional.ofNullable(pathPrefix), Optional.ofNullable(expression));
    }

    private List<String> lex(String input) {
        List<String> tokens = new ArrayList<>();
        for (int cursor = 0; cursor < input.length();) {
            char current = input.charAt(cursor);
            if (Character.isWhitespace(current)) {
                cursor++;
            } else if (current == '(' || current == ')') {
                tokens.add(String.valueOf(current));
                cursor++;
            } else if (current == '"') {
                int end = input.indexOf('"', cursor + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed quoted phrase");
                }
                if (end + 1 < input.length() && !Character.isWhitespace(input.charAt(end + 1))
                        && input.charAt(end + 1) != ')' && input.charAt(end + 1) != '(') {
                    throw new IllegalArgumentException("A phrase must be separated by whitespace");
                }
                tokens.add(input.substring(cursor, end + 1));
                cursor = end + 1;
            } else if (current == '-' && (cursor + 1 == input.length()
                    || input.charAt(cursor + 1) == '(' || input.charAt(cursor + 1) == '"'
                    || Character.isWhitespace(input.charAt(cursor + 1)))) {
                tokens.add("-");
                cursor++;
            } else {
                int end = cursor;
                while (end < input.length() && !Character.isWhitespace(input.charAt(end))
                        && input.charAt(end) != '(' && input.charAt(end) != ')') {
                    if (input.charAt(end) == '"') {
                        throw new IllegalArgumentException("Unexpected quote in query term");
                    }
                    end++;
                }
                String token = input.substring(cursor, end);
                if (token.startsWith("-") && token.length() > 1) {
                    tokens.add("-");
                    tokens.add(token.substring(1));
                } else {
                    tokens.add(token);
                }
                cursor = end;
            }
        }
        return tokens;
    }

    private void collectClauses(QueryExpression expression, List<SearchClause> clauses) {
        switch (expression) {
            case QueryExpression.Clause leaf -> clauses.add(leaf.value());
            case QueryExpression.And and -> and.children().forEach(child -> collectClauses(child, clauses));
            case QueryExpression.Or or -> or.children().forEach(child -> collectClauses(child, clauses));
            case QueryExpression.Not not -> collectClauses(not.child(), clauses);
        }
    }

    private final class ExpressionParser {
        private final List<String> tokens;
        private int cursor;
        private int depth;

        private ExpressionParser(List<String> tokens) {
            this.tokens = tokens;
        }

        private QueryExpression parse() {
            QueryExpression expression = parseOr();
            if (cursor != tokens.size()) {
                throw new IllegalArgumentException("Unexpected query token: " + tokens.get(cursor));
            }
            return expression;
        }

        private QueryExpression parseOr() {
            List<QueryExpression> children = new ArrayList<>();
            children.add(parseAnd());
            while (accept("OR")) {
                children.add(parseAnd());
            }
            return children.size() == 1 ? children.getFirst() : new QueryExpression.Or(children);
        }

        private QueryExpression parseAnd() {
            List<QueryExpression> children = new ArrayList<>();
            children.add(parseUnary());
            while (cursor < tokens.size() && !peek(")") && !peek("OR")) {
                accept("AND");
                children.add(parseUnary());
            }
            return children.size() == 1 ? children.getFirst() : new QueryExpression.And(children);
        }

        private QueryExpression parseUnary() {
            if (++depth > MAX_EXPRESSION_DEPTH) {
                throw new IllegalArgumentException("Query nesting is too deep");
            }
            try {
                return parseUnaryValue();
            } finally {
                depth--;
            }
        }

        private QueryExpression parseUnaryValue() {
            if (accept("-") || accept("NOT")) {
                return new QueryExpression.Not(parseUnary());
            }
            if (accept("(")) {
                QueryExpression expression = parseOr();
                if (!accept(")")) {
                    throw new IllegalArgumentException("Unclosed query group");
                }
                return expression;
            }
            if (cursor >= tokens.size() || peek(")") || peek("OR") || peek("AND")) {
                throw new IllegalArgumentException("Missing query operand");
            }
            String token = tokens.get(cursor++);
            if (token.startsWith("\"")) {
                List<String> terms = tokenizer.tokenize(token.substring(1, token.length() - 1))
                        .stream().map(Token::term).toList();
                if (terms.isEmpty()) {
                    throw new IllegalArgumentException("Quoted phrase has no searchable terms");
                }
                return new QueryExpression.Clause(new PhraseClause(terms));
            }
            if (token.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Unknown query filter: " + token);
            }
            List<QueryExpression> terms = tokenizer.tokenize(token).stream()
                    .map(Token::term).map(TermClause::new)
                    .map(QueryExpression.Clause::new).map(QueryExpression.class::cast).toList();
            if (terms.isEmpty()) {
                throw new IllegalArgumentException("Query term has no searchable text");
            }
            return terms.size() == 1 ? terms.getFirst() : new QueryExpression.And(terms);
        }

        private boolean peek(String value) {
            return cursor < tokens.size() && tokens.get(cursor).equalsIgnoreCase(value);
        }

        private boolean accept(String value) {
            if (!peek(value)) {
                return false;
            }
            cursor++;
            return true;
        }
    }
}
