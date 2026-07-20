package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.removeTrailingSlash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Prototype for quarkiverse/quarkus-roq#1104: publish a Markdown twin of every content page at {@code <page-url>.md} for AI
 * agents.
 * <p>
 * The twin is produced from the <em>source</em>, at build time, and handed to Roq as a static file
 * ({@link RoqFrontMatterStaticFileBuildItem}), which Roq both serves and writes to the generated site.
 * <ul>
 * <li>Markdown pages: the verbatim Markdown source.</li>
 * <li>AsciiDoc pages: flatten the includes (lossless, asciidoctor-reducer), then convert AsciiDoc &rarr; Markdown
 * (downdoc). Source-to-source, never through HTML.</li>
 * </ul>
 * <p>
 * Prototype limitations (see plan / #1104): filtering is coarse (hidden/derived/pagination pages are not distinguished
 * here); the AsciiDoc path shells out to external tools, so a JVM-native conversion is a follow-on for the real feature.
 */
public class RoqPluginMarkdownTwinProcessor {

    private static final Logger LOG = Logger.getLogger(RoqPluginMarkdownTwinProcessor.class);
    private static final String FEATURE = "roq-plugin-markdown-twin";

    private static final String MARKDOWN_OPEN = "{#markdown}";
    private static final String MARKDOWN_CLOSE = "{/markdown}";

    // Prototype: external converters, overridable so tests/builds can point at explicit binaries.
    private static final String REDUCER = System.getProperty("roq.markdown-twin.reducer", "asciidoctor-reducer");
    private static final String DOWNDOC = System.getProperty("roq.markdown-twin.downdoc", "downdoc");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void generateTwins(List<RoqFrontMatterPageTemplateBuildItem> templates,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        for (RoqFrontMatterPageTemplateBuildItem item : templates) {
            final PageSource source = item.source();
            // Only real content pages that render to HTML; skip the site index.
            if (!source.isTargetHtml() || source.isSiteIndex()) {
                continue;
            }
            // Reuse the existing llms.txt opt-out: `llmstxt: false` suppresses the twin too.
            if (!item.data().getBoolean("llmstxt", true)) {
                continue;
            }
            final String base = removeTrailingSlash(item.url().resourcePath());
            if (base == null || base.isBlank()) {
                continue;
            }

            final String markup = source.markup();
            final String markdown;
            if ("markdown".equals(markup)) {
                markdown = extractMarkdownSource(item.raw().generatedTemplate());
            } else if ("asciidoc".equals(markup)) {
                markdown = convertAsciidoc(source);
            } else {
                // html and anything else: no source to convert in the prototype.
                continue;
            }

            if (markdown == null || markdown.isBlank()) {
                continue;
            }
            staticFiles.produce(new RoqFrontMatterStaticFileBuildItem(base + ".md",
                    markdown.getBytes(StandardCharsets.UTF_8)));
            LOG.infof("Markdown twin: /%s.md (from %s)", base, markup);
        }
    }

    /**
     * Recover the verbatim Markdown body from the generated Qute template, which wraps it as
     * {@code {#markdown} ... {/markdown}} (optionally inside {@code {| ... |}} escape delimiters). Author-written Qute
     * expressions are left as-is (acceptable for the prototype; the primary target is AsciiDoc).
     */
    private static String extractMarkdownSource(String generatedTemplate) {
        if (generatedTemplate == null) {
            return null;
        }
        final int start = generatedTemplate.indexOf(MARKDOWN_OPEN);
        final int end = generatedTemplate.lastIndexOf(MARKDOWN_CLOSE);
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        String body = generatedTemplate.substring(start + MARKDOWN_OPEN.length(), end).strip();
        if (body.startsWith("{|") && body.endsWith("|}")) {
            body = body.substring(2, body.length() - 2).strip();
        }
        return body;
    }

    /**
     * Flatten the AsciiDoc source (resolve includes) then convert it to Markdown, using external tools. Returns
     * {@code null} on any failure so the twin is simply skipped rather than breaking the build.
     */
    private String convertAsciidoc(PageSource source) {
        final String src = source.file().absolutePath();
        final Path srcPath = Path.of(src);
        if (!Files.exists(srcPath)) {
            LOG.warnf("Markdown twin: AsciiDoc source not on disk, skipping: %s", src);
            return null;
        }
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("roq-md-twin");
            final Path flat = tmpDir.resolve("flat.adoc");
            final Path md = tmpDir.resolve("out.md");
            // 1) flatten includes (lossless); run in the source directory so relative includes resolve.
            if (!run(srcPath.toAbsolutePath().getParent(), REDUCER, srcPath.toString(), "-o", flat.toString())) {
                return null;
            }
            // 2) convert flattened AsciiDoc -> Markdown.
            if (!run(tmpDir, DOWNDOC, flat.toString(), "-o", md.toString())) {
                return null;
            }
            return Files.readString(md, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            LOG.warnf(e, "Markdown twin: conversion failed for %s", src);
            return null;
        } finally {
            if (tmpDir != null) {
                deleteQuietly(tmpDir);
            }
        }
    }

    private boolean run(Path workingDir, String... command) throws IOException, InterruptedException {
        final ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        final Process process = pb.start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            LOG.warnf("Markdown twin: command timed out: %s", String.join(" ", command));
            return false;
        }
        if (process.exitValue() != 0) {
            LOG.warnf("Markdown twin: command failed (exit %d): %s%n%s", process.exitValue(), String.join(" ", command),
                    output);
            return false;
        }
        return true;
    }

    private static void deleteQuietly(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
