package io.quarkiverse.roq.plugin.diagram.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginDiagramProcessor {

    public static final String FEATURE = "roq-plugin-diagram";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

}
