package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;

import io.quarkiverse.roq.util.PathUtils;

public record PageInfo(
        /**
         * The page id (e.g posts/my-favorite-beer)
         */
        String id,

        /**
         * If this page is a draft or not
         */
        boolean draft,

        String imagesPath,

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

    public PageInfo changeId(String id) {
        return new PageInfo(id, draft(), imagesPath(), dateString(), rawContent(), sourcePath(),
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

}
