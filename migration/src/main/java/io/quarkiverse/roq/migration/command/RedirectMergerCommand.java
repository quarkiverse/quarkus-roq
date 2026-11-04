///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2

package io.quarkiverse.roq.migration.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.migration.FrontMatter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Merges standalone redirect files into their target files as aliases.
 *
 * Jekyll/Roq redirect files (with newUrl/aliases frontmatter) often exist
 * as standalone pages. Before deleting the redirects directory, this command
 * extracts each redirect's source path and target URL, then adds the source
 * path as an alias to the target file.
 *
 * Example:
 * _redirects/guides/old-name/index.md with aliases: /guides/new-name
 * → adds "aliases: /guides/old-name" to content/guides/new-name.adoc
 */
@Command(name = "merge-redirects", mixinStandardHelpOptions = true, version = "1.0", description = "Merge redirect files into target files as aliases")
public class RedirectMergerCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project directory (must contain content/)")
    private Path projectDir;

    private static final String FM_DELIMITER = "---\n";
    // No "---" document start marker: the front matter delimiters are written separately
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();

    public static void main(String... args) {
        int exitCode = new CommandLine(new RedirectMergerCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Path contentDir = projectDir.resolve("content");
        Path redirectsDir = contentDir.resolve("redirects");

        if (!Files.isDirectory(redirectsDir)) {
            System.out.println("No redirects directory found - nothing to merge");
            return 0;
        }

        int mergedCount = 0;

        try (Stream<Path> paths = Files.walk(redirectsDir)) {
            for (Path redirectFile : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md") ||
                            p.getFileName().toString().endsWith(".html"))
                    .toList()) {

                if (mergeRedirect(contentDir, redirectsDir, redirectFile)) {
                    mergedCount++;
                }
            }
        }

        System.out.println("Merged " + mergedCount + " redirect(s) into target files");
        return 0;
    }

    private boolean mergeRedirect(Path contentDir, Path redirectsDir, Path redirectFile) throws IOException {
        String content = Files.readString(redirectFile);

        if (!FrontMatter.isPresent(content)) {
            return false;
        }

        ObjectNode data;
        try {
            data = FrontMatter.read(YAML_MAPPER, content);
        } catch (JsonProcessingException e) {
            System.out.println("  Warning: failed to parse frontmatter in " + redirectFile + ": " + e.getMessage());
            return false;
        }

        // Extract target URL from either aliases: or newUrl:
        String target = null;

        // Try aliases: first (if JekyllFrontMatterCommand already converted it)
        JsonNode aliases = data.get("aliases");
        if (aliases != null && aliases.isArray() && !aliases.isEmpty()) {
            target = aliases.get(0).asText();
        }

        // Fall back to newUrl: (if not yet converted)
        JsonNode newUrl = data.get("newUrl");
        if (target == null && newUrl != null && !newUrl.isNull()) {
            target = newUrl.asText();
        }

        if (target == null || target.isEmpty()) {
            return false;
        }

        // Calculate source path (where this redirect lives)
        Path relPath = redirectsDir.relativize(redirectFile);
        String sourcePath = "/" + relPath.toString()
                .replace('\\', '/')
                .replaceAll("/index\\.(md|html)$", "")
                .replaceAll("\\.(md|html)$", "");

        // Check if there's a content file at the source path that will render there
        String sourceFilePath = relPath.toString()
                .replace('\\', '/')
                .replaceAll("/index\\.(md|html)$", "")
                .replaceAll("\\.(md|html)$", "");
        Path sourceContentFile = findTargetFile(contentDir, sourceFilePath);
        if (sourceContentFile != null && !hasLinkFrontmatter(sourceContentFile)) {
            Files.delete(redirectFile);
            System.out.println("  Deleted: " + sourcePath + " → " + target + " (conflicts with content page)");
            return true;
        }

        // Check if target is external URL (keep these as redirect pages)
        boolean isExternal = target.startsWith("http://") || target.startsWith("https://");

        if (isExternal) {
            System.out.println("  Keep: " + sourcePath + " → " + target + " (external URL)");
            return false;
        }

        // Find target file for internal redirects
        String targetPath = target.replaceFirst("^/", "")
                .replaceAll("/$", "")
                .replaceAll("\\.html$", "");

        Path targetFile = findTargetFile(contentDir, targetPath);
        if (targetFile == null) {
            System.out.println("  Keep: " + sourcePath + " → " + target + " (redirect page, target file not found)");
            return false;
        }

        // Delete if source and target resolve to the same path (circular redirect)
        String normalizedSource = sourcePath.replaceAll("/$", "");
        String normalizedTarget = target.replaceAll("/$", "");
        if (!normalizedTarget.startsWith("/")) {
            normalizedTarget = "/" + normalizedTarget;
        }
        if (normalizedSource.equals(normalizedTarget)) {
            Files.delete(redirectFile);
            System.out.println("  Deleted: " + sourcePath + " → " + target + " (circular redirect)");
            return true;
        }

        // Add source path as alias to target file
        addAliasToFile(targetFile, sourcePath);
        System.out.println("  Merged: " + sourcePath + " → " + target);

        Files.delete(redirectFile);

        return true;
    }

    private Path findTargetFile(Path contentDir, String targetPath) throws IOException {
        Path adocFile = contentDir.resolve(targetPath + ".adoc");
        if (Files.exists(adocFile)) {
            return adocFile;
        }

        Path mdFile = contentDir.resolve(targetPath + ".md");
        if (Files.exists(mdFile)) {
            return mdFile;
        }

        Path htmlFile = contentDir.resolve(targetPath + ".html");
        if (Files.exists(htmlFile)) {
            return htmlFile;
        }

        return null;
    }

    private boolean hasLinkFrontmatter(Path file) throws IOException {
        String content = Files.readString(file);
        if (!FrontMatter.isPresent(content)) {
            return false;
        }
        try {
            return FrontMatter.read(YAML_MAPPER, content).has("link");
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private void addAliasToFile(Path file, String alias) throws IOException {
        String content = Files.readString(file);

        if (!FrontMatter.isPresent(content)) {
            content = FM_DELIMITER + "aliases:\n  - " + alias + "\n" + FM_DELIMITER + content;
            Files.writeString(file, content);
            return;
        }

        ObjectNode data;
        try {
            data = FrontMatter.read(YAML_MAPPER, content);
        } catch (JsonProcessingException e) {
            return;
        }

        String body = FrontMatter.strip(content);

        JsonNode existing = data.get("aliases");
        ArrayNode aliases = data.arrayNode();
        if (existing != null && existing.isArray()) {
            aliases = (ArrayNode) existing;
        } else if (existing != null && existing.isTextual()) {
            // A single alias written as a string
            aliases.add(existing.asText());
        }

        for (JsonNode existingAlias : aliases) {
            if (alias.equals(existingAlias.asText())) {
                return;
            }
        }

        aliases.add(alias);
        data.set("aliases", aliases);

        // Serialize back to YAML frontmatter
        String yaml = YAML_MAPPER.writeValueAsString(data);
        content = FM_DELIMITER + yaml + FM_DELIMITER + body;

        Files.writeString(file, content);
    }
}
