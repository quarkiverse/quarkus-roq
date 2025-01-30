package io.quarkiverse.roq.plugin.asciidoc.deployment;

import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginAsciidocProcessor {

    private static final String FEATURE = "roq-plugin-asciidoc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem(Set.of("adoc", "asciidoc"),
                new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection("{#asciidoc}", "{/asciidoc}"));
    }

}
