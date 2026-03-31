package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem.FRONTMATTER_HEADER_PARSER_PRIORITY;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.*;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.*;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.DRAFT;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.ESCAPE;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqFrontMatterStep0SetupProcessor {

    // Data modifications are callbacks that transform front matter data during Step2 (assemble).
    // They run on every page/layout and can add/modify front matter keys.

    @BuildStep
    void registerEscapedTemplates(RoqSiteConfig config,
            BuildProducer<RoqFrontMatterDataModificationBuildItem> dataModificationProducer) {
        // Mark pages matching the "escaped-pages" glob patterns so their Qute expressions
        // are wrapped in escape delimiters (useful for pages containing code samples with { })
        dataModificationProducer.produce(new RoqFrontMatterDataModificationBuildItem(sourceData -> {
            if (sourceData.isPage() && !sourceData.fm().containsKey(ESCAPE)
                    && isPageEscaped(config).test(sourceData.relativePath())) {
                sourceData.fm().put(ESCAPE, true);
            }
            return sourceData.fm();
        }));
    }

    @BuildStep
    void amendDraftContent(RoqSiteConfig config,
            BuildProducer<RoqFrontMatterDataModificationBuildItem> dataModificationProducer) {
        // Pages inside the draft directory are automatically marked as drafts
        // (unless they already have an explicit "draft" key in their front matter)
        dataModificationProducer.produce(new RoqFrontMatterDataModificationBuildItem(sourceData -> {
            var isInDraftDirectory = sourceData.isPage() && sourceData.collection() != null
                    && sourceData.relativePath().contains(config.draftDirectory() + "/");

            if (isInDraftDirectory && !sourceData.fm().containsKey(DRAFT)) {
                sourceData.fm().put(DRAFT, true);
            }
            return sourceData.fm();
        }));
    }

    @BuildStep
    RoqFrontMatterHeaderParserBuildItem registerFrontMatterParse(RoqJacksonBuildItem jackson) {
        // Register the YAML front matter parser (--- delimited blocks at the top of files).
        // Other extensions can register additional parsers via RoqFrontMatterHeaderParserBuildItem.
        return new RoqFrontMatterHeaderParserBuildItem(templateContext -> hasFrontMatter(templateContext.content()), c -> {
            try {
                return readFM(jackson.getYamlMapper(), c.content());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new RoqFrontMatterReadingException(
                        "Error reading YAML FrontMatter block (enclosed by '---') in file: %s".formatted(c.templatePath()));
            }
        }, RoqFrontMatterTemplateUtils::stripFrontMatter, FRONTMATTER_HEADER_PARSER_PRIORITY);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void watch(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<WebBundlerWatchedDirBuildItem> webBundlerWatch) {
        // In dev mode, tell WebBundler to watch Roq directories so changes trigger a rebuild
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
