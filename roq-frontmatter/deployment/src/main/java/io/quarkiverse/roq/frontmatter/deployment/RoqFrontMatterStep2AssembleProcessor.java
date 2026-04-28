package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.processTemplate;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils.getIncludeFilter;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.tools.stringpaths.StringPaths.slugify;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.exception.DataConversionException;
import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterAvailableLayoutsBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedContentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.ProcessedTemplate;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterStep2AssembleProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterStep2AssembleProcessor.class);

    // ── Available layouts index ─────────────────────────────────────────────

    @BuildStep
    void buildAvailableLayouts(List<RoqFrontMatterScannedLayoutBuildItem> scannedLayouts,
            BuildProducer<RoqFrontMatterAvailableLayoutsBuildItem> producer) {
        Map<String, SourceFile> layoutsById = new LinkedHashMap<>();
        for (RoqFrontMatterScannedLayoutBuildItem scanned : scannedLayouts) {
            String id = scanned.metadata().templateId();
            SourceFile existing = layoutsById.put(id, scanned.metadata().sourceFile());
            if (existing != null) {
                throw new IllegalStateException(
                        """

                                Multiple layouts found with id '%s'.
                                 - '%s'
                                 - '%s'
                                This usually happens when more than one 'layouts' directory provides a template with the same id. Please ensure layout IDs are unique across all themes and sources.
                                """
                                .formatted(id, existing.absolutePath(),
                                        scanned.metadata().sourceFile().absolutePath()));
            }
        }
        producer.produce(new RoqFrontMatterAvailableLayoutsBuildItem(layoutsById));
    }

    // ── Content processing ───────────────────────────────────────────────

    @BuildStep
    void processContent(RoqSiteConfig config,
            RoqFrontMatterAvailableLayoutsBuildItem availableLayouts,
            List<RoqFrontMatterScannedContentBuildItem> scannedContent,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPageProducer) {
        if (scannedContent.isEmpty()) {
            return;
        }

        // Data modifications (e.g. escape rules, draft marking) are applied during processing
        final List<RoqFrontMatterDataModificationBuildItem> sortedModifications = dataModifications.stream()
                .sorted(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order))
                .toList();

        // processTemplate resolves layout, applies data modifications, and generates
        // the Qute template (with layout include and markup wrapping)
        for (RoqFrontMatterScannedContentBuildItem scanned : scannedContent) {
            ProcessedTemplate processed = processTemplate(
                    scanned.metadata(), true, false, scanned.collection(),
                    scanned.isIndex(), scanned.isSiteIndex(),
                    config, availableLayouts, sortedModifications);

            LOGGER.debugf("Roq content processing producing raw page '%s'", processed.id());
            rawPageProducer.produce(new RoqFrontMatterRawPageBuildItem(
                    processed.templateSource(), processed.layout(), processed.data(),
                    scanned.collection(), processed.generatedTemplate(),
                    scanned.attachments()));
        }
    }

    // ── Layout processing ────────────────────────────────────────────────

    @BuildStep
    void processLayouts(RoqSiteConfig config,
            RoqFrontMatterAvailableLayoutsBuildItem availableLayouts,
            List<RoqFrontMatterScannedLayoutBuildItem> scannedLayouts,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<RoqFrontMatterRawLayoutBuildItem> rawLayoutProducer) {
        if (scannedLayouts.isEmpty()) {
            return;
        }

        final List<RoqFrontMatterDataModificationBuildItem> sortedModifications = dataModifications.stream()
                .sorted(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order))
                .toList();

        for (RoqFrontMatterScannedLayoutBuildItem scanned : scannedLayouts) {
            boolean isThemeLayout = scanned.isThemeLayout();
            ProcessedTemplate processed = processTemplate(
                    scanned.metadata(), false, isThemeLayout, null,
                    false, false,
                    config, availableLayouts, sortedModifications);

            LOGGER.debugf("Roq layout processing producing raw layout '%s'", processed.id());
            rawLayoutProducer.produce(new RoqFrontMatterRawLayoutBuildItem(
                    processed.templateSource(), processed.layout(), processed.data(),
                    processed.generatedTemplate(), isThemeLayout));
        }
    }

    // ── Roq Data processing ───────────────────────────────────────────────

    @BuildStep
    void processDataContent(RoqProjectBuildItem roqProject,
            RoqSiteConfig siteConfig,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            List<DataMappingBuildItem> roqDataBeanBuildItems,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPageProducer) {
        //Process roq-data may they have been mapped or not
        roqDataBeanBuildItems
                .forEach(item -> generatePages(roqProject, siteConfig, item.getName(), convert(item), rawPageProducer));
        roqDataJsonBuildItems
                .forEach(item -> generatePages(roqProject, siteConfig, item.getName(), item.getData(), rawPageProducer));

    }

    private void generatePages(RoqProjectBuildItem roqProject, RoqSiteConfig siteConfig, String itemName, Object item,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPageProducer) {
        var collectionConfig = siteConfig.collections().stream()
                .collect(Collectors.toMap(ConfiguredCollection::id, Function.identity())).get(itemName);
        if (collectionConfig != null && collectionConfig.generate()) {
            switch (item) {
                case JsonObject jsonObject ->
                    generateDataPages(collectionConfig, jsonObject, rawPageProducer, roqProject);
                case JsonArray jsonArray ->
                    jsonArray.forEach(jsonObject -> generateDataPages(collectionConfig, (JsonObject) jsonObject,
                            rawPageProducer, roqProject));
                default -> throw new IllegalStateException();
            }
        }
    }

    private void generateDataPages(ConfiguredCollection configuredCollection, JsonObject item,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPagesProducer, RoqProjectBuildItem roqProject) {
        final String extractedKey = item.getString(configuredCollection.titleAttributeName());
        if (extractedKey == null) {
            throw new RoqFrontMatterReadingException(RoqException
                    .builder("Error extracting title value from %s collection values".formatted(configuredCollection.id()))
                    .hint("Extracted key for %s is null with property key %s".formatted(item.toString(),
                            configuredCollection.titleAttributeName())));
        }
        final String id = configuredCollection.id() + "/" + slugify(extractedKey, false, false);
        final String path = id + ".md";
        final String layoutId = configuredCollection.layout() != null ? LAYOUTS_DIR + configuredCollection.layout() : null;
        rawPagesProducer.produce(new RoqFrontMatterRawPageBuildItem(
                TemplateSource.create(
                        id,
                        "markdown",
                        new SourceFile(
                                toUnixPath(roqProject.local().roqDir().normalize().toAbsolutePath()
                                        .toString()),
                                path),
                        path,
                        id + ".html",
                        false,
                        true,
                        false,
                        false),
                layoutId,
                item,
                configuredCollection,
                getIncludeFilter(layoutId).apply(""),
                "",
                List.of()));
    }

    private Object convert(DataMappingBuildItem dataMappingBuildItem) {
        try {
            return dataMappingBuildItem.getConverter().convert(dataMappingBuildItem.getContent());
        } catch (IOException e) {
            throw new DataConversionException(
                    "Unable to convert data file %s as an Object".formatted(dataMappingBuildItem.sourceFile()), e);
        }
    }
}
