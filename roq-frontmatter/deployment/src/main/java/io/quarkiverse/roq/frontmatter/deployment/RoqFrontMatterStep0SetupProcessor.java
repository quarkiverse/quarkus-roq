package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem.FRONTMATTER_HEADER_PARSER_PRIORITY;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.*;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.*;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.DRAFT;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.ESCAPE;
import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlash;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

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
                        RoqException.builder("Front matter reading error")
                                .sourceFilePath(c.templatePath())
                                .detail("Failed to parse the YAML front matter block (enclosed by '---').")
                                .hint("Check that the YAML syntax between the --- delimiters is valid.")
                                .cause(e));
            }
        }, RoqFrontMatterTemplateUtils::stripFrontMatter, FRONTMATTER_HEADER_PARSER_PRIORITY);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void watch(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<WebBundlerWatchedDirBuildItem> webBundlerWatch,
            BuildProducer<HotDeploymentWatchedFileBuildItem> hotWatch) {
        List<String> dirs = List.of(config.contentDir(), config.staticDir(), config.publicDir(), TEMPLATES_DIR);
        if (roqProject.local() != null) {
            Path roqDir = roqProject.local().roqDir();
            for (String dirName : dirs) {
                Path dir = roqDir.resolve(dirName);
                webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(dir));
                RoqProjectBuildItem.watchDirRecursively(dir, hotWatch);
            }
        }
        for (String dirName : dirs) {
            if (TEMPLATES_DIR.equals(dirName) && roqProject.roqResourceDir() == null) {
                continue;
            }
            String prefix = addTrailingSlash(roqProject.resolveRoqResourceSubDir(dirName));
            hotWatch.produce(HotDeploymentWatchedFileBuildItem.builder()
                    .setLocationPredicate(p -> p.startsWith(prefix))
                    .build());
        }
    }

    private static Predicate<String> isPageEscaped(RoqSiteConfig config) {
        return path -> config.escapedPages().orElse(List.of()).stream()
                .anyMatch(s -> Path.of("").getFileSystem().getPathMatcher("glob:" + s)
                        .matches(Path.of(path)));
    }
}
