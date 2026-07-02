package io.quarkiverse.roq.plugin.ogcard.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.TEMPLATES_DIR;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.plugin.ogcard.runtime.OgCardConfig;
import io.quarkiverse.roq.plugin.ogcard.runtime.OgCardKeys;
import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardTarget;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.Engine;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.vertx.core.json.JsonObject;

public class RoqPluginOgCardProcessor {

    private static final String FEATURE = "roq-plugin-og-card";

    private static final String OG_CARD_TEMPLATES_GLOB = "glob:og-card/**.svg";
    private static final String SEO_IMAGE_TAG = "tags/seoImage.html";
    private static final String SEO_IMAGE_OVERRIDE_RESOURCE = "templates/partials/og-card/seoImage.html";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerSeoImageOverride(
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) throws java.io.IOException {
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(SEO_IMAGE_OVERRIDE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing seoImage override template: " + SEO_IMAGE_OVERRIDE_RESOURCE);
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String targetPath = TEMPLATES_DIR + "/" + SEO_IMAGE_TAG;
            generatedResourceProducer
                    .produce(new GeneratedResourceBuildItem(targetPath, content.getBytes(StandardCharsets.UTF_8)));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(targetPath));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .priority(ROOT_ARCHIVE_PRIORITY + 10)
                    .path(SEO_IMAGE_TAG)
                    .content(content)
                    .parserConfig(ParserConfig.DEFAULT)
                    .extensionInfo(FEATURE)
                    .build());
        }
    }

    @BuildStep
    void scanOgCardTemplates(
            RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) throws java.io.IOException {
        if (!roqProject.isActive()) {
            return;
        }

        ParserConfig parserConfig = ParserConfig.DEFAULT;

        for (ProjectFile file : scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .matching(OG_CARD_TEMPLATES_GLOB)
                .list()) {
            String link = toUnixPath(file.scopedPath());
            String content = new String(file.content(), file.charset());
            generatedResourceProducer.produce(new GeneratedResourceBuildItem(
                    TEMPLATES_DIR + "/" + link,
                    content.getBytes(StandardCharsets.UTF_8)));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(TEMPLATES_DIR + "/" + link));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .priority(ROOT_ARCHIVE_PRIORITY)
                    .path(link)
                    .fullPath(file.file())
                    .content(content)
                    .parserConfig(parserConfig)
                    .extensionInfo(FEATURE)
                    .build());
        }
    }

    @BuildStep
    void generateOgCards(
            RoqProjectBuildItem roqProject,
            OgCardConfig config,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages,
            List<TemplatePathBuildItem> templatePaths,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        if (!roqProject.isActive()) {
            return;
        }

        OgCardTargetResolver.ResolvedTargets resolved = resolveTargets(config, normalPages, documents);
        if (resolved.byPngPath().isEmpty()) {
            return;
        }

        Engine engine = OgCardBuildTimeRenderer.createEngine(config, templatePaths);
        OgCardBuildTimeRenderer.validateTemplate(config, engine);

        for (OgCardTarget target : resolved.byPngPath().values()) {
            byte[] png = OgCardBuildTimeRenderer.render(config, engine, target);
            staticFiles.produce(new RoqFrontMatterStaticFileBuildItem(target.pngPath(), png));
        }
    }

    @BuildStep
    RoqFrontMatterDataModificationBuildItem injectOgCardMetadata(OgCardConfig config) {
        return new RoqFrontMatterDataModificationBuildItem(source -> {
            OgCardTargetResolver.PageContext pageContext = OgCardTargetResolver.pageContextFromSource(source);
            if (!OgCardTargetResolver.matchesSource(config, source, pageContext.pagePath(), pageContext.collectionId(),
                    pageContext.siteIndex())) {
                return source.fm();
            }
            OgCardTarget target = OgCardTargetResolver.targetFromSource(config, source, pageContext.pagePath(),
                    pageContext.collectionId(), pageContext.slug(), pageContext.siteIndex());
            if (target == null) {
                return source.fm();
            }
            JsonObject fm = source.fm().copy();
            fm.put(OgCardKeys.IMAGE_PATH, target.pngPath());
            fm.put(OgCardKeys.WIDTH, target.width());
            fm.put(OgCardKeys.HEIGHT, target.height());
            fm.put(OgCardKeys.ALT, target.imageAlt());
            return fm;
        });
    }

    private static OgCardTargetResolver.ResolvedTargets resolveTargets(
            OgCardConfig config,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents) {
        return OgCardTargetResolver.resolve(config, config.siteName(), documents, normalPages);
    }
}
