package io.quarkiverse.roq.migration.react;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fields a GraphQL query selects, e.g. {@code site { siteMetadata { title } }}, read just enough to tell what a
 * static query uses. Aliases, fragments and directives are not read.
 *
 * @param fields the selected fields, by name
 * @param arguments the arguments of the field this is the selection of, such as a filter or a sort, without their
 *        parentheses; null if it has none
 */
record GraphQlSelection(Map<String, GraphQlSelection> fields, String arguments) {

    static final GraphQlSelection LEAF = new GraphQlSelection(Map.of(), null);

    static class InvalidQuery extends Exception {
        InvalidQuery(String message) {
            super(message);
        }
    }

    /**
     * @param query a query, such as {@code query Name { ... }}, or a bare {@code { ... }}
     */
    static final Pattern ENTRY = Pattern.compile("\\s*(\\w+)\\s*:");

    static GraphQlSelection parse(String query) throws InvalidQuery {
        int open = query.indexOf('{');
        if (open < 0) {
            throw new InvalidQuery("no selection");
        }
        Reader reader = new Reader(query, open);
        GraphQlSelection selection = reader.selection(null);
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
     * @return true if this field, or one it selects, has arguments
     */
    boolean hasArguments() {
        return arguments != null || fields.values().stream().anyMatch(GraphQlSelection::hasArguments);
    }

    /**
     * Reads arguments that only test fields for equality with strings, such as Gatsby's
     * {@code fields: {slug: {eq: "home"}}}.
     *
     * @return the fields tested, by path (e.g. {@code fields.slug}), and the values they must equal; null if the
     *         arguments do anything else
     */
    static Map<String, String> equalities(String arguments) {
        Map<String, String> equalities = new LinkedHashMap<>();
        return arguments != null && equalities(arguments.strip(), "", equalities) ? equalities : null;
    }

    private static boolean equalities(String object, String path, Map<String, String> equalities) {
        String rest = object;
        while (!rest.isBlank()) {
            Matcher entry = ENTRY.matcher(rest);
            if (!entry.lookingAt()) {
                return false;
            }
            String key = entry.group(1);
            String value = rest.substring(entry.end()).strip();
            int end;
            if (value.startsWith("{")) {
                end = JsSource.matching(value, 0);
                if (end >= value.length() || key.equals("eq")
                        || !equalities(value.substring(1, end), path.isEmpty() ? key : path + "." + key, equalities)) {
                    return false;
                }
            } else if (key.equals("eq") && value.startsWith("\"") && !path.isEmpty()) {
                end = value.indexOf('"', 1);
                if (end < 0) {
                    return false;
                }
                equalities.put(path, value.substring(1, end));
            } else {
                return false;
            }
            rest = value.substring(end + 1).replaceFirst("^\\s*,", "");
        }
        return !equalities.isEmpty();
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

        GraphQlSelection selection(String arguments) throws InvalidQuery {
            position++; // {
            Map<String, GraphQlSelection> fields = new LinkedHashMap<>();
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
                String fieldArguments = null;
                if (position < query.length() && query.charAt(position) == '(') {
                    int end = JsSource.matching(query, position);
                    if (end >= query.length()) {
                        throw new InvalidQuery("unclosed (");
                    }
                    fieldArguments = query.substring(position + 1, end).strip();
                    position = end + 1;
                    skipSpace();
                }
                GraphQlSelection field = fieldArguments == null ? LEAF : new GraphQlSelection(Map.of(), fieldArguments);
                if (position < query.length() && query.charAt(position) == '{') {
                    field = selection(fieldArguments);
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
