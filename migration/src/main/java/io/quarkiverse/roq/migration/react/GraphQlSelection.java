package io.quarkiverse.roq.migration.react;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fields a GraphQL query selects, e.g. {@code site { siteMetadata { title } }}, read just enough to tell what a
 * static query uses. Aliases, fragments and directives are not read.
 *
 * @param fields the selected fields, by name
 * @param arguments true if any selected field has arguments, such as a filter or a sort
 */
record GraphQlSelection(Map<String, GraphQlSelection> fields, boolean arguments) {

    static final GraphQlSelection LEAF = new GraphQlSelection(Map.of(), false);

    static class InvalidQuery extends Exception {
        InvalidQuery(String message) {
            super(message);
        }
    }

    /**
     * @param query a query, such as {@code query Name { ... }}, or a bare {@code { ... }}
     */
    static GraphQlSelection parse(String query) throws InvalidQuery {
        int open = query.indexOf('{');
        if (open < 0) {
            throw new InvalidQuery("no selection");
        }
        Reader reader = new Reader(query, open);
        GraphQlSelection selection = reader.selection();
        reader.skipSpace();
        if (reader.position < query.length()) {
            throw new InvalidQuery("unexpected " + query.substring(reader.position).strip());
        }
        return selection;
    }

    boolean isLeaf() {
        return fields.isEmpty();
    }

    /**
     * @return the selection under a field; null if it is not selected
     */
    GraphQlSelection get(String field) {
        return fields.get(field);
    }

    private static final class Reader {
        final String query;
        int position;

        Reader(String query, int position) {
            this.query = query;
            this.position = position;
        }

        GraphQlSelection selection() throws InvalidQuery {
            position++; // {
            Map<String, GraphQlSelection> fields = new LinkedHashMap<>();
            boolean arguments = false;
            while (true) {
                skipSpace();
                if (position >= query.length()) {
                    throw new InvalidQuery("unclosed {");
                }
                char c = query.charAt(position);
                if (c == '}') {
                    position++;
                    return new GraphQlSelection(fields, arguments);
                }
                if (!Character.isJavaIdentifierStart(c)) {
                    throw new InvalidQuery("unexpected '" + c + "'");
                }
                String name = name();
                skipSpace();
                if (position < query.length() && query.charAt(position) == ':') {
                    throw new InvalidQuery("aliases are not read");
                }
                if (position < query.length() && query.charAt(position) == '(') {
                    int end = JsSource.matching(query, position);
                    if (end >= query.length()) {
                        throw new InvalidQuery("unclosed (");
                    }
                    position = end + 1;
                    arguments = true;
                    skipSpace();
                }
                GraphQlSelection field = LEAF;
                if (position < query.length() && query.charAt(position) == '{') {
                    field = selection();
                    arguments |= field.arguments();
                }
                fields.put(name, field);
            }
        }

        String name() {
            int start = position;
            while (position < query.length() && Character.isJavaIdentifierPart(query.charAt(position))) {
                position++;
            }
            return query.substring(start, position);
        }

        void skipSpace() {
            while (position < query.length()
                    && (Character.isWhitespace(query.charAt(position)) || query.charAt(position) == ',')) {
                position++;
            }
        }
    }
}
