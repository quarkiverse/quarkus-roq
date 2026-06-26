package io.quarkiverse.roq.plugin.ogimage.deployment;

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
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageConfig;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageKeys;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;
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

public class RoqPluginOgImageProcessor {

    private static final String FEATURE = "roq-plugin-og-image";

    private static final String OG_IMAGE_TEMPLATES_GLOB = "glob:og-image/**.svg";
    private static final String OG_SEO_IMAGE_TAG = "tags/ogSeoImage.html";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerOgSeoImageTag(
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) throws java.io.IOException {
        String resourcePath = TEMPLATES_DIR + "/" + OG_SEO_IMAGE_TAG;
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing OG SEO tag template: " + resourcePath);
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            generatedResourceProducer
                    .produce(new GeneratedResourceBuildItem(resourcePath, content.getBytes(StandardCharsets.UTF_8)));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(resourcePath));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .priority(ROOT_ARCHIVE_PRIORITY + 10)
                    .path(OG_SEO_IMAGE_TAG)
                    .content(content)
                    .parserConfig(ParserConfig.DEFAULT)
                    .extensionInfo(FEATURE)
                    .build());
        }
    }

    @BuildStep
    void scanOgImageTemplates(
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
                .matching(OG_IMAGE_TEMPLATES_GLOB)
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
    void generateOgImages(
            RoqProjectBuildItem roqProject,
            OgImageConfig config,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages,
            List<TemplatePathBuildItem> templatePaths,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        if (!roqProject.isActive()) {
            return;
        }

        OgImageTargetResolver.ResolvedTargets resolved = resolveTargets(config, normalPages, documents);
        if (resolved.byPngPath().isEmpty()) {
            return;
        }

        Engine engine = OgCardBuildTimeRenderer.createEngine(config, templatePaths);
        OgCardBuildTimeRenderer.validateTemplate(config, engine);

        for (OgImageTarget target : resolved.byPngPath().values()) {
            byte[] png = OgCardBuildTimeRenderer.render(config, engine, target);
            staticFiles.produce(new RoqFrontMatterStaticFileBuildItem(target.pngPath(), png));
        }
    }

    @BuildStep
    RoqFrontMatterDataModificationBuildItem injectOgImageMetadata(OgImageConfig config) {
        return new RoqFrontMatterDataModificationBuildItem(source -> {
            OgImageTargetResolver.PageContext pageContext = OgImageTargetResolver.pageContextFromSource(source);
            if (!OgImageTargetResolver.matchesSource(config, source, pageContext.pagePath(), pageContext.collectionId(),
                    pageContext.siteIndex())) {
                return source.fm();
            }
            OgImageTarget target = OgImageTargetResolver.targetFromSource(config, source, pageContext.pagePath(),
                    pageContext.collectionId(), pageContext.slug(), pageContext.siteIndex());
            if (target == null) {
                return source.fm();
            }
            JsonObject fm = source.fm().copy();
            fm.put(OgImageKeys.IMAGE_PATH, target.pngPath());
            fm.put(OgImageKeys.WIDTH, target.width());
            fm.put(OgImageKeys.HEIGHT, target.height());
            fm.put(OgImageKeys.ALT, target.imageAlt());
            return fm;
        });
    }

    private static OgImageTargetResolver.ResolvedTargets resolveTargets(
            OgImageConfig config,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents) {
        return OgImageTargetResolver.resolve(config, config.siteName(), documents, normalPages);
    }
}
