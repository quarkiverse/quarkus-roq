package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public record PageInfo(
        /**
         * The page unique id, it is either the source file name (e.g. _posts/my-favorite-beer.md or a generated id for dynamic
         * pages).
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
         * The item file content (without the frontmatter header)
         */
        String rawContent,

        /**
         * The path of the source file (e.g _posts/my-favorite-beer.md)
         */
        String sourceFilePath,

        /**
         * The generated template path for Qute (e.g posts/my-favorite-beer.html)
         */
        String generatedTemplateId,

        /**
         * List of attached static files (null when public files should be used instead)
         */
        List<String> files,

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
            String rawContent,
            String sourcePath,
            String quteTemplateId,
            List<String> files,
            boolean isHtml,
            boolean isSiteIndex) {
        return new PageInfo(id, draft, dateString, rawContent, sourcePath, quteTemplateId, files,
                isHtml, isSiteIndex);
    }

    public PageInfo changeId(String id) {
        // We don't copy the site index files
        return new PageInfo(id, draft(), dateString(), rawContent(), sourceFilePath(),
                generatedTemplateId(), isSiteIndex() ? null : files(), isHtml(), false);
    }

    public PageInfo changeIds(Function<String, String> function) {
        return new PageInfo(function.apply(id()), draft(), dateString(), rawContent(),
                sourceFilePath(),
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
        return PathUtils.fileName(sourceFilePath);
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

    public boolean hasFile(Object name) {
        if (name == null) {
            return false;
        }
        if (!hasFiles()) {
            return false;
        }
        return files().contains(name);
    }

}
