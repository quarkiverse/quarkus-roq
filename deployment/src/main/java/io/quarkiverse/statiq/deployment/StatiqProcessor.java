package io.quarkiverse.statiq.deployment;

import static java.util.function.Predicate.not;

import java.nio.file.Path;
import java.util.*;

import jakarta.inject.Singleton;

import io.quarkiverse.statiq.runtime.FixedStaticPagesProvider;
import io.quarkiverse.statiq.runtime.StatiqGenerator;
import io.quarkiverse.statiq.runtime.StatiqGeneratorConfig;
import io.quarkiverse.statiq.runtime.StatiqRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem;

class StatiqProcessor {

    private static final String FEATURE = "statiq";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    SyntheticBeanBuildItem produceGeneratorConfig(StatiqConfig config, OutputTargetBuildItem outputTarget,
            StatiqRecorder recorder) {
        final Path outputDir = outputTarget.getOutputDirectory().resolve(config.outputDir());
        return SyntheticBeanBuildItem.configure(StatiqGeneratorConfig.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.createGeneratorConfig(config.fixed().orElse(List.of()),
                        outputDir.toAbsolutePath().toString()))
                .done();
    }

    @BuildStep
    void produceBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(StatiqGenerator.class));
        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(FixedStaticPagesProvider.class));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void initHandler(List<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpoints,
            StaticResourcesBuildItem staticResourcesBuildItem,
            StatiqRecorder recorder) {

        Set<String> staticPaths = new HashSet<>(staticResourcesBuildItem.getPaths());
        staticPaths.addAll(notFoundPageDisplayableEndpoints.stream()
                .filter(not(NotFoundPageDisplayableEndpointBuildItem::isAbsolutePath))
                .map(NotFoundPageDisplayableEndpointBuildItem::getEndpoint)
                .toList());
        recorder.setStatiqPages(staticPaths);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void initHandler(BuildProducer<RouteBuildItem> routes,
            NonApplicationRootPathBuildItem nonApplicationRootPath,
            StatiqRecorder recorder) {

        final RouteBuildItem route = nonApplicationRootPath.routeBuilder()
                .management()
                .blockingRoute()
                .route("statiq/generate")
                .handler(recorder.createGenerateHandler())
                .build();

        routes.produce(route);

    }

}
