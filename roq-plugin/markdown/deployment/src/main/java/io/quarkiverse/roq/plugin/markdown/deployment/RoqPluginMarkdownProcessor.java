package io.quarkiverse.roq.plugin.markdown.deployment;

import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginMarkdownProcessor {
    private static final Set<String> APPLICABLE_EXTENSIONS = Set.of("md", "markdown");
    private static final String FEATURE = "roq-plugin-markdown";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem("markdown", c -> APPLICABLE_EXTENSIONS.contains(c.getExtension()),
                new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection("{#markdown}", "{/markdown}"));
    }

}
