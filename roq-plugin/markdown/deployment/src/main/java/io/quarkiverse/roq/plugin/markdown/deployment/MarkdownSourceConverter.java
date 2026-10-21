package io.quarkiverse.roq.plugin.markdown.deployment;

import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem.MarkdownConversion;

/**
 * The Markdown of a Markdown page is its source, taken from the Qute template Roq generated from it, which this plugin
 * wrapped in its {@code {#markdown}} section: the front matter is already gone, the title of the front matter becomes
 * the heading when the source has none, and the Qute {@code {|...|}} escape delimiters go, as the rendered page does not
 * show them either. Other Qute expressions the author wrote stay as written. A source saved with CRLF line endings gives
 * Markdown with line feeds, which ends with one line feed. Reading the generated template rather than the file keeps
 * the pages that come from a dependency jar, which have no file on disk, and the charset the scan decoded them with.
 * Contributed to the build as the {@link RoqMarkdownConverterBuildItem} of the {@code markdown} markup.
 */
public final class MarkdownSourceConverter implements RoqMarkdownConverterBuildItem.MarkdownConverter {

    private static final Logger LOG = Logger.getLogger(MarkdownSourceConverter.class);

    // the Qute escape delimiters an author writes in a page, which Qute removes when it renders the page
    private static final String ESCAPE_OPEN = "{|";
    private static final String ESCAPE_CLOSE = "|}";
    // the delimiters RoqFrontMatterTemplateUtils wraps the body of a page that sets `escape: true` in; Qute shows
    // everything between them as written, the author's own {| ... |} included
    private static final String PAGE_ESCAPE_OPEN = "{||||||";
    private static final String PAGE_ESCAPE_CLOSE = "||||||}";

    private final QuteMarkupSection section;

    /**
     * @param section the Qute section this plugin wraps a Markdown page in, as it registers the markup
     */
    public MarkdownSourceConverter(QuteMarkupSection section) {
        this.section = section;
    }

    @Override
    public Optional<String> convert(MarkdownConversion conversion) {
        final String body = extractMarkdownSource(conversion.generatedTemplate());
        if (body == null) {
            LOG.warnf("Markdown source: the generated template of %s has no %s section (a page made by a plugin),"
                    + " skipping", conversion.source(), section.open());
            return Optional.empty();
        }
        return Optional.of(withTitle(body, conversion.title()));
    }

    /**
     * Recover the Markdown body from the generated Qute template, which wraps it in the markup's section ({@code
     * {#markdown}}, a line feed, the body, a line feed, {@code {/markdown}}), the body itself inside {@code {|||||| ...
     * ||||||}}
     * escape delimiters when the page sets {@code escape: true}. Only those wrapper characters go, so the body keeps
     * its own indentation; it ends with one line feed, as an AsciiDoc twin does. In a page that is not escaped, the
     * author's own {@code {| ... |}} delimiters are removed, as Qute removes them from the page; in an escaped page
     * they stay, as Qute shows them; other Qute expressions the author wrote stay as
     * written, since a Markdown page is a Qute template by default. A source saved with CRLF line endings gets a twin
     * with line feeds, as an AsciiDoc twin has. Null when the template has no such section.
     */
    String extractMarkdownSource(String generatedTemplate) {
        if (generatedTemplate == null || section == null) {
            return null;
        }
        final String template = generatedTemplate.replace("\r\n", "\n");
        final int start = template.indexOf(section.open());
        final int end = template.lastIndexOf(section.close());
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        final String inner = template.substring(start + section.open().length(), end);
        final String wrapped = unwrap(inner, "\n", "\n");
        final String escaped = unwrap(wrapped, PAGE_ESCAPE_OPEN, PAGE_ESCAPE_CLOSE);
        // An escaped page keeps its body as written; only a page that is not escaped is a
        // Qute template whose escape delimiters Qute removes.
        final String unescaped = escaped.length() < wrapped.length() ? escaped : unescape(wrapped);
        return unescaped.endsWith("\n") ? unescaped : unescaped + "\n";
    }

    /** The text between the prefix and the suffix, or the text itself when it does not carry both. */
    private String unwrap(String text, String prefix, String suffix) {
        if (text.startsWith(prefix) && text.endsWith(suffix) && text.length() >= prefix.length() + suffix.length()) {
            return text.substring(prefix.length(), text.length() - suffix.length());
        }
        return text;
    }

