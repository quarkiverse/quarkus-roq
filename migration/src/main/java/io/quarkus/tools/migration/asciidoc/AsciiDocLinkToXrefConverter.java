package io.quarkus.tools.migration.asciidoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts AsciiDoc link: macros to xref: for cross-document references.
 *
 * In Jekyll, guides at _guides/foo.adoc used link:bar to reference sibling files.
 * In Roq, guides render with directory-style URLs (/guides/foo/) so link:bar resolves
 * relative to the page URL: /guides/foo/bar → 404.
 *
 * AsciiDoc's xref: cross-reference macro uses relfileprefix=../ and relfilesuffix=/
 * to construct proper relative paths. Roq sets these attributes so xref:bar.adoc
 * resolves to ../bar/ which works correctly at any nesting depth.
 */
public class AsciiDocLinkToXrefConverter {

    // Match link:bare-filename with optional anchor and link text
    // Group 1: filename (lowercase letters, numbers, hyphens - NO slashes or dots)
    // Group 2: #anchor (optional)
    // Group 3: [link text] (optional)
    // Negative lookahead BEFORE capture: don't match if filename is http or https (URL scheme)
    // Positive lookahead AFTER filename: must be followed by # or [ or end of string (no / or .)
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "link:(?!https?:)([a-z][a-z0-9-]+)(?=[#\\[\\s]|$)(#[a-z0-9_-]+)?(\\[[^\\]]*\\])?");

    /**
     * Convert link: to xref: in all .adoc and .asciidoc files under contentDir.
     * Skips URLs (link:http://..., link:https://...) and only converts bare filenames.
     */
    public void convertProject(Path contentDir) throws IOException {
        if (!Files.isDirectory(contentDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(contentDir)
                .onClose(() -> {
                }) // swallow close exceptions
                .filter(p -> {
                    try {
                        return Files.isRegularFile(p);
                    } catch (Exception e) {
                        return false; // skip files we can't access
                    }
                })) {
            paths.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".adoc") || name.endsWith(".asciidoc");
            })
                    .forEach(this::convertFile);
        } catch (Exception e) {
            // Files.walk can throw on permission errors; ignore them
            if (e.getCause() instanceof java.nio.file.AccessDeniedException) {
                System.err.println("  Warning: skipped some files due to permissions");
            } else {
                throw e;
            }
        }
    }

    private void convertFile(Path file) {
        try {
            String content = Files.readString(file);
            String converted = convertLinks(content);

            if (!content.equals(converted)) {
                Files.writeString(file, converted);
                System.out.println("  Converted: " + file);
            }
        } catch (IOException e) {
            System.err.println("  Error converting " + file + ": " + e.getMessage());
        }
    }

    /**
     * Convert link: macros to xref: in the given content.
     *
     * Converts: link:guide-name → xref:guide-name.adoc
     * Converts: link:guide-name#anchor → xref:guide-name.adoc#anchor
     * Converts: link:guide-name[text] → xref:guide-name.adoc[text]
     * Converts: link:guide-name#anchor[text] → xref:guide-name.adoc#anchor[text]
     *
     * Skips: link:https://... (URLs preserved)
     * Skips: link:http://... (URLs preserved)
     */
    String convertLinks(String content) {
        Matcher matcher = LINK_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String filename = matcher.group(1);
            String anchor = matcher.group(2) != null ? matcher.group(2) : "";
            String linkText = matcher.group(3) != null ? matcher.group(3) : "";

            // Convert to xref:filename.adoc#anchor[text]
            String replacement = "xref:" + filename + ".adoc" + anchor + linkText;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
