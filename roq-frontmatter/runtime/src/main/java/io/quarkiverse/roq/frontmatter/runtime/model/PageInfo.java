package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.Function;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public record PageInfo(
        /**
         * The page unique identifier, it is either the source file relative path (e.g. posts/my-post.md or a generated source
         * path for dynamic pages).
         */
        String id,

        /**
         * If this page is a draft or not
         */
        boolean draft,

        /**
         * This page zoned date
         */
        String dateString,

        /**
         * The markup (markdown, asciidoc) or null if none
         */
        String markup,

        /**
         * The content of the file template, excluding the frontmatter header.
         * If applicable, this also includes the markup section (e.g "<md>...</md>").
         */
        String rawContent,

        /**
         * The path of the source file on disk or in the classpath
         */
        String sourceFile,

        /**
         * The path of the source relative to the content directory (e.g posts/my-favorite-beer.md)
         */
        String sourcePath,

        /**
         * The generated template path for Qute (e.g roq-gen/posts/my-favorite-beer.html)
         */
        String generatedTemplateId,

        /**
         * List of attached static files (null when public files should be used instead)
         */
        PageFiles files,

        /**
         * Is this a html page or something else (json, yml, ...)
         */
        boolean isHtml,
        boolean isSiteIndex) {

    public static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");

    public static PageInfo create(String id,
            boolean draft,
            String dateString,
            String markup,
            String rawContent,
            String absoluteSourceFilePath,
            String sourcePath,
            String quteTemplateId,
            PageFiles files,
            boolean isHtml,
            boolean isSiteIndex) {
        return new PageInfo(id, draft, dateString, markup, rawContent, absoluteSourceFilePath, sourcePath, quteTemplateId,
                files,
                isHtml, isSiteIndex);
    }

    public PageInfo changeId(String id) {
        // We don't copy the site index files
        return new PageInfo(id, draft(), dateString(), markup(), rawContent(), sourceFile(), sourcePath(),
                generatedTemplateId(), isSiteIndex() ? null : files(), isHtml(), false);
    }

    public PageInfo changeIds(Function<String, String> function) {
        return new PageInfo(function.apply(id()), draft(), dateString(), markup(), rawContent(),
                sourceFile(),
                sourcePath(),
                function.apply(generatedTemplateId()), isSiteIndex() ? null : files(), isHtml(), false);
    }

    public ZonedDateTime date() {
        return dateString != null ? ZonedDateTime.parse(dateString) : null;
    }

    public boolean usePublicFiles() {
        return isSiteIndex || files() == null;
    }

    /**
     * The file name (e.g my-favorite-beer.md)
     */
    public String sourceFileName() {
        return PathUtils.fileName(sourcePath);
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
        return !files.isEmpty();
    }

    public boolean hasNoFiles() {
        return files.isEmpty();
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