    /** Removes each {@code {| ... |}} pair, keeping its content; an unclosed {@code {|} stays as written. */
    String unescape(String body) {
        if (!body.contains(ESCAPE_OPEN)) {
            return body;
        }
        final StringBuilder result = new StringBuilder(body.length());
        int from = 0;
        while (true) {
            final int open = body.indexOf(ESCAPE_OPEN, from);
            final int close = open < 0 ? -1 : body.indexOf(ESCAPE_CLOSE, open + ESCAPE_OPEN.length());
            if (close < 0) {
                result.append(body, from, body.length());
                return result.toString();
            }
            result.append(body, from, open).append(body, open + ESCAPE_OPEN.length(), close);
            from = close + ESCAPE_CLOSE.length();
        }
    }

    /**
     * A Markdown page keeps its title in the front matter, which the Markdown drops, so the Markdown opens with the
     * title as a level-one heading unless the source already has one, written as {@code # Title} or underlined with
     * {@code =}.
     */
    String withTitle(String markdown, String title) {
        if (markdown == null || title == null || title.isBlank() || hasLevelOneHeading(markdown)) {
            return markdown;
        }
        int body = 0;
        while (body < markdown.length() && markdown.charAt(body) == '\n') {
            body++;
        }
        return "# " + title.strip() + "\n\n" + markdown.substring(body);
    }

    /**
     * Whether the Markdown has a level-one heading, as CommonMark reads one: {@code # Title} or a paragraph line
     * underlined with {@code =}, indented by at most three spaces. A line inside a fenced code block is code, not a
     * heading, so a {@code # comment} in a shell or YAML block does not give the page its title; nor is a line indented
     * by four spaces or a tab, which is an indented code block, and a {@code =} line after a list item or a code line
     * underlines nothing.
     */
    private boolean hasLevelOneHeading(String markdown) {
        String previous = "";
        String fence = null;
        for (String line : markdown.split("\n")) {
            final String marker = fenceMarker(line);
            if (fence != null) {
                if (marker != null && marker.charAt(0) == fence.charAt(0) && marker.length() >= fence.length()
                        && line.strip().equals(marker)) {
                    fence = null;
                }
                previous = "";
                continue;
            }
            if (marker != null) {
                fence = marker;
                previous = "";
                continue;
            }
            if (indentation(line) < 4) {
                final String text = line.strip();
                if (text.equals("#") || text.startsWith("# ") || text.startsWith("#\t")
                        || (isParagraphLine(previous) && !text.isEmpty() && text.chars().allMatch(c -> c == '='))) {
                    return true;
                }
            }
            previous = line;
        }
        return false;
    }

    /**
     * The run of three or more backticks or tildes, indented by at most three spaces, that opens or closes a fence. A
     * backtick run followed by text holding a backtick is inline code, not a fence.
     */
    private String fenceMarker(String line) {
        final int start = indentation(line);
        if (start > 3 || start >= line.length() || (line.charAt(start) != '`' && line.charAt(start) != '~')) {
            return null;
        }
        int end = start;
        while (end < line.length() && line.charAt(end) == line.charAt(start)) {
            end++;
        }
        if (end - start < 3 || (line.charAt(start) == '`' && line.indexOf('`', end) >= 0)) {
            return null;
        }
        return line.substring(start, end);
    }

    /**
     * The indentation in columns, a tab reaching the next multiple of four as CommonMark counts it, so that a
     * tab-indented line is an indented code block.
     */
    private int indentation(String line) {
        int columns = 0;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == ' ') {
                columns++;
            } else if (c == '\t') {
                columns += 4 - columns % 4;
            } else {
                break;
            }
        }
        return columns;
    }

    /**
     * Whether a line can be the paragraph a {@code =} line underlines: text, not blank, not indented code, not a
     * heading, not a list item ({@code -}, {@code *}, {@code +} or a number followed by {@code .} or {@code )}).
     */
    private boolean isParagraphLine(String line) {
        if (line.isBlank() || indentation(line) >= 4) {
            return false;
        }
        final String text = line.strip();
        if (text.startsWith("#") || text.startsWith(">")) {
            return false;
        }
        if ((text.startsWith("-") || text.startsWith("*") || text.startsWith("+"))
                && (text.length() == 1 || text.charAt(1) == ' ' || text.charAt(1) == '\t')) {
            return false;
        }
        int digits = 0;
        while (digits < text.length() && Character.isDigit(text.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits < text.length() && (text.charAt(digits) == '.' || text.charAt(digits) == ')')
                && (digits + 1 == text.length() || text.charAt(digits + 1) == ' ')) {
            return false;
        }
        return true;
    }
}
