package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.Function;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

/**
 * Represents metadata and content for a template source (page, document or template).
 *
 * TODO: rename to TemplateSource
 * TODO: move all optional fields to another record (draft, dateString, files, isHtml, isSiteIndex)
 *
 * @param id The page unique identifier. It is either the source file's relative path
 *        (e.g. {@code posts/my-post.md}) or a generated source path for dynamic pages.
 * @param draft Whether this page is marked as a draft.
 * @param dateString The date associated with this page, as a string (with time zone).
 * @param markup The markup language used for this page (e.g. {@code markdown}, {@code asciidoc}), or {@code null} if none.
 * @param rawContent The content of the file or template, excluding any frontmatter header.
 *        If applicable, this includes the markup block (e.g. {@code <md>...</md>}).
 * @param sourceFile The source file location on disk or in the classpath.
 * @param path A stable, canonical identifier for the page within the logical content structure
 *        (e.g. {@code posts/my-favorite-beer.md}).
 * @param generatedTemplateId The generated Qute template path for this page
 *        (e.g. {@code roq-gen/posts/my-favorite-beer.html}).
 * @param files List of attached static files, or {@code null} when public files should be used instead.
 * @param isHtml Whether this page is an HTML page (as opposed to JSON, YAML, etc.).
 * @param isSiteIndex Whether this page is the site index (only one such page exists per site).
 */
@TemplateData
@Vetoed
public record PageInfo(
        String id,
        boolean draft,
        String dateString,
        String markup,
        String rawContent,
        SourceFile sourceFile,
        String path,
        String generatedTemplateId,
        PageFiles files,
        boolean isHtml,
        boolean isSiteIndex) {

    public static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");

    public static PageInfo create(String id,
            boolean draft,
            String dateString,
            String markup,
            String rawContent,
            SourceFile sourceFile,
            String path,
            String quteTemplateId,
            PageFiles files,
            boolean isHtml,
            boolean isSiteIndex) {
        return new PageInfo(id, draft, dateString, markup, rawContent, sourceFile, path, quteTemplateId,
                files,
                isHtml, isSiteIndex);
    }

    public PageInfo changeId(String id) {
        // We don't copy the site index files
        return new PageInfo(id, draft(), dateString(), markup(), rawContent(), sourceFile(), path(),
                generatedTemplateId(), isSiteIndex() ? null : files(), isHtml(), false);
    }

    public PageInfo changeIds(Function<String, String> function) {
        return new PageInfo(function.apply(id()), draft(), dateString(), markup(), rawContent(), sourceFile(), path(),
                function.apply(generatedTemplateId()), isSiteIndex() ? null : files(), isHtml(), false);
    }

    public ZonedDateTime date() {
        return dateString != null ? ZonedDateTime.parse(dateString) : null;
    }

    public boolean usePublicFiles() {
        return isSiteIndex || hasNoFiles();
    }

    /**
     * The file name (e.g my-favorite-beer.md)
     */
    public String sourceFileName() {
        return PathUtils.fileName(path());
    }

    /**
     * The file name without the extension (e.g my-favorite-beer)
     */
    public String sourceBaseFileName() {
        return PathUtils.removeExtension(sourceFileName());
    }

    public String sourceFileExtension() {
        return PathUtils.getExtension(sourceFileName());
    }

    public boolean isIndex() {
        return isHtml && "index".equals(sourceBaseFileName());
    }

    public boolean hasFiles() {
        return files != null && !files.isEmpty();
    }

    public boolean hasNoFiles() {
        return files == null || files.isEmpty();
    }

    public boolean fileExists(Object name) {
        if (name == null) {
            return false;
        }
        if (hasNoFiles()) {
            return false;
        }
        return files().contains(name);
    }

}
