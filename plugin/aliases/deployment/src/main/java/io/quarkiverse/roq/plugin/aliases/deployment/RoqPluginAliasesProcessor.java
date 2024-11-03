package io.quarkiverse.roq.plugin.aliases.deployment;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.roq.util.PathUtils.prefixWithSlash;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.TemplateLink;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.plugin.aliases.deployment.items.RoqFrontMatterAliasesBuildItem;
import io.quarkiverse.roq.plugin.aliases.runtime.RoqFrontMatterAliasesRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqPluginAliasesProcessor {

    private static final String FEATURE = "roq-plugin-aliases";
    private static final Set<String> ALIASES_FOR_REDIRECTING = Set.of("redirect_from", "redirect-from", "aliases");

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
                        item.raw().info(), item.raw().collectionId(), item.data()));
                aliasMap.put(aliasLink, url.absolute());
            }
        }

        for (Map.Entry<String, String> alias : aliasMap.entrySet()) {
            aliasesProducer.produce(new RoqFrontMatterAliasesBuildItem(alias.getKey(), alias.getValue()));
            selectedPathsProducer.produce(new SelectedPathBuildItem(
                    addTrailingSlash(alias.getKey()), null));
            notFoundPageDisplayableEndpointProducer.produce(
                    new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(alias.getKey()),
                            "Roq URL alias for " + alias.getValue() + " URL."));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createVertxRedirects(
            RoqFrontMatterAliasesRecorder recorder,
            BuildProducer<RouteBuildItem> routes,
            List<RoqFrontMatterAliasesBuildItem> aliases) {
        for (RoqFrontMatterAliasesBuildItem item : aliases) {
            routes.produce(RouteBuildItem.builder()
                    .route(prefixWithSlash(item.alias()))
                    .handler(recorder.sendRedirectPage(item.target()))
                    .build());
        }
    }

    private Set<String> getAliases(JsonObject json) {
        HashSet<String> aliases = new HashSet<>();
        for (String aliasesKey : ALIASES_FOR_REDIRECTING) {
            JsonArray jsonArray = json.getJsonArray(aliasesKey);
            if (jsonArray == null) {
                continue;
            }
            for (int i = 0; i < jsonArray.size(); i++) {
                String alias = jsonArray.getString(i);
                if (!alias.isBlank()) {
                    aliases.add(alias);
                }
            }
        }
        return aliases;
    }
}
