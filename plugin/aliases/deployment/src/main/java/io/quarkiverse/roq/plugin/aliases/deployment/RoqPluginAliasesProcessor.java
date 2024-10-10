package io.quarkiverse.roq.plugin.aliases.deployment;

import java.util.*;

import io.quarkiverse.roq.frontmatter.deployment.TemplateLink;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.plugin.aliases.deployment.items.RoqFrontMatterAliasesBuildItem;
import io.quarkiverse.roq.plugin.aliases.runtime.RoqFrontMatterAliasesRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqPluginAliasesProcessor {

    private static final String FEATURE = "roq-plugin-aliases";
    private static final String ALIASES_KEY = "aliases";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void consumeTemplates(
            RoqSiteConfig config,
            List<RoqFrontMatterTemplateBuildItem> templates,
            BuildProducer<RoqFrontMatterAliasesBuildItem> aliasesProducer,
            BuildProducer<SelectedPathBuildItem> selectedPathsProducer,
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpointProducer) {

        if (templates.isEmpty()) {
            return;
        }

        HashMap<String, String> aliasMap = new HashMap<>();
        for (RoqFrontMatterTemplateBuildItem item : templates) {

            if (!item.published()) {
                continue;
            }

            Set<String> aliasesName = getAliases(item.data());
            if (aliasesName.isEmpty()) {
                continue;
            }
            RoqUrl url = item.url();
            for (String alias : aliasesName) {
                String aliasLink = TemplateLink.pageLink(config.rootPath(), alias, new TemplateLink.PageLinkData(
                        item.raw().info(), item.raw().collection(), item.data()));
                aliasMap.put(aliasLink, url.absolute());
            }
        }

        for (Map.Entry<String, String> alias : aliasMap.entrySet()) {
            aliasesProducer.produce(new RoqFrontMatterAliasesBuildItem(alias.getKey(), alias.getValue()));
            selectedPathsProducer.produce(new SelectedPathBuildItem(
                    alias.getKey(), null));
            notFoundPageDisplayableEndpointProducer.produce(
                    new NotFoundPageDisplayableEndpointBuildItem(alias.getKey(),
                            "Roq URL alias for " + alias.getValue() + " URL."));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createVertxRedirects(
            HttpRootPathBuildItem httpRootPath,
            RoqFrontMatterAliasesRecorder recorder,
            BuildProducer<RouteBuildItem> routes,
            List<RoqFrontMatterAliasesBuildItem> aliases) {
        for (RoqFrontMatterAliasesBuildItem item : aliases) {
            routes.produce(RouteBuildItem.builder()
                    .route(httpRootPath.relativePath(item.alias()))
                    .handler(recorder.sendRedirectPage(item.target()))
                    .build());
        }
    }

    private Set<String> getAliases(JsonObject json) {
        JsonArray array = json.getJsonArray(ALIASES_KEY);
        if (array == null) {
            return Set.of();
        }
        Set<String> aliases = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            String alias = array.getString(i);
            if (!alias.isBlank()) {
                aliases.add(alias);
            }
        }
        return aliases;
    }
}
