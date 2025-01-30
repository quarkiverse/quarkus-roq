package io.quarkiverse.roq.plugin.qrcode.deployment;

import io.quarkiverse.roq.plugin.qrcode.runtime.QRCodeRenderer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginQrCodeProcessor {

    private static final String FEATURE = "roq-plugin-qrcode";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem process() {
        return new AdditionalBeanBuildItem(QRCodeRenderer.class);
    }

}
