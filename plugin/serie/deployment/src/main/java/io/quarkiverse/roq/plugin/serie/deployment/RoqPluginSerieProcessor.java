package io.quarkiverse.roq.plugin.serie.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.plugin.series.runtime.SerieMessage;
import io.quarkiverse.roq.plugin.series.runtime.Series;
import io.quarkiverse.roq.plugin.series.runtime.SeriesRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class RoqPluginSerieProcessor {

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
                .addBeanClasses(SerieMessage.class)
                .setUnremovable().build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(Series.Serie.class).serialization()
                .constructors().fields().methods().build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(Series.Serie.Entry.class).serialization()
                .constructors().fields().methods().build());
    }

    @BuildStep
    @Record(value = STATIC_INIT)
    void generateSeries(
            SeriesRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqFrontMatterRawTemplateBuildItem> templates) {
        Map<String, List<String>> serieTemplates = templates.stream()
                .filter(Predicate.not(RoqFrontMatterRawTemplateBuildItem::isLayout))
                .filter(item -> item.data().containsKey("serie"))
                .collect(Collectors.toMap(
                        item -> item.data().getString("serie"),
                        item -> new ArrayList<>(List.of(item.data().getString("title"))),
                        (a, b) -> {
                            a.addAll(b);
                            return a;
                        }));
        if (serieTemplates.isEmpty()) {
            return;
        }
        beansProducer.produce(SyntheticBeanBuildItem.configure(Series.class)
                .named("series")
                .scope(Singleton.class)
                .unremovable()
                .runtimeValue(recorder.generateSeries(serieTemplates))
                .done());
    }

}
