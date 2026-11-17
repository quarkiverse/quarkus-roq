package io.quarkiverse.roq.migration.react;

import java.util.ArrayList;
import java.util.List;

/**
 * A JSX tree, and a parser for the JSX in React components: elements, fragments, attributes, text and {@code {...}}
 * expressions. Expressions are kept as source code for the converter to translate.
 */
final class Jsx {

    private Jsx() {
    }

    sealed interface Node permits Element, Text, Expression {
    }

    /**
     * @param name the tag or component name; empty for a fragment ({@code <>...</>})
     */
    record Element(String name, List<Attribute> attributes, List<Node> children) implements Node {

        boolean isFragment() {
            return name.isEmpty();
        }

        boolean isComponent() {
            return !name.isEmpty() && (Character.isUpperCase(name.charAt(0)) || name.contains("."));
        }

        Attribute attribute(String attributeName) {
            return attributes.stream().filter(a -> attributeName.equals(a.name())).findFirst().orElse(null);
        }
    }

    record Text(String text) implements Node {
    }

    /**
     * @param code the JavaScript between the braces
     */
    record Expression(String code) implements Node {
    }

    /**
     * @param name the attribute name; null for a spread ({@code {...props}})
     * @param string the value of {@code name="..."}, or null
     * @param code the expression of {@code name={...}} or of a spread, or null; both are null for a bare attribute
     */
    record Attribute(String name, String string, String code) {

        boolean isSpread() {
            return name == null;
        }
    }

    record Parsed(Element element, int end) {
    }

    static class JsxException extends RuntimeException {
        JsxException(String message) {
            super(message);
        }
    }

    /**
     * @param start the index of the {@code <} that opens the element
     */
    static Parsed parse(String source, int start) {
        Parser parser = new Parser(source, start);
        Element element = parser.element();
        return new Parsed(element, parser.position);
    }

    private static final class Parser {
        final String source;
        int position;

        Parser(String source, int position) {
            this.source = source;
            this.position = position;
        }

        Element element() {
            expect('<');
            skipSpace();
            if (peek() == '>') {
                position++;
                List<Node> children = children("");
                return new Element("", List.of(), children);
            }
            String name = name();
            List<Attribute> attributes = new ArrayList<>();
            while (true) {
                skipSpace();
                if (source.startsWith("/>", position)) {
                    position += 2;
                    return new Element(name, attributes, List.of());
                }
                if (peek() == '>') {
                    position++;
                    return new Element(name, attributes, children(name));
                }
                attributes.add(attribute());
            }
        }

        Attribute attribute() {
            if (peek() == '{') {
                int end = JsSource.matching(source, position);
                String code = source.substring(position + 1, end).strip();
                position = end + 1;
                if (!code.startsWith("...")) {
                    throw new JsxException("unexpected {" + code + "} among the attributes");
                }
                return new Attribute(null, null, code.substring(3).strip());
            }
            String name = name();
            skipSpace();
            if (peek() != '=') {
                return new Attribute(name, null, null);
            }
            position++;
            skipSpace();
            char c = peek();
            if (c == '"' || c == '\'') {
                int end = source.indexOf(c, position + 1);
                if (end < 0) {
                    throw new JsxException("unterminated attribute value");
                }
                String value = source.substring(position + 1, end);
                position = end + 1;
                return new Attribute(name, value, null);
            }
            if (c == '{') {
                int end = JsSource.matching(source, position);
                String code = source.substring(position + 1, end).strip();
                position = end + 1;
                return new Attribute(name, null, code);
            }
            throw new JsxException("unexpected value for the attribute " + name);
        }

        List<Node> children(String parent) {
            List<Node> children = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            while (position < source.length()) {
                if (source.startsWith("</", position)) {
                    flush(text, children);
                    position += 2;
                    skipSpace();
                    String closing = peek() == '>' ? "" : name();
                    skipSpace();
                    expect('>');
                    if (!closing.equals(parent)) {
                        throw new JsxException("</" + closing + "> closes <" + parent + ">");
                    }
                    return children;
                }
                char c = peek();
                if (c == '<') {
                    flush(text, children);
                    children.add(element());
                } else if (c == '{') {
                    flush(text, children);
                    int end = JsSource.matching(source, position);
                    String code = source.substring(position + 1, end).strip();
                    position = end + 1;
                    if (!code.isEmpty()) {
                        children.add(new Expression(code));
                    }
                } else {
                    text.append(c);
                    position++;
                }
            }
            throw new JsxException("<" + parent + "> is not closed");
        }

        /**
         * JSX drops whitespace that contains a line break, and joins the rest of the lines with a space.
         */
        void flush(StringBuilder text, List<Node> children) {
            if (text.isEmpty()) {
                return;
            }
            String raw = text.toString();
            text.setLength(0);
            String[] lines = raw.split("\\R", -1);
            StringBuilder kept = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (i > 0) {
                    line = line.stripLeading();
                }
                if (i < lines.length - 1) {
                    line = line.stripTrailing();
                }
                if (line.isEmpty()) {
                    continue;
                }
                if (!kept.isEmpty()) {
                    kept.append(' ');
                }
                kept.append(line);
            }
            if (!kept.isEmpty()) {
                children.add(new Text(kept.toString()));
            }
        }

        String name() {
            int start = position;
            while (position < source.length()) {
                char c = source.charAt(position);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':' || c == '$') {
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new JsxException("expected a name at '" + snippet() + "'");
            }
            return source.substring(start, position);
        }

        void skipSpace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        char peek() {
            if (position >= source.length()) {
                throw new JsxException("unexpected end of the JSX");
            }
            return source.charAt(position);
        }

        void expect(char c) {
            if (peek() != c) {
                throw new JsxException("expected '" + c + "' at '" + snippet() + "'");
            }
            position++;
        }

        String snippet() {
            return source.substring(position, Math.min(source.length(), position + 20));
        }
    }
}
