package io.quarkiverse.roq.plugin.series.deployment;

import static io.quarkiverse.roq.plugin.series.runtime.Series.FM_SERIE;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDocumentTemplateBuildItem;
import io.quarkiverse.roq.plugin.series.runtime.Series;
import io.quarkiverse.roq.plugin.series.runtime.SeriesMessage;
import io.quarkiverse.roq.plugin.series.runtime.SeriesRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class RoqPluginSeriesProcessor {

    private static final String FEATURE = "roq-plugin-series";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerAdditionalBeans(RoqFrontMatterOutputBuildItem roqOutput,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        if (roqOutput == null) {
            return;
        }
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(SeriesMessage.class)
                .setUnremovable().build());
        reflectiveClass.produce(
                ReflectiveClassBuildItem.builder(Series.class).serialization().constructors().fields().methods().build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(Series.SeriesEntry.class).serialization()
                .constructors().fields().methods().build());
    }

    @BuildStep
    @Record(value = STATIC_INIT)
    void generateSeries(
            SeriesRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqFrontMatterDocumentTemplateBuildItem> documents) {
        // Currently, we don't enforce series to be on the same collection, should we?
        Map<String, List<String>> series = documents.stream()
                .filter(item -> item.data().containsKey(FM_SERIE))
                .collect(Collectors.toMap(
                        item -> item.data().getString(FM_SERIE),
                        item -> new ArrayList<>(List.of(item.raw().id())),
                        (a, b) -> {
                            a.addAll(b);
                            return a;
                        }));

        if (series.isEmpty()) {
            return;
        }
        beansProducer.produce(SyntheticBeanBuildItem.configure(Series.class)
                .named("series")
                .scope(Singleton.class)
                .unremovable()
                .runtimeValue(recorder.generateSeries(series))
                .done());
    }

}
