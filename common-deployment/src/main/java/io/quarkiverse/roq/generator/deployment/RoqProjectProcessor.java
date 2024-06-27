package io.quarkiverse.roq.generator.deployment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.generator.deployment.config.RoqBuildConfig;
import io.quarkiverse.roq.generator.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class RoqProjectProcessor {

    private static final Logger LOG = Logger.getLogger(RoqProjectProcessor.class);

    @BuildStep
    RoqProjectBuildItem findProject(RoqBuildConfig config, OutputTargetBuildItem outputTarget) {
        return new RoqProjectBuildItem(resolveProjectDirs(config, outputTarget));
    }

    /**
     * Resolves the project directories based on the provided configuration and output target.
     *
     * @param config the build configuration
     * @param outputTarget the output target build item
     * @return a {@link RoqProjectBuildItem.RoqProject} object containing the resolved project root, site root, and data root
     *         directories, or {@code null} if the site root directory is not found
     * @throws IllegalStateException if the project root is not found and the site directory is not absolute
     */
    private static RoqProjectBuildItem.RoqProject resolveProjectDirs(RoqBuildConfig config,
            OutputTargetBuildItem outputTarget) {
        Path projectRoot = findProjectRoot(outputTarget.getOutputDirectory());
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

        return new RoqProjectBuildItem.RoqProject(projectRoot, siteRoot);
    }

    private static Path findProjectRoot(Path outputDirectory) {
        Path currentPath = outputDirectory;
        do {
            if (Files.exists(currentPath.resolve(Paths.get("src", "main")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.properties")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yaml")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yml")))) {
                return currentPath.normalize();
            }
            if (currentPath.getParent() != null && Files.exists(currentPath.getParent())) {
                currentPath = currentPath.getParent();
            } else {
                return null;
            }
        } while (true);
    }

}
