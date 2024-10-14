package io.quarkiverse.roq.plugin.tagging.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.LINK_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.PAGINATE_KEY;
import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink.PageLinkData;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDocumentTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterPaginateTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
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
            RoqFrontMatterRootUrlBuildItem rootUrl,
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
                    .filter(d -> d.collection().id().equals(derivedFromCollection)).toList()) {
                final List<String> tags = resolveTags(document);

                // For all the tags we create a derivation: tag -> document ids
                for (String tag : tags) {
                    derived.computeIfAbsent(tag, k -> new ArrayList<>())
                            .add(document.raw().id());
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

                final String link = TemplateLink.pageLink(config.rootPath(),
                        data.getString(LINK_KEY, DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE),
                        new PageLinkData(item.info(), tagCollection, data));
                final RoqUrl url = rootUrl.rootUrl().resolve(link);

                PageInfo info = item.info().changeId(tagCollection);

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
