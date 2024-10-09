package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.roq.util.PathUtils.prefixWithSlash;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterMessages;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateGlobal;
import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.ValidationParserHookBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;

class RoqFrontMatterProcessor {

    private static final String FEATURE = "roq-frontmatter";

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
                    .produce(TemplatePathBuildItem.builder().path(item.info().generatedTemplatePath()).extensionInfo(FEATURE)
                            .content(item.generatedContent()).build());
            if (item.published()) {
                if (item.collection() != null) {
                    docTemplates.add(item.info().generatedTemplatePath());
                } else {
                    pageTemplates.add(item.info().generatedTemplatePath());
                }
            } else {
                layoutTemplates.add(item.info().generatedTemplatePath());
            }
        }

        // Setup type-safety for generate templates
        validationParserHookProducer.produce(new ValidationParserHookBuildItem(c -> {
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
            RoqFrontMatterConfig config,
            BuildProducer<SelectedPathBuildItem> selectedPathProducer,
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpointProducer,
            RoqFrontMatterOutputBuildItem roqOutput) {
        if (roqOutput == null) {
            return;
        }

        // Bind Roq Generator and dev-ui endpoints
        if (config.generator()) {
            for (String path : roqOutput.allPagesByPath().keySet()) {
                selectedPathProducer.produce(new SelectedPathBuildItem(addTrailingSlash(path), null)); // We add a trailing slash to make it detected as a html page
                notFoundPageDisplayableEndpointProducer
                        .produce(new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(path)));
            }
        }
    }

}
