package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.Page.LINK_KEY;
import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.roq.util.PathUtils.removeExtension;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.deployment.QuteWebTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.RoqFrontMatterBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.*;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.ValidationParserHookBuildItem;
import io.vertx.core.json.JsonObject;

class RoqFrontMatterProcessor {

    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterProcessor.class);
    private static final String FEATURE = "roq-frontmatter";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void generateTemplate(
            RoqFrontMatterConfig config,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<QuteWebTemplateBuildItem> quteWebTemplateProducer,
            BuildProducer<ValidationParserHookBuildItem> validationParserHookProducer,
            BuildProducer<SelectedPathBuildItem> selectedPathProducer,
            List<RoqFrontMatterBuildItem> roqFrontMatterBuildItems,
            RoqFrontMatterOutputBuildItem roqOutput) {
        final Set<String> templates = new HashSet<>();
        for (RoqFrontMatterBuildItem item : roqFrontMatterBuildItems) {
            final String name = removeExtension(item.templatePath());
            templatePathProducer.produce(TemplatePathBuildItem.builder().path(item.templatePath()).extensionInfo(FEATURE)
                    .content(item.generatedContent()).build());
            final String path = roqOutput.paths().get(name);
            if (path != null) {
                if (config.generator()) {
                    selectedPathProducer.produce(new SelectedPathBuildItem(addTrailingSlash(path))); // We add a trailing slash to make it detected as a html page
                }
                templates.add(item.templatePath());
                quteWebTemplateProducer
                        .produce(new QuteWebTemplateBuildItem(name,
                                path));
            }

        }
        validationParserHookProducer.produce(new ValidationParserHookBuildItem(c -> {
            if (templates.contains(c.getTemplateId())) {
                c.addParameter("page", Page.class.getName());
                c.addParameter("site", Site.class.getName());
                c.addParameter("collections", RoqCollections.class.getName());
                c.addParameter("config", RoqSiteConfig.class.getName());
            }
        }));

    }

    @BuildStep
    void registerAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqFrontmatterDataQuteEngineObserver.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqTemplateExtension.class));
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem addSite() {
        return new AdditionalIndexedClassesBuildItem(DotName.createSimple(Site.class).toString());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    RoqFrontMatterOutputBuildItem bindFrontMatterData(BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqFrontMatterBuildItem> roqFrontMatterBuildItems,
            RoqFrontMatterRecorder recorder) {
        final var byKey = roqFrontMatterBuildItems.stream()
                .collect(Collectors.toMap(RoqFrontMatterBuildItem::key, Function.identity()));
        final var collections = new HashMap<String, List<Supplier<Page>>>();
        final Map<String, String> paths = new HashMap<>();
        for (RoqFrontMatterBuildItem item : roqFrontMatterBuildItems) {
            if (!item.visible()) {
                continue;
            }
            final String name = item.key();
            LOGGER.info("Creating synthetic bean for page with name " + name);
            final JsonObject merged = mergeParents(item, byKey);

            final String link = Link.link(merged);
            merged.put(LINK_KEY, link);
            final Supplier<Page> pageSupplier = recorder.createPage(name, merged);
            if (item.collection() != null) {
                collections.computeIfAbsent(item.collection(), k -> new ArrayList<>()).add(pageSupplier);
            }

            beansProducer.produce(SyntheticBeanBuildItem.configure(Page.class)
                    .scope(Singleton.class)
                    .unremovable()
                    .addQualifier().annotation(Named.class).addValue("value", name).done()
                    .supplier(pageSupplier)
                    .done());
            paths.put(name, link);
        }
        beansProducer.produce(SyntheticBeanBuildItem.configure(RoqCollections.class)
                .scope(Singleton.class)
                .unremovable()
                .supplier(recorder.createRoqCollections(collections))
                .done());
        return new RoqFrontMatterOutputBuildItem(paths);
    }

    private static JsonObject mergeParents(RoqFrontMatterBuildItem item, Map<String, RoqFrontMatterBuildItem> byPath) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = item.layout();
        fms.add(item.fm());
        while (parent != null) {
            if (byPath.containsKey(parent)) {
                final RoqFrontMatterBuildItem parentItem = byPath.get(parent);
                parent = parentItem.layout();
                fms.push(parentItem.fm());
            } else {
                parent = null;
            }
        }

        JsonObject merged = new JsonObject();
        while (!fms.empty()) {
            merged.mergeIn(fms.pop());
        }
        return merged;
    }

}
