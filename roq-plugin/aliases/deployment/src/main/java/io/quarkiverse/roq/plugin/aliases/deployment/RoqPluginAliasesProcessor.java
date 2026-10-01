package io.quarkiverse.roq.plugin.aliases.deployment;

import static io.quarkiverse.roq.plugin.aliases.runtime.RoqAliasesKeys.ALIASES;
import static io.quarkiverse.roq.plugin.aliases.runtime.RoqAliasesKeys.REDIRECT_FROM;
import static io.quarkiverse.roq.plugin.aliases.runtime.RoqAliasesKeys.REDIRECT_FROM_HYPHEN;
import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlash;
import static io.quarkiverse.tools.stringpaths.StringPaths.prefixWithSlash;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeTrailingSlash;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.items.RoqPathBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink;
import io.quarkiverse.roq.plugin.aliases.deployment.items.RoqFrontMatterAliasesBuildItem;
import io.quarkiverse.roq.plugin.aliases.runtime.RoqFrontMatterAliasesRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.vertx.core.json.JsonObject;

public class RoqPluginAliasesProcessor {

    private static final String FEATURE = "roq-plugin-aliases";
    private static final Set<String> ALIASES_FOR_REDIRECTING = Set.of(REDIRECT_FROM, REDIRECT_FROM_HYPHEN, ALIASES);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    // Collect the aliases declared in the front matter and register a redirect for each of them.
    // Each alias claims its path (RoqPathBuildItem), so an alias colliding with a page, a static file
    // or an alias of another page is reported by the frontmatter path check.
    @BuildStep
    public void consumeTemplates(
            RoqSiteConfig config,
            List<RoqFrontMatterPageTemplateBuildItem> templates,
            BuildProducer<RoqFrontMatterAliasesBuildItem> aliasesProducer,
            BuildProducer<RoqPathBuildItem> pathProducer,
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpointProducer) {

        if (templates.isEmpty()) {
            return;
        }

        for (RoqFrontMatterPageTemplateBuildItem item : templates) {

            Set<String> aliasesName = getAliases(item.data());
            if (aliasesName.isEmpty()) {
                continue;
            }
            RoqUrl url = item.url();
            // The same page may declare the same alias twice (e.g. in 'aliases' and 'redirect_from')
            final Set<String> aliasLinks = new HashSet<>();
            for (String alias : aliasesName) {
                String aliasLink = TemplateLink.pageLink(config.pathPrefixOrEmpty(), alias, new TemplateLink.PageLinkData(
                        item.source(), item.raw().collectionId(), item.data()));
                if (!aliasLinks.add(aliasLink)) {
                    continue;
                }
                aliasesProducer.produce(new RoqFrontMatterAliasesBuildItem(aliasLink, url.absolute()));
                pathProducer.produce(new RoqPathBuildItem(aliasLink,
                        "alias '%s' of page '%s'".formatted(alias, item.source().id())));
                notFoundPageDisplayableEndpointProducer.produce(
                        new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(aliasLink),
                                "Roq URL alias for " + url.absolute() + " URL."));
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createVertxRedirects(
            RoqFrontMatterAliasesRecorder recorder,
            BuildProducer<RouteBuildItem> routes,
            List<RoqFrontMatterAliasesBuildItem> aliases) {
        for (RoqFrontMatterAliasesBuildItem item : aliases) {
            String withSlash = prefixWithSlash(addTrailingSlash(item.alias()));
            String withoutSlash = prefixWithSlash(removeTrailingSlash(item.alias()));
            routes.produce(RouteBuildItem.builder()
                    .route(withSlash)
                    .handler(recorder.sendRedirectPage(item.target()))
                    .build());
            routes.produce(RouteBuildItem.builder()
                    .route(withoutSlash)
                    .handler(recorder.sendRedirectPage(item.target()))
                    .build());
        }
    }

    private Set<String> getAliases(JsonObject json) {
        HashSet<String> aliases = new HashSet<>();
        for (String aliasesKey : ALIASES_FOR_REDIRECTING) {
            final List<String> array = RoqTemplateExtension.asStrings(json.getValue(aliasesKey));
            for (int i = 0; i < array.size(); i++) {
                String alias = array.get(i);
                if (!alias.isBlank()) {
                    aliases.add(alias);
                }
            }
        }
        return aliases;
    }
}
