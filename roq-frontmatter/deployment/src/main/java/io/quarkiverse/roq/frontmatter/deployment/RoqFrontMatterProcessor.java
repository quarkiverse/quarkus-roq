package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.isLayoutSourceTemplate;
import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.roq.util.PathUtils.getExtension;
import static io.quarkiverse.roq.util.PathUtils.prefixWithSlash;

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

import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterLayoutTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterMessages;
import io.quarkiverse.roq.frontmatter.runtime.RoqI18NTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.RoqQuteEngineObserver;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateGlobal;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollections;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
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

public class RoqFrontMatterProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterProcessor.class);
    public static final String FEATURE = "roq-frontmatter";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

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
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, item.raw().generatedTemplate());
                final String resourceName = "templates/" + item.raw().templateSource().generatedQuteTemplateId();
                generatedResourceProducer
                        .produce(new GeneratedResourceBuildItem(
                                resourceName,
                                item.raw().generatedTemplate().getBytes(StandardCharsets.UTF_8)));
                nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(resourceName));
                templatePathProducer
                        .produce(TemplatePathBuildItem.builder()
                                .fullPath(filePath)
                                .path(item.raw().templateSource().generatedQuteTemplateId())
                                .content(item.raw().generatedTemplate())
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
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, item.raw().generatedTemplate());
                final String resourceName = "templates/" + item.raw().templateSource().generatedQuteTemplateId();
                generatedResourceProducer
                        .produce(new GeneratedResourceBuildItem(
                                resourceName,
                                item.raw().generatedTemplate().getBytes(StandardCharsets.UTF_8)));
                nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(resourceName));
                templatePathProducer
                        .produce(TemplatePathBuildItem.builder()
                                .path(item.raw().templateSource().generatedQuteTemplateId())
                                .fullPath(filePath)
                                .extensionInfo(FEATURE)
                                .content(item.raw().generatedTemplate()).build());
                layoutTemplates.add(item.raw().templateSource().generatedQuteTemplateId());
            }

            // Setup type-safety for generate templates
            validationParserHookProducer.produce(new ValidationParserHookBuildItem(c -> {
                if (isLayoutSourceTemplate(c.getTemplateId())) {
                    // Fixes https://github.com/quarkiverse/quarkus-roq/issues/530
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
                        RoqTemplateExtension.class,
                        RoqI18NTemplateExtension.class,
                        RoqTemplateGlobal.class,
                        Page.class,
                        RoqUrl.class,
                        RootUrl.class,
                        DocumentPage.class,
                        NormalPage.class,
                        RoqCollections.class,
                        RoqCollection.class,
                        Paginator.class)
                .setUnremovable().build());
    }

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
                final String selectedPath = getExtension(path) != null ? path : addTrailingSlash(path);
                selectedPathProducer.produce(new SelectedPathBuildItem(prefixWithSlash(selectedPath), null));
                notFoundPageDisplayableEndpointProducer
                        .produce(new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(path)));
            }
        }
    }

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
