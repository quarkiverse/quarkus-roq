package io.quarkiverse.roq.deployment;

import static java.util.function.Predicate.not;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.config.RoqBuildConfig;
import io.quarkiverse.roq.deployment.config.RoqProjectDirectories;
import io.quarkiverse.roq.runtime.FixedStaticPagesProvider;
import io.quarkiverse.roq.runtime.RoqGenerator;
import io.quarkiverse.roq.runtime.RoqRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem;

class RoqProcessor {

    private static final Logger LOG = Logger.getLogger(RoqProcessor.class);
    private static final String FEATURE = "roq";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void produceBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(RoqGenerator.class));
        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(FixedStaticPagesProvider.class));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void initHandler(List<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpoints,
            StaticResourcesBuildItem staticResourcesBuildItem,
            OutputTargetBuildItem outputTarget,
            RoqRecorder recorder) {
        Set<String> staticPaths = new HashSet<>();
        if (staticResourcesBuildItem != null) {
            staticPaths.addAll(staticResourcesBuildItem.getPaths());
        }
        if (notFoundPageDisplayableEndpoints != null) {
            staticPaths.addAll(notFoundPageDisplayableEndpoints.stream()
                    .filter(not(NotFoundPageDisplayableEndpointBuildItem::isAbsolutePath))
                    .map(NotFoundPageDisplayableEndpointBuildItem::getEndpoint)
                    .toList());
        }
        recorder.setStaticPaths(staticPaths);
        recorder.setOutputTarget(outputTarget.getOutputDirectory().toAbsolutePath().toString());
    }

    /**
     * Resolves the project directories based on the provided configuration and output target.
     *
     * @param config the build configuration
     * @param outputTarget the output target build item
     * @return a {@link RoqProjectDirectories} object containing the resolved project root, site root, and data root
     *         directories, or {@code null} if the site root directory is not found
     * @throws IllegalStateException if the project root is not found and the site directory is not absolute
     */
    private static RoqProjectDirectories resolveProjectDirs(RoqBuildConfig config, OutputTargetBuildItem outputTarget) {
        Path projectRoot = RoqProjectDirectories.findProjectRoot(outputTarget.getOutputDirectory());
        Path configuredSiteDirPath = Paths.get(config.siteDir().trim());

        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            if (configuredSiteDirPath.isAbsolute() && Files.isDirectory(configuredSiteDirPath)) {
                configuredSiteDirPath = configuredSiteDirPath.normalize();
            } else {
                throw new IllegalStateException(
                        "If not absolute, the Site directory is resolved relative to the project root, but Roq was not able to find the project root.");
            }
        }

        final Path siteRoot = projectRoot.resolve(configuredSiteDirPath).normalize();

        if (!Files.isDirectory(siteRoot)) {
            LOG.warnf(
                    "Roq directory not found 'quarkus.roq.site-dir=%s' resolved to '%s'. It is recommended to remove the quarkus-roq extension if not used.",
                    config.siteDir(), siteRoot.toAbsolutePath());
            return null;
        }

        Path dataRoot = siteRoot.resolve(config.dataDir().trim()).normalize();

        if (!Files.isDirectory(dataRoot)) {
            LOG.debugf("Roq directory not found 'quarkus.roq.data-dir=%s' resolved to '%s'.", config.dataDir(),
                    dataRoot.toAbsolutePath());
            dataRoot = null;
        }

        return new RoqProjectDirectories(projectRoot, siteRoot, dataRoot);
    }

}
