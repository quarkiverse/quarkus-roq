package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.processTemplate;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterAvailableLayoutsBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedContentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.ProcessedTemplate;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

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
                    scanned.collection(), scanned.metadata().parserConfig(),
                    processed.generatedTemplate(),
                    processed.generatedContentTemplate(), scanned.attachments()));
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
                    scanned.metadata().parserConfig(),
                    processed.generatedTemplate(), isThemeLayout));
        }
    }
}
