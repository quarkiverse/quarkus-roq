package io.quarkiverse.roq.plugin.aliases.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.FrontMatterJsonData.mergeParents;
import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGE_LINK_TEMPLATE;
import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.LINK_KEY;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkiverse.roq.frontmatter.deployment.Link;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.plugin.aliases.deployment.items.RoqFrontMatterAliasesBuildItem;
import io.quarkiverse.roq.plugin.aliases.runtime.RoqFrontMatterAliasesRecorder;
import io.quarkiverse.roq.util.PathUtils;
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
    private static final String ALIASES_KEY = "aliases";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void consumeTemplates(RoqSiteConfig config, List<RoqFrontMatterRawTemplateBuildItem> rawTemplates,
            BuildProducer<RoqFrontMatterAliasesBuildItem> aliasesProducer,
            BuildProducer<SelectedPathBuildItem> selectedPathsProducer,
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpointProducer) {

        if (rawTemplates.isEmpty()) {
            return;
        }

        final var byKey = rawTemplates.stream()
                .collect(Collectors.toMap(RoqFrontMatterRawTemplateBuildItem::id, Function.identity()));

        HashMap<String, String> aliasMap = new HashMap<>();
        for (RoqFrontMatterRawTemplateBuildItem item : rawTemplates) {

            final JsonObject data = mergeParents(item, byKey);

            Set<String> aliasesName = getAliases(data);
            if (aliasesName.isEmpty()) {
                continue;
            }

            Link.PageLinkData pageLinkData = new Link.PageLinkData(item.info().baseFileName(), item.info().date(),
                    item.collection(), data);
            final String targetLink = Link.pageLink(config.rootPath(), data.getString(LINK_KEY, DEFAULT_PAGE_LINK_TEMPLATE),
                    pageLinkData);

            for (String alias : aliasesName) {
                String aliasLink = Link.pageLink(config.rootPath(), alias, pageLinkData);
                aliasMap.put(aliasLink, targetLink);
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
    public void createVertxRedirects(RoqFrontMatterAliasesRecorder recorder,
            BuildProducer<RouteBuildItem> routes,
            List<RoqFrontMatterAliasesBuildItem> aliases) {
        for (RoqFrontMatterAliasesBuildItem item : aliases) {
            routes.produce(RouteBuildItem.builder()
                    .route(PathUtils.prefixWithSlash(item.alias()))
                    .handler(recorder.addRedirect(item.target()))
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
