package io.quarkiverse.roq.migration.react;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lays out converted templates on several lines, without changing how they render.
 * <p>
 * Line breaks are only added next to block-level elements, where whitespace does not render, and around what contains
 * them: an element whose children include a block puts each block on its own indented line, and keeps the inline
 * content between them (text, links, images, expressions) together on one line, exactly as it was. Elements and Qute
 * sections that contain a block are laid out like blocks, apart from Gatsby's constrained image wrappers, which are
 * divs shown inline. The content of pre and textarea elements is left as it is.
 */
final class QuteFormatter {

    static final Set<String> BLOCKS = Set.of("address", "article", "aside", "blockquote", "body", "dd", "details",
            "dialog", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4",
            "h5", "h6", "head", "header", "hgroup", "hr", "html", "li", "main", "nav", "ol", "p", "pre", "section",
            "summary", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul");
    static final Set<String> PREFORMATTED = Set.of("pre", "textarea");
    static final Set<String> VOID_ELEMENTS = JsxToQuteConverter.VOID_ELEMENTS;
    static final String INDENT = "  ";
    /**
     * Gatsby's constrained image wrapper, a div that is laid out inline.
     */
    static final Pattern INLINE_BLOCK = Pattern.compile("class=\"[^\"]*\\bgatsby-image-wrapper-constrained\\b");

    /**
     * An HTML tag, a Qute section tag such as {@code {#if x}}, {@code {#else}} or {@code {/if}}, or anything else.
     */
    static final Pattern TOKEN = Pattern.compile("<(/?)([a-zA-Z][\\w-]*)(?:\"[^\"]*\"|'[^']*'|[^'\">])*?(/?)>"
            + "|\\{(#|/)(\\w+)(?:[^{}]|\\{[^{}]*})*?(/?)}");

    private record Node(String open, String name, List<Node> children, String close) {

        boolean isText() {
            return name == null;
        }

        boolean isBlock() {
            if (isText() || INLINE_BLOCK.matcher(open).find()) {
                return false;
            }
            return BLOCKS.contains(name) || children.stream().anyMatch(Node::isBlock);
        }
    }

    private QuteFormatter() {
    }

    /**
     * @return the template laid out on several lines; the template as it was if it cannot be read as nested tags
     */
    static String format(String template) {
        List<Node> nodes;
        try {
            nodes = parse(template);
        } catch (IllegalStateException e) {
            return template;
        }
        StringBuilder out = new StringBuilder();
        printChildren(nodes, "", out);
        return out.toString().stripTrailing();
    }

    private static List<Node> parse(String template) {
        List<List<Node>> stack = new ArrayList<>();
        List<String[]> opened = new ArrayList<>();
        stack.add(new ArrayList<>());
        Matcher token = TOKEN.matcher(template);
        int last = 0;
        while (token.find()) {
            if (token.start() > last) {
                text(stack, template.substring(last, token.start()));
            }
            last = token.end();
            boolean html = token.group(2) != null;
            String name = html ? token.group(2).toLowerCase() : token.group(5);
            boolean closing = html ? token.group(1).equals("/") : token.group(4).equals("/");
            boolean selfClosing = html ? !token.group(3).isEmpty() || VOID_ELEMENTS.contains(name)
                    : !token.group(6).isEmpty() || name.equals("else");
            if (!html && name.equals("else")) {
                // {#else} separates the branches of the {#if} it is in
                text(stack, token.group());
                continue;
            }
            if (closing) {
                if (opened.isEmpty() || !opened.get(opened.size() - 1)[0].equals(name)) {
                    throw new IllegalStateException("unbalanced " + token.group());
                }
                String[] open = opened.remove(opened.size() - 1);
                List<Node> children = stack.remove(stack.size() - 1);
                stack.get(stack.size() - 1).add(new Node(open[1], name, children, token.group()));
            } else if (selfClosing) {
                stack.get(stack.size() - 1).add(new Node(token.group(), name, List.of(), ""));
            } else if (html && PREFORMATTED.contains(name)) {
                // Kept as it is, up to its closing tag
                int end = template.indexOf("</" + name, token.end());
                int close = end < 0 ? -1 : template.indexOf('>', end);
                if (close < 0) {
                    throw new IllegalStateException("unclosed <" + name + ">");
                }
                text(stack, template.substring(token.start(), close + 1));
                // A <pre> is a block, but its content must stay as it is
                List<Node> current = stack.get(stack.size() - 1);
                Node pre = current.remove(current.size() - 1);
                current.add(new Node(pre.open(), name, List.of(), ""));
                token.region(close + 1, template.length());
                last = close + 1;
            } else {
                opened.add(new String[] { name, token.group() });
                stack.add(new ArrayList<>());
            }
        }
        if (last < template.length()) {
            text(stack, template.substring(last));
        }
        if (!opened.isEmpty()) {
            throw new IllegalStateException("unclosed " + opened.get(opened.size() - 1)[1]);
        }
        return stack.get(0);
    }

    private static void text(List<List<Node>> stack, String text) {
        stack.get(stack.size() - 1).add(new Node(text, null, List.of(), ""));
    }

    /**
     * Prints nodes one per line where they are blocks, with the inline content between blocks kept on one line.
     */
    private static void printChildren(List<Node> nodes, String indent, StringBuilder out) {
        StringBuilder inline = new StringBuilder();
        for (Node node : nodes) {
            if (node.isBlock()) {
                flushInline(inline, indent, out);
                printBlock(node, indent, out);
            } else {
                printInline(node, inline);
            }
        }
        flushInline(inline, indent, out);
    }

    private static void printBlock(Node node, String indent, StringBuilder out) {
        if (node.children().stream().noneMatch(Node::isBlock)) {
            // Only inline content: one line
            StringBuilder line = new StringBuilder();
            printInline(node, line);
            out.append(indent).append(line).append('\n');
            return;
        }
        out.append(indent).append(node.open()).append('\n');
        printChildren(node.children(), indent + INDENT, out);
        if (!node.close().isEmpty()) {
            out.append(indent).append(node.close()).append('\n');
        }
    }

    private static void printInline(Node node, StringBuilder out) {
        out.append(node.open());
        for (Node child : node.children()) {
            printInline(child, out);
        }
        out.append(node.close());
    }

    private static void flushInline(StringBuilder inline, String indent, StringBuilder out) {
        String line = inline.toString();
        inline.setLength(0);
        if (line.isBlank()) {
            return;
        }
        if (line.strip().equals("{#else}")) {
            // An {#else} on its own between blocks lines up with its {#if}
            out.append(indent, 0, Math.max(0, indent.length() - INDENT.length())).append(line.strip()).append('\n');
        } else {
            out.append(indent).append(line).append('\n');
        }
    }
}
