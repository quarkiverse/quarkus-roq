package io.quarkiverse.roq.plugin.lunr.deployment;

import io.quarkiverse.roq.plugin.lunr.runtime.RoqPluginLunrTemplateExtension;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginLunrProcessor {
    private static final String FEATURE = "roq-plugin-lunr";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem process() {
        return new AdditionalBeanBuildItem(
                RoqPluginLunrTemplateExtension.class);
    }

}
