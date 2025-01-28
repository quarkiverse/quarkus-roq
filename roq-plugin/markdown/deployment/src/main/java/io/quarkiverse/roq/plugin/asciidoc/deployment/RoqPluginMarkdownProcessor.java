package io.quarkiverse.roq.plugin.markdown.deployment;

import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginMarkdownProcessor {

    private static final String FEATURE = "roq-plugin-markdown";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem(Set.of("md", "markdown"),
                new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection("{#markdown}", "{/markdown}"));
    }

}
