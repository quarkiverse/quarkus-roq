package io.quarkiverse.roq.plugin.l10n.asciidoc.deployment;

import io.quarkiverse.roq.plugin.l10n.asciidoc.L10nAdocConfig;
import io.quarkiverse.roq.plugin.l10n.asciidoc.L10nAdocRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginL10nAdocProcessor {

    private static final String FEATURE = "roq-plugin-l10n-adoc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void configureL10n(L10nAdocConfig config, L10nAdocRecorder recorder) {
        recorder.setPoBaseDir(config.poBaseDir());
    }
}
