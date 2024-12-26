package io.quarkiverse.roq.plugin.tagging.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.LINK_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.PAGINATE_KEY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink.PageLinkData;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDocumentTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterPaginateTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.vertx.core.json.JsonObject;

public class RoqPluginTaggingProcessor {

    private static final String FEATURE = "roq-plugin-tagging";
    public static final String DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE = "/:collection/";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void publishTagPages(
            RoqSiteConfig config,
            RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterTemplateBuildItem> templates,
            List<RoqFrontMatterDocumentTemplateBuildItem> documents,
            BuildProducer<RoqFrontMatterPublishDerivedCollectionBuildItem> derivedCollectionProducer,
            BuildProducer<RoqFrontMatterPaginateTemplateBuildItem> paginatedPagesProducer,
            BuildProducer<RoqFrontMatterPublishPageBuildItem> pagesProducer) {

        // Let's find non page templates with the tagging data
        final List<RoqFrontMatterTemplateBuildItem> taggingTemplates = templates.stream()
                .filter(RoqFrontMatterTemplateBuildItem::isLayout)
                .filter(i -> i.data().containsKey("tagging"))
                .toList();

        if (taggingTemplates.isEmpty()) {
            return;
        }

        for (RoqFrontMatterTemplateBuildItem item : taggingTemplates) {

            // Let's find the collection we want tagging from and iterate on the document from this collection
            final Tagging tagging = readTagging(item.raw().id(), item.data());
            final Map<String, List<String>> derived = new HashMap<>();
            for (RoqFrontMatterDocumentTemplateBuildItem document : documents.stream()
                    .filter(d -> d.collection().id().equals(tagging.collection())).toList()) {
                final List<String> tags = resolveTags(document);

                // For all the tags we create a derivation: tag -> document ids
                for (String tag : tags) {
                    derived.computeIfAbsent(tag, k -> new ArrayList<>())
                            .add(document.raw().id());
                }
            }

            // for all the resolved derivation, let's create a page and publish the derived collection
            for (Map.Entry<String, List<String>> e : derived.entrySet()) {
                final String tagCollection = tagging.collection() + "/tag/" + e.getKey();
                final JsonObject data = new JsonObject()
                        .mergeIn(item.data())
                        .put("tag", e.getKey())
                        .put("tagCollection", tagCollection);
                derivedCollectionProducer
                        .produce(new RoqFrontMatterPublishDerivedCollectionBuildItem(tagCollection, e.getValue(), data));

                final String link = TemplateLink.link(config.rootPath(),
                        tagging.link(),
                        DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE,
                        new PageLinkData(item.raw().info(), tagCollection, data),
                        Map.of(":tag", e::getKey));
                final RoqUrl url = rootUrl.rootUrl().resolve(link);

                PageInfo info = item.raw().info().changeId(tagCollection);

                // Dealing with pagination is as simple as those two lines:
                if (data.containsKey(PAGINATE_KEY)) {
                    paginatedPagesProducer
                            .produce(new RoqFrontMatterPaginateTemplateBuildItem(url, info, data, tagCollection));
                } else {

                    pagesProducer.produce(new RoqFrontMatterPublishPageBuildItem(url, info, data, null));
                }
            }
        }
    }

    private static Tagging readTagging(String name, JsonObject data) {
        final Object value = data.getValue("tagging");
        if (value instanceof JsonObject paginate) {
            final String collection = paginate.getString("collection");
            if (collection == null) {
                throw new ConfigurationException("Invalid tagging configuration in " + name);
            }
            return new Tagging(collection, paginate.getString(LINK_KEY, DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE));
        }
        if (value instanceof String collection) {
            return new Tagging(collection, DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE);
        }
        throw new ConfigurationException("Invalid tagging configuration in " + name);
    }

    record Tagging(String collection, String link) {
    }

    private static List<String> resolveTags(RoqFrontMatterDocumentTemplateBuildItem document) {
        return getRawTags(document).stream().map(RoqTemplateExtension::slugify).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> getRawTags(RoqFrontMatterDocumentTemplateBuildItem document) {
        if (!document.data().containsKey("tags")) {
            return List.of();
        }
        final Object tags = document.data().getValue("tags");
        return RoqTemplateExtension.asStrings(tags);
    }

}
