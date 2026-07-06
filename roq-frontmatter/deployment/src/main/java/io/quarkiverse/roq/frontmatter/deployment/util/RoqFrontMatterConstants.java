package io.quarkiverse.roq.frontmatter.deployment.util;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RoqFrontMatterConstants {

    private RoqFrontMatterConstants() {
    }

    // ── Patterns ────────────────────────────────────────────────────────

    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---(?:\\v|$)", Pattern.DOTALL);

    // Detects a frontmatter-shaped block (--- ... ---) that is not at the very
    // start of the file: the opening --- must be preceded by at least one
    // vertical whitespace (i.e. some content - a comment, a blank line, or any
    // line - comes before it). The caller is responsible for stripping fenced
    // code blocks (AsciiDoc ----, Markdown ``` / ~~~) before applying this so
    // that examples of frontmatter shown inside code blocks are not flagged.
    public static final Pattern MISPLACED_FRONTMATTER_PATTERN = Pattern.compile(
            "\\v+---\\v.*?---(?:\\v|$)", Pattern.DOTALL);

    // An AsciiDoc fenced code block: a line of exactly "----" opens it and the
    // next line of exactly "----" closes it. Everything in between (including
    // any --- frontmatter-looking lines) is code and must be ignored.
    public static final Pattern ASCIIDOC_CODE_BLOCK = Pattern.compile(
            "(?m)^----\\s*$.*?^----\\s*$", Pattern.DOTALL);

    // A Markdown fenced code block: a line starting with ``` (or ~~~) opens it
    // and a line starting with the same fence closes it. The opening fence may
    // be followed by an info string (e.g. ```yaml). We use a backreference to
    // the fence chars to match the close.
    public static final Pattern MARKDOWN_CODE_BLOCK = Pattern.compile(
            "(?m)^(`{3,}|~{3,}).*$.*?^\\1.*$", Pattern.DOTALL);

    public static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})-");

    // ── Directory and path constants ────────────────────────────────────

    public static final String TEMPLATES_DIR = "templates";

    // ── File extensions ─────────────────────────────────────────────────

    public static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");
    public static final Set<String> INDEX_FILES = HTML_OUTPUT_EXTENSIONS.stream().map(e -> "index." + e)
            .collect(Collectors.toSet());
}
