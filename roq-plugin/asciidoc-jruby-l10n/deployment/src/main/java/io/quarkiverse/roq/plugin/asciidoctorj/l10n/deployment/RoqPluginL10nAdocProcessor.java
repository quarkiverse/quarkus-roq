package io.quarkiverse.roq.plugin.asciidoctorj.l10n.deployment;

import io.quarkiverse.roq.plugin.asciidoctorj.l10n.L10nAdocConfig;
import io.quarkiverse.roq.plugin.asciidoctorj.l10n.L10nAdocRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginL10nAdocProcessor {

    private static final String FEATURE = "roq-plugin-asciidoc-jruby-l10n";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void configureL10n(L10nAdocConfig config, L10nAdocRecorder recorder) {
        recorder.setPoBaseDir(config.poBaseDir());
        recorder.setExtractOnBuild(config.extractOnBuild());
        recorder.setTargetLanguage(config.targetLanguage());
    }
}
