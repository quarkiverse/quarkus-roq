package io.quarkiverse.roq.plugin.qrcode.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginQrCodeProcessor {

    private static final String FEATURE = "roq-plugin-qrcode";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

}
