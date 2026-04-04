package io.quarkiverse.roq.frontmatter.deployment.util;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RoqFrontMatterConstants {

    private RoqFrontMatterConstants() {
    }

    // ── Patterns ────────────────────────────────────────────────────────

    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---(?:\\v|$)", Pattern.DOTALL);
    public static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})-");

    // ── Directory and path constants ────────────────────────────────────

    public static final String TEMPLATES_DIR = "templates";

    // ── File extensions ─────────────────────────────────────────────────

    public static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");
    public static final Set<String> INDEX_FILES = HTML_OUTPUT_EXTENSIONS.stream().map(e -> "index." + e)
            .collect(Collectors.toSet());
}
