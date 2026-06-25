package io.quarkiverse.roq.plugin.ogimage.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.TEMPLATES_DIR;
import static io.quarkiverse.tools.stringpaths.StringPaths.prefixWithSlash;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgCardRenderer;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageConfig;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageKeys;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageRecorder;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageService;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageRegistry;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.vertx.core.json.JsonObject;

public class RoqPluginOgImageProcessor {

    private static final String FEATURE = "roq-plugin-og-image";

    private static final String OG_IMAGE_TEMPLATES_GLOB = "glob:og-image/**.svg";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
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
    void registerAdditionalBeans(
            OgImageConfig config,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (!config.enabled() || resolveTargets(config, normalPages, documents).byPngPath().isEmpty()) {
            return;
        }
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(OgImageService.class, OgCardRenderer.class)
                .setUnremovable()
                .build());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRegistry(
            OgImageConfig config,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<SelectedPathBuildItem> selectedPaths,
            OgImageRecorder recorder) {
        if (!config.enabled()) {
            return;
        }

        OgImageTargetResolver.ResolvedTargets resolved = resolveTargets(config, normalPages, documents);
        if (resolved.byPngPath().isEmpty()) {
            return;
        }

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(OgImageRegistry.class)
                .scope(Singleton.class)
                .unremovable()
                .runtimeValue(recorder.createRegistry(resolved.byPngPath()))
                .done());

        for (String pngPath : resolved.byPngPath().keySet()) {
            selectedPaths.produce(new SelectedPathBuildItem(prefixWithSlash(pngPath), pngPath));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerRoutes(
            OgImageConfig config,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishNormalPageBuildItem> normalPages,
            BuildProducer<RouteBuildItem> routes,
            OgImageRecorder recorder) {
        if (!config.enabled()) {
            return;
        }

        OgImageTargetResolver.ResolvedTargets resolved = resolveTargets(config, normalPages, documents);
        for (Map.Entry<String, OgImageTarget> entry : resolved.byPngPath().entrySet()) {
            routes.produce(RouteBuildItem.builder()
                    .route(prefixWithSlash(entry.getKey()))
                    .handler(recorder.pngHandler(entry.getValue()))
                    .build());
        }
    }

    @BuildStep
    RoqFrontMatterDataModificationBuildItem injectOgImageMetadata(OgImageConfig config) {
        if (!config.enabled()) {
            return new RoqFrontMatterDataModificationBuildItem(source -> source.fm());
        }

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
