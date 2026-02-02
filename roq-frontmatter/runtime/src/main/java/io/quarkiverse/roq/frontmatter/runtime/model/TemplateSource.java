package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_GENERATED_CONTENT_QUTE_PREFIX;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_GENERATED_PAGE_QUTE_PREFIX;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_GENERATED_QUTE_PREFIX;

import java.util.function.Function;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

/**
 * Represents a template source (page, document or template).
 *
 * @param id The page unique identifier. It is either the source file's relative path
 *        (e.g. {@code posts/my-post.md}) or a generated source path for dynamic pages.
 * @param markup The markup language used for this page (e.g. {@code html}, {@code markdown}, {@code asciidoc}), or {@code null}
 *        if none.
 * @param file The source file location on disk or in the classpath.
 * @param path A stable, canonical identifier for the page within the logical content structure
 *        (e.g. {@code posts/my-favorite-beer.md}).
 * @param generatedQuteId The generated Qute template path for this page
 *        (e.g. {@code roq-gen/posts/my-favorite-beer.html}).
 * @param isLayout true if this is a layout template
 * @param isTargetHtml true if the output type is html (html, md, adoc -> html)
 * @param isIndex true if this is an index file
 * @param isSiteIndex true if this is the site index (only one per site)
 */
@TemplateData
@Vetoed
public record TemplateSource(
        String id,
        String markup,
        SourceFile file,
        String path,
        String generatedQuteId,
        boolean isLayout,
        boolean isTargetHtml,
        boolean isIndex,
        boolean isSiteIndex) {

    public static TemplateSource create(
            String id,
            String markup,
            SourceFile sourceFile,
            String path,
            String quteTemplateId,
            boolean isLayout,
            boolean isTargetHtml,
            boolean isIndex,
            boolean isSiteIndex) {

        return new TemplateSource(id, markup, sourceFile, path, quteTemplateId, isLayout, isTargetHtml, isIndex,
                isSiteIndex);
    }

    public TemplateSource changeId(String id) {
        // We don't copy the site index files
        return new TemplateSource(id, markup(), file(), path(),
                generatedQuteId(), isLayout(), isTargetHtml(), isIndex(), false);
    }

    public TemplateSource changeIds(Function<String, String> function) {
        return new TemplateSource(function.apply(id()), markup(), file(), path(),
                function.apply(generatedQuteId()), isLayout(), isTargetHtml(), isIndex(), false);
    }

    public String generatedQuteTemplateId() {
        return isLayout() ? ROQ_GENERATED_QUTE_PREFIX + generatedQuteId : ROQ_GENERATED_PAGE_QUTE_PREFIX + generatedQuteId;
    }

    public String generatedQuteContentTemplateId() {
        return ROQ_GENERATED_CONTENT_QUTE_PREFIX + generatedQuteId;
    }

    /**
     * The file name (e.g my-favorite-beer.md)
     */
    public String fileName() {
        return PathUtils.fileName(path());
    }

    /**
     * The file name without the extension (e.g my-favorite-beer)
     */
    public String baseFileName() {
        return PathUtils.removeExtension(fileName());
    }

    public String extension() {
        return PathUtils.getExtension(fileName());
    }

}
