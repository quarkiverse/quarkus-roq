package io.quarkiverse.roq.plugin.tagging.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.LINK_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.PAGINATE_KEY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink.PageLinkData;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDocumentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterLayoutTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterPaginatePageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.roq.plugin.tagging.RoqTaggingTemplateExtension;
import io.quarkiverse.roq.plugin.tagging.RoqTaggingUtils;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
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
    void registerAdditionalBeans(RoqFrontMatterOutputBuildItem roqOutput,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (roqOutput == null) {
            return;
        }
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(RoqTaggingTemplateExtension.class)
                .setUnremovable().build());
    }

    @BuildStep
    void publishTagPages(
            RoqSiteConfig config,
            RoqTaggingConfig taggingConfig,
            RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterLayoutTemplateBuildItem> templates,
            List<RoqFrontMatterDocumentBuildItem> documents,
            BuildProducer<RoqFrontMatterPublishDerivedCollectionBuildItem> derivedCollectionProducer,
            BuildProducer<RoqFrontMatterPaginatePageBuildItem> paginatedPagesProducer,
            BuildProducer<RoqFrontMatterPublishNormalPageBuildItem> pagesProducer) {

        // Let's find non page templates with the tagging data
        final List<RoqFrontMatterLayoutTemplateBuildItem> taggingTemplates = templates.stream()
                // We filter out theme layouts
                .filter(i -> i.raw().isLayout() && i.data().containsKey("tagging"))
                .toList();

        if (taggingTemplates.isEmpty() || documents.isEmpty()) {
            return;
        }
        final ConfiguredCollection collection = Objects
                .requireNonNull(RoqFrontMatterDocumentBuildItem.getCollection(documents));
        for (RoqFrontMatterLayoutTemplateBuildItem item : taggingTemplates) {

            // Let's find the collection we want tagging from and iterate on the document from this collection
            final Tagging tagging = readTagging(item.raw().id(), item.data());
            final Map<String, List<String>> derived = new HashMap<>();

            for (RoqFrontMatterDocumentBuildItem document : documents.stream()
                    .filter(d -> d.collection().id().equals(tagging.collection())).toList()) {
                List<String> tags = resolveTags(document);
                if (taggingConfig.lowercase()) {
                    tags = tags.stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList();
                }

                // For all the tags we create a derivation: tag -> document ids
                for (String tag : tags) {
                    derived.computeIfAbsent(tag, k -> new ArrayList<>())
                            .add(document.template().source().id());
                }
            }

            // for all the resolved derivation, let's create a page and publish the derived collection
            for (Map.Entry<String, List<String>> e : derived.entrySet()) {
                final String tagCollection = tagging.collection() + "/tag/" + e.getKey();
                final JsonObject data = new JsonObject()
                        .mergeIn(item.data())
                        .put("title", "#" + e.getKey())
                        .put("tag", e.getKey())
                        .put("tagCollection", tagCollection);
                final ConfiguredCollection configuredCollection = new ConfiguredCollection(tagCollection, true,
                        collection.hidden(),
                        collection.future(), collection.layout());
                derivedCollectionProducer
                        .produce(new RoqFrontMatterPublishDerivedCollectionBuildItem(configuredCollection, e.getValue(), data));

                final TemplateSource templateSource = item.raw().templateSource()
                        .changeId(tagCollection + "." + item.raw().templateSource().extension());
                final PageSource pageSource = new PageSource(templateSource, false, null, PageFiles.empty(), true);
                final String link = TemplateLink.link(config.pathPrefixOrEmpty(),
                        tagging.link(),
                        DEFAULT_TAGGING_COLLECTION_LINK_TEMPLATE,
                        new PageLinkData(pageSource, tagCollection, data),
                        Map.of(":tag", e::getKey));
                final RoqUrl url = rootUrl.rootUrl().resolve(link);

                // Dealing with pagination is as simple as those two lines:
                if (data.containsKey(PAGINATE_KEY)) {
                    paginatedPagesProducer
                            .produce(new RoqFrontMatterPaginatePageBuildItem(url, pageSource, data, configuredCollection));
                } else {
                    pagesProducer.produce(new RoqFrontMatterPublishNormalPageBuildItem(url, pageSource, data, null));
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

    private static List<String> resolveTags(RoqFrontMatterDocumentBuildItem document) {
        return RoqTaggingUtils.slugifiedTagStrings(document.data());
    }

}
