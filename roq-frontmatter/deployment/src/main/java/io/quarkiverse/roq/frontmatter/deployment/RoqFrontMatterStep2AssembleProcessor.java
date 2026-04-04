package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.processTemplate;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils.removeThemePrefix;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedContentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.ProcessedTemplate;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqFrontMatterStep2AssembleProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterStep2AssembleProcessor.class);

    // ── Content processing ───────────────────────────────────────────────

    @BuildStep
    void processContent(RoqSiteConfig config,
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
                    config, sortedModifications);

            LOGGER.debugf("Roq content processing producing raw page '%s'", processed.id());
            rawPageProducer.produce(new RoqFrontMatterRawPageBuildItem(
                    processed.templateSource(), processed.layout(), processed.data(),
                    scanned.collection(), processed.generatedTemplate(),
                    processed.generatedContentTemplate(), scanned.attachments()));
        }
    }

    // ── Layout processing ────────────────────────────────────────────────

    @BuildStep
    void processLayouts(RoqSiteConfig config,
            List<RoqFrontMatterScannedLayoutBuildItem> scannedLayouts,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<RoqFrontMatterRawLayoutBuildItem> rawLayoutProducer) {
        if (scannedLayouts.isEmpty()) {
            return;
        }

        final List<RoqFrontMatterDataModificationBuildItem> sortedModifications = dataModifications.stream()
                .sorted(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order))
                .toList();

        // Two-pass processing: regular layouts first, then theme layouts.
        // This lets us track which ids are already defined, so theme layouts
        // only produce a non-theme copy when not overridden by a regular layout.
        Set<String> regularLayoutIds = new HashSet<>();

        // Pass 1: regular layouts (from templates/layouts/)
        for (RoqFrontMatterScannedLayoutBuildItem scanned : scannedLayouts) {
            if (scanned.isThemeLayout()) {
                continue;
            }
            ProcessedTemplate processed = processTemplate(
                    scanned.metadata(), false, false, null,
                    false, false,
                    config, sortedModifications);

            regularLayoutIds.add(processed.id());
            LOGGER.debugf("Roq layout processing producing raw layout '%s'", processed.id());
            rawLayoutProducer.produce(new RoqFrontMatterRawLayoutBuildItem(
                    processed.templateSource(), processed.layout(), processed.data(),
                    processed.generatedTemplate(), false));
        }

        // Pass 2: theme layouts (from templates/theme-layouts/)
        for (RoqFrontMatterScannedLayoutBuildItem scanned : scannedLayouts) {
            if (!scanned.isThemeLayout()) {
                continue;
            }
            ProcessedTemplate processed = processTemplate(
                    scanned.metadata(), false, true, null,
                    false, false,
                    config, sortedModifications);

            LOGGER.debugf("Roq theme-layout processing producing raw layout '%s'", processed.id());
            rawLayoutProducer.produce(new RoqFrontMatterRawLayoutBuildItem(
                    processed.templateSource(), processed.layout(), processed.data(),
                    processed.generatedTemplate(), true));

            // Theme-layout dedup: produce a non-theme copy (themeLayout=false) so pages
            // can reference this layout without the theme prefix, but only if no regular
            // layout already provides it (regular layouts take precedence)
            String overrideId = removeThemePrefix(processed.id());
            if (!regularLayoutIds.contains(overrideId)) {
                LOGGER.debugf("Roq theme-layout producing override '%s'", overrideId);
                rawLayoutProducer.produce(new RoqFrontMatterRawLayoutBuildItem(
                        processed.templateSource().changeIds(RoqFrontMatterLayoutUtils::removeThemePrefix),
                        processed.layout(), processed.data(),
                        processed.generatedTemplate(), false));
            }
        }
    }
}
