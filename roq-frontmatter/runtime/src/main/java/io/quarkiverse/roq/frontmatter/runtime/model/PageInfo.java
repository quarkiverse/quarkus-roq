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
         * The page unique id, it is either the source file name (e.g. _posts/my-favorite-beer.md or a generated id for dynamic
         * pages).
         */
        String id,

        /**
         * If this page is a draft or not
         */
        boolean draft,

        /**
         * Where all the images for this page are based relative to the site path (e.g. {site-path}/static/images)
         */
        String imagesDirPath,

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
         * Is this a html page or something else (json, yml, ...)
         */
        boolean isHtml) {

    public static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");

    public static PageInfo create(String id, boolean draft, String imagesDirPath, String dateString,
            String rawContent,
            String sourcePath,
            String quteTemplateId,
            boolean isHtml) {
        return new PageInfo(id, draft, imagesDirPath, dateString, rawContent, sourcePath, quteTemplateId, isHtml);
    }

    public PageInfo changeId(String id) {
        return new PageInfo(id, draft(), imagesDirPath(), dateString(), rawContent(), sourceFilePath(),
                generatedTemplateId(), isHtml());
    }

    public PageInfo changeIds(Function<String, String> function) {
        return new PageInfo(function.apply(id()), draft(), imagesDirPath(), dateString(), rawContent(),
                sourceFilePath(),
                function.apply(generatedTemplateId()), isHtml());
    }

    public PageInfo changeIdAndGeneratedTemplateId(String id) {
        return new PageInfo(id, draft(), imagesDirPath(), dateString(), rawContent(), sourceFilePath(),
                generatedTemplateId(), isHtml());
    }

    public ZonedDateTime date() {
        return dateString != null ? ZonedDateTime.parse(dateString) : null;
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

    public boolean isSiteIndex() {
        return isHtml && id().startsWith("index.");
    }

    public boolean isIndex() {
        return isHtml && "index".equals(sourceBaseFileName());
    }

}
