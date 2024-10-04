package io.quarkiverse.roq.frontmatter.deployment.data;

import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGE_LINK_TEMPLATE;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.roq.frontmatter.deployment.Link;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterDataProcessor {

    public static final String LINK_KEY = "link";
    public static final String PAGINATE_KEY = "paginate";

    @BuildStep
    void prepareData(HttpBuildTimeConfig httpConfig,
            RoqSiteConfig config,
            BuildProducer<RoqFrontMatterRootUrlBuildItem> rootUrlProducer,
            BuildProducer<RoqFrontMatterPublishPageBuildItem> pagesProducer,
            BuildProducer<RoqFrontMatterDocumentTemplateBuildItem> documentTemplatesProducer,
            BuildProducer<RoqFrontMatterPaginateTemplateBuildItem> paginatedPagesProducer,
            List<RoqFrontMatterRawTemplateBuildItem> roqFrontMatterTemplates) {
        if (roqFrontMatterTemplates.isEmpty()) {
            return;
        }
        final var byKey = roqFrontMatterTemplates.stream()
                .collect(Collectors.toMap(RoqFrontMatterRawTemplateBuildItem::id, Function.identity()));
        final RootUrl rootUrl = new RootUrl(config.urlOptional().orElse(""), httpConfig.rootPath);
        rootUrlProducer.produce(new RoqFrontMatterRootUrlBuildItem(rootUrl));

        for (RoqFrontMatterRawTemplateBuildItem item : roqFrontMatterTemplates) {
            if (!item.published()) {
                continue;
            }
            final JsonObject data = mergeParents(item, byKey);
            final String link = Link.pageLink(config.rootPath(), data.getString(LINK_KEY, DEFAULT_PAGE_LINK_TEMPLATE),
                    new Link.PageLinkData(item.info().baseFileName(), item.info().date(), item.collection(), data));
            if (item.collection() != null) {
                documentTemplatesProducer
                        .produce(new RoqFrontMatterDocumentTemplateBuildItem(item, rootUrl.resolve(link), item.collection(),
                                data));
            } else {
                if (data.containsKey(PAGINATE_KEY)) {
                    // Pagination is created needs collections size so it's produced after
                    paginatedPagesProducer.produce(new RoqFrontMatterPaginateTemplateBuildItem(item, null, link, data));
                } else {
                    pagesProducer.produce(new RoqFrontMatterPublishPageBuildItem(link, item.info(), data, null));
                }
            }

        }
    }

    private static JsonObject mergeParents(RoqFrontMatterRawTemplateBuildItem item,
            Map<String, RoqFrontMatterRawTemplateBuildItem> byPath) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = item.layout();
        fms.add(item.data());
        while (parent != null) {
            if (byPath.containsKey(parent)) {
                final RoqFrontMatterRawTemplateBuildItem parentItem = byPath.get(parent);
                parent = parentItem.layout();
                fms.push(parentItem.data());
            } else {
                parent = null;
            }
        }

        JsonObject merged = new JsonObject();
        while (!fms.empty()) {
            merged.mergeIn(fms.pop());
        }
        return merged;
    }
}
