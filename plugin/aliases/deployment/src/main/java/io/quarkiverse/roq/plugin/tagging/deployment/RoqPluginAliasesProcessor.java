package io.quarkiverse.roq.plugin.tagging.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginAliasesProcessor {

    private static final String FEATURE = "roq-plugin-aliases";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

}
