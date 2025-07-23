package io.quarkiverse.roq.plugin.asciidoc.deployment;

import java.util.Set;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.plugin.asciidoc.runtime.AsciidocConfig;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginAsciidocProcessor {

    private static final Set<String> APPLICABLE_EXTENSIONS = Set.of("adoc", "asciidoc");

    private static final String FEATURE = "roq-plugin-asciidoc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RoqFrontMatterQuteMarkupBuildItem markup() {
        return new RoqFrontMatterQuteMarkupBuildItem("asciidoc", c -> APPLICABLE_EXTENSIONS.contains(c.getExtension()),
                new RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection("{#asciidoc}", "{/asciidoc}"));
    }

    @BuildStep
    RoqFrontMatterHeaderParserBuildItem header(AsciidocConfig config) {
        return AsciidocHeaderParser.createBuildItem(config.escape(), c -> APPLICABLE_EXTENSIONS.contains(c.getExtension()));
    }

}
