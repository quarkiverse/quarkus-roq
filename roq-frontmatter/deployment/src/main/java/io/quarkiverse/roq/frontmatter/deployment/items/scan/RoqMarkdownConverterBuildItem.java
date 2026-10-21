package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A converter from a page source of one markup to Markdown, contributed by the plugin that owns the markup (the
 * AsciiDoc plugins for {@code asciidoc}, the Markdown plugin for {@code markdown}), the way
 * {@link RoqFrontMatterQuteMarkupBuildItem} contributes the markup itself. A consumer, such as the Markdown twin
 * plugin, picks the converter of a page by {@link #markup()} and never needs to know the markup's parser.
 */
public final class RoqMarkdownConverterBuildItem extends MultiBuildItem {

    private final String markup;
    private final MarkdownConverter converter;

    /**
     * @param markup the markup name, as given to {@link RoqFrontMatterQuteMarkupBuildItem}
     * @param converter the converter of a page source of that markup
     */
    public RoqMarkdownConverterBuildItem(String markup, MarkdownConverter converter) {
        this.markup = markup;
        this.converter = converter;
    }

    public String markup() {
        return markup;
    }

    public MarkdownConverter converter() {
        return converter;
    }

    /**
     * The converter of a markup, or empty when no plugin contributes one.
     */
    public static Optional<MarkdownConverter> find(List<RoqMarkdownConverterBuildItem> converters, String markup) {
        for (RoqMarkdownConverterBuildItem item : converters) {
            if (item.markup.equals(markup)) {
                return Optional.of(item.converter);
            }
        }
        return Optional.empty();
    }

    /**
     * Converts one page source to Markdown.
     */
    @FunctionalInterface
    public interface MarkdownConverter {

        /**
         * @param conversion the page to convert
         * @return the Markdown, or empty when the page cannot be converted; the converter logs why and never throws
         */
        Optional<String> convert(MarkdownConversion conversion);
    }

    /**
     * What a converter gets for one page.
     *
     * @param source the page source file as the scan found it, front matter included; it is on disk for a page of the
     *        site directory, and not for a page that comes from a dependency jar
     * @param generatedTemplate the Qute template Roq generated from the source, the page's content wrapped in the
     *        markup's section and decoded by the scan; what a converter reads when the source is not on disk
     * @param siteDir the site directory, the boundary of what the page may include; null when unknown
     * @param rootUrl the site root URL; null when unknown
     * @param url the page URL; null when unknown
     * @param data the page's front matter data, parents merged in
     * @param crossReferenceSuffix what a reference to another page of the same markup ends with in the Markdown, such
     *        as {@code /index.md} when each page's Markdown is published at {@code <page-url>index.md}; null keeps the
     *        converter's default
     */
    public record MarkdownConversion(Path source, String generatedTemplate, Path siteDir, RootUrl rootUrl, RoqUrl url,
            JsonObject data, String crossReferenceSuffix) {

        /** The page title from its data, or null. */
        public String title() {
            return data == null ? null : data.getString("title");
        }
    }
}
