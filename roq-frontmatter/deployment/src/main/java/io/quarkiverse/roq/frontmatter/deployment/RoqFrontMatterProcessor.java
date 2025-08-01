package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.isLayoutSourceTemplate;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.resolveGeneratedContentTemplateId;
import static io.quarkiverse.roq.util.PathUtils.*;

import java.util.*;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.*;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
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
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<ValidationParserHookBuildItem> validationParserHookProducer,
            List<RoqFrontMatterRawTemplateBuildItem> roqFrontMatterTemplates,
            RoqFrontMatterOutputBuildItem roqOutput) {
        if (roqOutput == null) {
            return;
        }
        final Set<String> docTemplates = new HashSet<>();
        final Set<String> pageTemplates = new HashSet<>();
        final Set<String> layoutTemplates = new HashSet<>();
        // Produce generated Qute templates
        for (RoqFrontMatterRawTemplateBuildItem item : roqFrontMatterTemplates) {
            templatePathProducer
                    .produce(TemplatePathBuildItem.builder().path(item.info().generatedTemplateId()).extensionInfo(FEATURE)
                            .content(item.generatedTemplate()).build());
            if (item.published()) {
                // Add the template for just the content
                final String contentTemplateId = resolveGeneratedContentTemplateId(item.info().generatedTemplateId());
                templatePathProducer.produce(TemplatePathBuildItem.builder().path(contentTemplateId).extensionInfo(FEATURE)
                        .content(item.generatedContentTemplate()).build());
                if (item.collection() != null) {
                    docTemplates.add(contentTemplateId);
                    docTemplates.add(item.info().generatedTemplateId());
                } else {
                    pageTemplates.add(contentTemplateId);
                    pageTemplates.add(item.info().generatedTemplateId());
                }
            } else {
                layoutTemplates.add(item.info().generatedTemplateId());
            }
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
                selectedPathProducer.produce(new SelectedPathBuildItem(selectedPath, null));
                notFoundPageDisplayableEndpointProducer
                        .produce(new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(path)));
            }
        }
    }

    @BuildStep
    void bindStaticFiles(
            RoqSiteConfig config,
            LaunchModeBuildItem launchMode,
            BuildProducer<SelectedPathBuildItem> selectedPathProducer,
            List<RoqFrontMatterStaticFileBuildItem> staticFiles,
            BuildProducer<GeneratedStaticResourceBuildItem> staticResourcesProducer) {
        Map<String, String> paths = new HashMap<>();
        for (RoqFrontMatterStaticFileBuildItem staticFile : staticFiles) {
            final String endpoint = prefixWithSlash(staticFile.link());
            final String prev = paths.put(endpoint, staticFile.filePath().toString());
            if (prev != null) {
                if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
                    LOGGER.warnf(
                            "Conflict detected: Duplicate path (%s) found in %s and %s. In development, the first occurrence will be kept, but this will cause an exception in normal mode.",
                            endpoint, prev, staticFile.filePath());
                    continue;
                } else {
                    throw new RoqPathConflictException(
                            "Conflict detected: Duplicate path (%s) found in %s and %s".formatted(endpoint, prev,
                                    staticFile.filePath()));
                }
            }

            LOGGER.debugf("Published static file: '%s'", endpoint);
            selectedPathProducer.produce(new SelectedPathBuildItem(endpoint, null));
            staticResourcesProducer.produce(new GeneratedStaticResourceBuildItem(
                    endpoint, staticFile.filePath()));
        }
    }

}
