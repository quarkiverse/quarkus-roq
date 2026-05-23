package io.quarkiverse.roq.frontmatter.deployment.util;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.vertx.core.json.JsonObject;

/**
 * @deprecated Use {@link io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink} instead.
 */
@Deprecated(forRemoval = true)
public class TemplateLink extends io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink {

    /**
     * @deprecated Use {@link io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.LinkData} instead.
     */
    @Deprecated(forRemoval = true)
    public interface LinkData extends io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.LinkData {
    }

    /**
     * @deprecated Use {@link io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.PageLinkData} instead.
     */
    @Deprecated(forRemoval = true)
    public record PageLinkData(PageSource pageSource, String collection, JsonObject data)
            implements
                LinkData {
    }

    /**
     * @deprecated Use {@link io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.PaginateLinkData} instead.
     */
    @Deprecated(forRemoval = true)
    public record PaginateLinkData(PageSource pageSource, String collection, String page, JsonObject data)
            implements
                LinkData {
    }

    public static String pageLink(String basePath, String template, PageLinkData data) {
        return io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.pageLink(basePath, template,
                new io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.PageLinkData(
                        data.pageSource(), data.collection(), data.data()));
    }

    public static String paginateLink(String basePath, String template, PaginateLinkData data) {
        return io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.paginateLink(basePath, template,
                new io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.PaginateLinkData(
                        data.pageSource(), data.collection(), data.page(), data.data()));
    }

    public static String link(String basePath, String template, String defaultTemplate, LinkData data,
            Map<String, Supplier<String>> placeHolders) {
        return io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.link(basePath, template, defaultTemplate,
                data, placeHolders);
    }
}
