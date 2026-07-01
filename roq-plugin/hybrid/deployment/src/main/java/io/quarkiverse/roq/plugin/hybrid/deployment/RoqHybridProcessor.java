package io.quarkiverse.roq.plugin.hybrid.deployment;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterIncludeFuturePagesBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.plugin.hybrid.runtime.RoqCacheManager;
import io.quarkiverse.roq.plugin.hybrid.runtime.RoqCachingRenderer;
import io.quarkiverse.roq.plugin.hybrid.runtime.RoqHybridRecorder;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;

class RoqHybridProcessor {

    private static final String FEATURE = "roq-hybrid";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RoqFrontMatterIncludeFuturePagesBuildItem includeFuturePages() {
        return new RoqFrontMatterIncludeFuturePagesBuildItem();
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqCachingRenderer.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqCacheManager.class));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    RouteBuildItem produceFilterRoute(RoqSiteConfig siteConfig,
            RoqHybridRecorder recorder,
            HttpRootPathBuildItem httpRootPath,
            RoqFrontMatterOutputBuildItem roqFrontMatterOutput) {
        if (roqFrontMatterOutput == null || roqFrontMatterOutput.allPagesByPath().isEmpty()) {
            return null;
        }
        recorder.startupCache(roqFrontMatterOutput.allPagesByPath());
        String routePath = httpRootPath.relativePath(StringPaths.join(siteConfig.pathPrefixOrEmpty(), "/*"));
        return httpRootPath.routeBuilder()
                .routeFunction(routePath, recorder.initializeFilterRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.hybridFilter())
                .build();
    }
}
