package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public record PageInfo(
        /**
         * The page resolve path (e.g. posts/my-favorite-beer.html)
         */
        String resolvedPath,

        /**
         * If this page is a draft or not
         */
        boolean draft,

        /**
         * Where the images for this page are based (e.g. /static/images)
         */
        String imagesRootPath,

        /**
         * This page zoned date
         */
        String dateString,

        /**
         * The item file content (without the frontmatter header)
         */
        String rawContent,

        /**
         * The path of the source file (e.g posts/my-favorite-beer.md)
         */
        String sourcePath,

        /**
         * The generated template path (e.g posts/my-favorite-beer.html)
         */
        String generatedTemplatePath) {

    public static PageInfo create(String resolvedPath, boolean draft, String imagesRootPath, String dateString,
            String rawContent,
            String sourcePath) {
        return new PageInfo(resolvedPath, draft, imagesRootPath, dateString, rawContent, sourcePath, resolvedPath);
    }

    public PageInfo changeResolvedPath(String resolvedPath) {
        return new PageInfo(resolvedPath, draft(), imagesRootPath(), dateString(), rawContent(), sourcePath(),
                generatedTemplatePath());
    }

    public ZonedDateTime date() {
        return dateString != null ? ZonedDateTime.parse(dateString) : null;
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
    public String baseFileName() {
        return PathUtils.removeExtension(sourceFileName());
    }

    public String getExtension() {
        return PathUtils.getExtension(sourceFileName());
    }

    public boolean isHtml() {
        return "html".equalsIgnoreCase(getExtension());
    }

}
