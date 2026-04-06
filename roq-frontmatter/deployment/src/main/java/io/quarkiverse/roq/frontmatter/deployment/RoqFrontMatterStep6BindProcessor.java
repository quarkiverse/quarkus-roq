package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.isLayoutSourceTemplate;
import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlash;
import static io.quarkiverse.tools.stringpaths.StringPaths.prefixWithSlash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterLayoutTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterMessages;
import io.quarkiverse.roq.frontmatter.runtime.RoqQuteEngineObserver;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateGlobal;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollections;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.BuildSystemTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.ValidationParserHookBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;

public class RoqFrontMatterStep6BindProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterStep6BindProcessor.class);
    public static final String FEATURE = "roq-frontmatter";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    // Write generated Qute templates to disk and register them with the Qute engine.
    // Each page produces two templates: "full" (with layout include) and "content" (body only).
    // Also sets up type-safe validation so Qute knows page/site variable types.
    @BuildStep
    void bindQuteTemplates(
            RoqSiteConfig roqSiteConfig,
            BuildSystemTargetBuildItem buildSystemTargetBuildItem,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<ValidationParserHookBuildItem> validationParserHookProducer,
            List<RoqFrontMatterPageTemplateBuildItem> pageTemplatesItems,
            List<RoqFrontMatterLayoutTemplateBuildItem> layoutTemplatesItems,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            RoqFrontMatterOutputBuildItem roqOutput) {
        if (roqOutput == null) {
            return;
        }
        final Path roqTemplatesOutputDir = buildSystemTargetBuildItem.getOutputDirectory()
                .resolve(roqSiteConfig.generatedTemplatesOutputDir());
        try {
            FileUtil.deleteDirectory(roqTemplatesOutputDir);
            Files.createDirectories(roqTemplatesOutputDir);

            final Set<String> docTemplates = new HashSet<>();
            final Set<String> pageTemplates = new HashSet<>();
            final Set<String> layoutTemplates = new HashSet<>();
            // Produce generated Qute templates
            for (RoqFrontMatterPageTemplateBuildItem item : pageTemplatesItems) {
                final Path filePath = roqTemplatesOutputDir
                        .resolve("full")
                        .resolve(item.raw().templateSource().generatedQuteId());
                createTemplateResource(generatedResourceProducer, nativeImageResourceProducer, filePath,
                        item.raw().generatedTemplate(), item.raw().templateSource().generatedQuteTemplateId());
                templatePathProducer
                        .produce(TemplatePathBuildItem.builder()
                                .fullPath(filePath)
                                .path(item.raw().templateSource().generatedQuteTemplateId())
                                .content(item.raw().generatedTemplate())
                                .parserConfig(item.raw().parserConfig())
                                .extensionInfo(FEATURE)
                                .build());

                // Add the template for the content
                final Path contentFilePath = roqTemplatesOutputDir
                        .resolve("content")
                        .resolve(item.raw().templateSource().generatedQuteId());
                Files.createDirectories(contentFilePath.getParent());
                Files.writeString(contentFilePath, item.raw().generatedContentTemplate());
                final String contentResourceName = "templates/" + item.raw().templateSource().generatedQuteContentTemplateId();
                generatedResourceProducer
                        .produce(new GeneratedResourceBuildItem(
                                contentResourceName,
                                item.raw().generatedContentTemplate().getBytes(StandardCharsets.UTF_8)));
                nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(contentResourceName));
                templatePathProducer.produce(TemplatePathBuildItem.builder()
                        .fullPath(contentFilePath)
                        .path(item.raw().templateSource().generatedQuteContentTemplateId())
                        .content(item.raw().generatedContentTemplate())
                        .parserConfig(item.raw().parserConfig())
                        .extensionInfo(FEATURE)
                        .build());
                if (item.raw().collection() != null) {
                    docTemplates.add(item.raw().templateSource().generatedQuteContentTemplateId());
                    docTemplates.add(item.raw().templateSource().generatedQuteTemplateId());
                } else {
                    pageTemplates.add(item.raw().templateSource().generatedQuteContentTemplateId());
                    pageTemplates.add(item.raw().templateSource().generatedQuteTemplateId());
                }

            }

            for (RoqFrontMatterLayoutTemplateBuildItem item : layoutTemplatesItems) {
                final Path filePath = roqTemplatesOutputDir
                        .resolve(item.raw().templateSource().generatedQuteId());
                createTemplateResource(generatedResourceProducer, nativeImageResourceProducer, filePath,
                        item.raw().generatedTemplate(), item.raw().templateSource().generatedQuteTemplateId());
                templatePathProducer
                        .produce(TemplatePathBuildItem.builder()
                                .path(item.raw().templateSource().generatedQuteTemplateId())
                                .fullPath(filePath)
                                .parserConfig(item.raw().parserConfig())
                                .extensionInfo(FEATURE)
                                .content(item.raw().generatedTemplate()).build());
                layoutTemplates.add(item.raw().templateSource().generatedQuteTemplateId());
            }

            // Setup type-safe validation: declare `page` and `site` parameter types per template kind.
            // This enables Qute's compile-time type checking for template expressions.
            validationParserHookProducer.produce(new ValidationParserHookBuildItem(c -> {
                if (isLayoutSourceTemplate(c.getTemplateId())) {
                    // Layout source templates are intermediate (included by pages) — skip validation
                    // to avoid false errors on {#insert} sections. See #530
                    c.addContentFilter(s -> "");
                    return;
                }
                if (docTemplates.contains(c.getTemplateId())) {
                    c.addParameter("page", DocumentPage.class.getName());
                    c.addParameter("site", Site.class.getName());
                } else if (pageTemplates.contains(c.getTemplateId())) {
                    c.addParameter("page", NormalPage.class.getName());
                    c.addParameter("site", Site.class.getName());
                } else if (layoutTemplates.contains(c.getTemplateId())) {
                    c.addParameter("page", Page.class.getName());
                    c.addParameter("site", Site.class.getName());
                }
            }));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void createTemplateResource(BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer, Path filePath,
            String generatedTemplate, String generatedQuteTemplateId) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, generatedTemplate);
        final String resourceName = "templates/" + generatedQuteTemplateId;
        generatedResourceProducer
                .produce(new GeneratedResourceBuildItem(
                        resourceName,
                        generatedTemplate.getBytes(StandardCharsets.UTF_8)));
        nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(resourceName));
    }

    // Register runtime model classes as CDI beans so they can be injected and
    // Register template extensions unconditionally so Qute can validate templates
    // (e.g. fm/rss.html) even when no Roq site is configured (e.g. in plugin tests)
    @BuildStep
    void registerTemplateExtensions(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(RoqTemplateExtension.class)
                .setUnremovable().build());
    }

    // are recognized by Qute's type-safe expressions
    @BuildStep
    void registerAdditionalBeans(RoqFrontMatterOutputBuildItem roqOutput,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (roqOutput == null) {
            return;
        }
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        RoqQuteEngineObserver.class,
                        RoqFrontMatterMessages.class,
                        RoqTemplateGlobal.class,
                        Page.class,
                        PageSource.class,
                        TemplateSource.class,
                        RoqUrl.class,
                        RootUrl.class,
                        DocumentPage.class,
                        NormalPage.class,
                        RoqCollections.class,
                        RoqCollection.class,
                        Paginator.class,
                        SourceFile.class)
                .setUnremovable().build());
    }

    // Register page paths with Roq Generator (for static site generation)
    // and with the dev-ui 404 page (for the endpoint listing)
    @BuildStep
    void bindEndpoints(
            RoqSiteConfig config,
            BuildProducer<SelectedPathBuildItem> selectedPathProducer,
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpointProducer,
            RoqFrontMatterOutputBuildItem roqOutput) {
        if (roqOutput == null) {
            return;
        }

        // Bind Roq Generator and dev-ui endpoints
        if (config.generator()) {
            for (String path : roqOutput.allPagesByPath().keySet()) {
                // If there is no extension, we add a trailing slash to make it detected as a html page (this is Roq Generator api)
                final String selectedPath = StringPaths.fileExtension(path) != null ? path : addTrailingSlash(path);
                selectedPathProducer.produce(new SelectedPathBuildItem(prefixWithSlash(selectedPath), null));
                notFoundPageDisplayableEndpointProducer
                        .produce(new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(path)));
            }
        }
    }

    // Publish static files (attachments from content pages) as generated static resources.
    // These are served directly by Vert.x without going through the Qute rendering pipeline.
    @BuildStep
    void bindStaticFiles(
            LaunchModeBuildItem launchMode,
            BuildProducer<SelectedPathBuildItem> selectedPathProducer,
            List<RoqFrontMatterStaticFileBuildItem> staticFiles,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourcesProducer) {
        Map<String, String> paths = new HashMap<>();
        for (RoqFrontMatterStaticFileBuildItem staticFile : staticFiles) {
            final String endpoint = prefixWithSlash(staticFile.link());
            final String prev = paths.put(endpoint, staticFile.filePath().toString());
            if (prev != null) {
                String message = """
                        Conflict detected: Multiple source files are targeting the same endpoint '%s':
                          - '%s'
                          - '%s'
                        """.formatted(endpoint, prev, staticFile.filePath());
                if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
                    LOGGER.warn(message
                            + "\nIn development, the first occurrence will be kept, but this will cause an exception in normal mode.");
                    continue;
                } else {
                    throw new RoqPathConflictException(message);
                }
            }

            LOGGER.debugf("Published static file: '%s'", endpoint);
            selectedPathProducer.produce(new SelectedPathBuildItem(endpoint, null));
            staticResourcesProducer.produce(new GeneratedStaticResourceBuildItem(
                    endpoint, staticFile.filePath()));
        }
    }

}
