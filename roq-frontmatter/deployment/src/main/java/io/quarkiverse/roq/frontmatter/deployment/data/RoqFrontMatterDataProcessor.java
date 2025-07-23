package io.quarkiverse.roq.frontmatter.deployment.data;

import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.Attachment;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterDataProcessor {

    public static final String LINK_KEY = "link";
    public static final String PAGINATE_KEY = "paginate";

    @BuildStep
    void prepareData(VertxHttpBuildTimeConfig httpConfig,
            RoqSiteConfig config,
            BuildProducer<RoqFrontMatterRootUrlBuildItem> rootUrlProducer,
            BuildProducer<RoqFrontMatterTemplateBuildItem> templatesProducer,
            List<RoqFrontMatterRawTemplateBuildItem> roqFrontMatterTemplates) {
        if (roqFrontMatterTemplates.isEmpty()) {
            return;
        }

        final var byId = roqFrontMatterTemplates.stream()
                .filter(i -> !i.type().isPage())
                .collect(Collectors.toMap(RoqFrontMatterRawTemplateBuildItem::id, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Multiple templates found with id '" + a.id() + "', this is a bug.");
                }));
        final RootUrl rootUrl = new RootUrl(config.urlOptional().orElse(""), httpConfig.rootPath());
        rootUrlProducer.produce(new RoqFrontMatterRootUrlBuildItem(rootUrl));
        for (RoqFrontMatterRawTemplateBuildItem item : roqFrontMatterTemplates) {
            JsonObject data = mergeParents(config, item, byId);
            RoqUrl url = null;
            if (item.published()) {
                final String link = TemplateLink.pageLink(config.pathPrefixOrEmpty(),
                        data.getString(LINK_KEY),
                        new TemplateLink.PageLinkData(item.info(), item.collectionId(), data));
                url = rootUrl.resolve(link);
            }
            RoqFrontMatterTemplateBuildItem templateItem = new RoqFrontMatterTemplateBuildItem(item, url,
                    data);
            templatesProducer.produce(templateItem);
        }
    }

    @BuildStep
    void dispatchByType(BuildProducer<RoqFrontMatterPublishPageBuildItem> pagesProducer,
            BuildProducer<RoqFrontMatterDocumentTemplateBuildItem> documentTemplatesProducer,
            BuildProducer<RoqFrontMatterPaginateTemplateBuildItem> paginatedPagesProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFileProducer,
            List<RoqFrontMatterTemplateBuildItem> templates) {
        if (templates.isEmpty()) {
            return;
        }

        for (RoqFrontMatterTemplateBuildItem item : templates) {
            if (!item.published()) {
                continue;
            }
            if (item.raw().attachments() != null) {
                // Publish static assets
                for (Attachment attachment : item.raw().attachments()) {
                    staticFileProducer.produce(new RoqFrontMatterStaticFileBuildItem(
                            item.url().resolve(attachment.name()).resourcePath(), attachment.path()));
                }
            }
            // Publish Pages
            if (item.raw().collection() != null) {
                documentTemplatesProducer
                        .produce(new RoqFrontMatterDocumentTemplateBuildItem(item.raw(), item.url(), item.raw().collection(),
                                item.data()));
            } else {
                if (item.data().containsKey(PAGINATE_KEY)) {
                    // Pagination needs collections size so it's produced after
                    paginatedPagesProducer
                            .produce(new RoqFrontMatterPaginateTemplateBuildItem(item.url(), item.raw().info(), item.data(),
                                    null));
                } else {
                    pagesProducer
                            .produce(new RoqFrontMatterPublishPageBuildItem(item.url(), item.raw().info(), item.data(), null));
                }
            }

        }
    }

    public static JsonObject mergeParents(RoqSiteConfig config, RoqFrontMatterRawTemplateBuildItem item,
            Map<String, RoqFrontMatterRawTemplateBuildItem> byId) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = item.layout();
        fms.add(item.data());
        while (parent != null) {
            if (!byId.containsKey(parent)) {
                final String layoutKey = RoqFrontMatterScanProcessor.getLayoutKey(config.theme(), parent);
                throw new RoqLayoutNotFoundException(
                        "Layout '%s' not found for file '%s'. Available layouts are: %s."
                                .formatted(layoutKey, item.info().sourcePath(), getAvailableLayouts(config, byId)));
            }
            final RoqFrontMatterRawTemplateBuildItem parentItem = byId.get(parent);
            parent = parentItem.layout();
            fms.push(parentItem.data());
        }

        JsonObject merged = new JsonObject();
        while (!fms.empty()) {
            merged.mergeIn(fms.pop());
        }
        return merged;
    }

    private static String getAvailableLayouts(RoqSiteConfig config, Map<String, RoqFrontMatterRawTemplateBuildItem> byId) {
        return byId.entrySet().stream()
                .filter(i -> i.getValue().isLayout())
                .map(i -> RoqFrontMatterScanProcessor.getLayoutKey(config.theme(), i.getKey()))
                .map("'%s'"::formatted).collect(Collectors.joining(", "));
    }
}
