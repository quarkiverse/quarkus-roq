package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.DRAFT_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem.FRONTMATTER_HEADER_PARSER_PRIORITY;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.ESCAPE_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.TEMPLATES_DIR;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.hasFrontMatter;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.readFM;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.stripFrontMatter;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqFrontMatterScanProcessor {

    @BuildStep
    void registerEscapedTemplates(RoqSiteConfig config,
            BuildProducer<RoqFrontMatterDataModificationBuildItem> dataModificationProducer) {
        dataModificationProducer.produce(new RoqFrontMatterDataModificationBuildItem(sourceData -> {
            if (sourceData.type().isPage() && !sourceData.fm().containsKey(ESCAPE_KEY)
                    && isPageEscaped(config).test(sourceData.relativePath())) {
                sourceData.fm().put(ESCAPE_KEY, true);
            }
            return sourceData.fm();
        }));
    }

    @BuildStep
    void amendDraftContent(RoqSiteConfig config,
            BuildProducer<RoqFrontMatterDataModificationBuildItem> dataModificationProducer) {
        dataModificationProducer.produce(new RoqFrontMatterDataModificationBuildItem(sourceData -> {
            var isInDraftDirectory = sourceData.type().isPage() && sourceData.collection() != null
                    && sourceData.relativePath().contains(config.draftDirectory() + "/");

            // Frontmatter `draft` takes precedence; drafts directory acts as fallback.
            if (isInDraftDirectory && !sourceData.fm().containsKey(DRAFT_KEY)) {
                sourceData.fm().put(DRAFT_KEY, true);
            }
            return sourceData.fm();
        }));
    }

    @BuildStep
    RoqFrontMatterHeaderParserBuildItem registerFrontMatterParse(RoqJacksonBuildItem jackson) {
        return new RoqFrontMatterHeaderParserBuildItem(templateContext -> hasFrontMatter(templateContext.content()), c -> {
            try {
                return readFM(jackson.getYamlMapper(), c.content());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new RoqFrontMatterReadingException(
                        "Error reading YAML FrontMatter block (enclosed by '---') in file: %s".formatted(c.templatePath()));
            }
        }, RoqFrontMatterScanUtils::stripFrontMatter, FRONTMATTER_HEADER_PARSER_PRIORITY);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void watch(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<WebBundlerWatchedDirBuildItem> webBundlerWatch) {
        if (roqProject.local() == null) {
            return;
        }
        Path roqDir = roqProject.local().roqDir();
        webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(roqDir.resolve(config.contentDir())));
        webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(roqDir.resolve(config.staticDir())));
        webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(roqDir.resolve(config.publicDir())));
        webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(roqDir.resolve(TEMPLATES_DIR)));
    }

    private static Predicate<String> isPageEscaped(RoqSiteConfig config) {
        return path -> config.escapedPages().orElse(List.of()).stream()
                .anyMatch(s -> Path.of("").getFileSystem().getPathMatcher("glob:" + s)
                        .matches(Path.of(path)));
    }
}
