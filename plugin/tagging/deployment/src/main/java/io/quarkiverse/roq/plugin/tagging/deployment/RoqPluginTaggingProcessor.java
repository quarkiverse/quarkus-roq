package io.quarkiverse.roq.plugin.tagging.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.LINK_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.PAGINATE_KEY;
import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.roq.frontmatter.deployment.Link;
import io.quarkiverse.roq.frontmatter.deployment.Link.PageLinkData;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDocumentTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterPaginateTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqPluginTaggingProcessor {

    private static final String FEATURE = "roq-plugin-tagging";
    public static final String DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE = "/:collection";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void publishTagPages(
            RoqSiteConfig config,
            List<RoqFrontMatterRawTemplateBuildItem> rawTemplates,
            List<RoqFrontMatterDocumentTemplateBuildItem> documents,
            BuildProducer<RoqFrontMatterPublishDerivedCollectionBuildItem> derivedCollectionProducer,
            BuildProducer<RoqFrontMatterPaginateTemplateBuildItem> paginatedPagesProducer,
            BuildProducer<RoqFrontMatterPublishPageBuildItem> pagesProducer) {

        // Let's find non page templates with the tagging data
        final List<RoqFrontMatterRawTemplateBuildItem> taggingTemplates = rawTemplates.stream()
                .filter(not(RoqFrontMatterRawTemplateBuildItem::isPage))
                .filter(i -> i.data().containsKey("tagging"))
                .toList();

        if (taggingTemplates.isEmpty()) {
            return;
        }

        for (RoqFrontMatterRawTemplateBuildItem item : taggingTemplates) {

            // Let's find the collection we want tagging from and iterate on the document from this collection
            final String derivedFromCollection = item.data().getString("tagging");
            final Map<String, List<String>> derived = new HashMap<>();
            for (RoqFrontMatterDocumentTemplateBuildItem document : documents.stream()
                    .filter(d -> d.collection().equals(derivedFromCollection)).toList()) {
                final List<String> tags = resolveTags(document);

                // For all the tags we create a derivation: tag -> document ids
                for (String tag : tags) {
                    derived.computeIfAbsent(tag, k -> new ArrayList<>())
                            .add(document.item().id());
                }
            }

            // for all the resolved derivation, let's create a page and publish the derived collection
            for (Map.Entry<String, List<String>> e : derived.entrySet()) {
                final String tagCollection = derivedFromCollection + "/tag/" + e.getKey();
                final JsonObject data = new JsonObject()
                        .mergeIn(item.data())
                        .put("tag", e.getKey())
                        .put("tagCollection", tagCollection);
                derivedCollectionProducer
                        .produce(new RoqFrontMatterPublishDerivedCollectionBuildItem(tagCollection, e.getValue(), data));

                final String link = Link.pageLink(config.rootPath(),
                        data.getString(LINK_KEY, DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE),
                        new PageLinkData(item.info().baseFileName(), item.info().date(), tagCollection, data));

                // Dealing with pagination is as simple as those two lines:
                if (data.containsKey(PAGINATE_KEY)) {
                    paginatedPagesProducer
                            .produce(new RoqFrontMatterPaginateTemplateBuildItem(item, tagCollection, link, data));
                } else {
                    pagesProducer.produce(new RoqFrontMatterPublishPageBuildItem(link, item.info(), data, null));
                }
            }
        }
    }

    private static List<String> resolveTags(RoqFrontMatterDocumentTemplateBuildItem document) {
        return getRawTags(document).stream().map(Link::slugify).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> getRawTags(RoqFrontMatterDocumentTemplateBuildItem document) {
        if (!document.data().containsKey("tags")) {
            return List.of();
        }
        final Object tags = document.data().getValue("tags");
        if (tags instanceof String) {
            return List.of(((String) tags).split("\\h*,\\h*|\\h{2,}"));
        }
        if (tags instanceof JsonArray) {
            return ((JsonArray) tags).getList();
        }
        return List.of();
    }

}
