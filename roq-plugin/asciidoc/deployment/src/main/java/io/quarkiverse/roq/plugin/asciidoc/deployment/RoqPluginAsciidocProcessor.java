package io.quarkiverse.roq.plugin.asciidoc.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginAsciidocProcessor {

    private static final String FEATURE = "roq-plugin-asciidoc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

}
