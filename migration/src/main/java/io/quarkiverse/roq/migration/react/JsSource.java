package io.quarkiverse.roq.migration.react;

/**
 * Just enough JavaScript scanning to read React components: comments, strings and template literals, and matching
 * brackets. It is not a parser; callers fall back to reporting what they cannot read.
 */
final class JsSource {

    private JsSource() {
    }

    /**
     * @return the source without comments; strings and template literals are kept, and a {@code //} after a colon
     *         (as in {@code https://}) is not taken as a comment
     */
    static String stripComments(String js) {
        StringBuilder out = new StringBuilder(js.length());
        int i = 0;
        while (i < js.length()) {
            char c = js.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                int end = skipString(js, i);
                out.append(js, i, end);
                i = end;
            } else if (js.startsWith("/*", i)) {
                int end = js.indexOf("*/", i + 2);
                i = end < 0 ? js.length() : end + 2;
            } else if (js.startsWith("//", i) && (i == 0 || js.charAt(i - 1) != ':')) {
                int end = js.indexOf('\n', i);
                i = end < 0 ? js.length() : end;
            } else if (js.startsWith("{/*", i)) {
                // A JSX comment: {/* ... */}
                int end = js.indexOf("*/}", i);
                i = end < 0 ? js.length() : end + 3;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * @param start the index of the opening quote or backtick
     * @return the index just after the closing one
     */
    static int skipString(String js, int start) {
        char quote = js.charAt(start);
        int i = start + 1;
        while (i < js.length()) {
            char c = js.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (quote == '`' && js.startsWith("${", i)) {
                i = matching(js, i + 1) + 1;
            } else {
                i++;
            }
        }
        return js.length();
    }

    /**
     * @param open the index of an opening bracket: {@code (}, {@code [} or {@code {}
     *        @return the index of its closing bracket, or the end of the source
     */
    static int matching(String js, int open) {
        int depth = 0;
        int i = open;
        while (i < js.length()) {
            char c = js.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                i = skipString(js, i);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return js.length();
    }

    /**
     * @return the index of the first occurrence of {@code token} outside strings and brackets, or -1; operators such as
     *         {@code &&} and {@code ?} come before the JSX they apply to, so JSX needs no special handling
     */
    static int indexAtTopLevel(String js, String token) {
        int depth = 0;
        int i = 0;
        while (i < js.length()) {
            char c = js.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                i = skipString(js, i);
                continue;
            }
            if (depth == 0 && js.startsWith(token, i)) {
                return i;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            }
            i++;
        }
        return -1;
    }

    /**
     * @return the expression without the parentheses around all of it
     */
    static String unwrap(String expression) {
        String e = expression.strip();
        while (e.startsWith("(") && matching(e, 0) == e.length() - 1) {
            e = e.substring(1, e.length() - 1).strip();
        }
        return e;
    }
}
