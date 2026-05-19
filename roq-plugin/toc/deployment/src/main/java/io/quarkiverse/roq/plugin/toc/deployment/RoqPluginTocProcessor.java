package io.quarkiverse.roq.plugin.toc.deployment;

import io.quarkiverse.roq.plugin.toc.runtime.RoqPluginTocTemplateExtension;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginTocProcessor {

    private static final String FEATURE = "roq-plugin-toc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem process() {
        return new AdditionalBeanBuildItem(RoqPluginTocTemplateExtension.class);
    }
}
