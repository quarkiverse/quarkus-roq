package io.quarkiverse.roq.plugin.diagram.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginDiagramProcessor {

    private static final String FEATURE = "roq-plugin-diagram";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem process() {
        return new AdditionalBeanBuildItem(RoqPluginDiagramProcessor.class);
    }

}
