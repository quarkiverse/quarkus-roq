package io.quarkiverse.roq.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.config.RoqConfig;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class RoqProjectProcessor {

    private static final Logger LOG = Logger.getLogger(RoqProjectProcessor.class);

    @BuildStep
    RoqProjectBuildItem findProject(RoqConfig config, OutputTargetBuildItem outputTarget) {
        final RoqProjectBuildItem.RoqProject project = resolveProjectDirs(config, outputTarget);

        String resourceDir;
        try {
            final boolean hasResourceDir = config.resourceDir().isEmpty() || Thread.currentThread().getContextClassLoader()
                    .getResources(config.resourceDir()).hasMoreElements();
            resourceDir = hasResourceDir ? config.resourceDir() : null;
        } catch (IOException e) {
            resourceDir = null;
        }
        final RoqProjectBuildItem roqProject = new RoqProjectBuildItem(project, resourceDir);
        if (!roqProject.isActive()) {
            LOG.warn("Not Roq site directory found. It is recommended to remove the quarkus-roq extension if not used.");
        }
        return roqProject;
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
    private static RoqProjectBuildItem.RoqProject resolveProjectDirs(RoqConfig config,
            OutputTargetBuildItem outputTarget) {
        Path projectRoot = findProjectRoot(outputTarget.getOutputDirectory());
        Path configuredSiteDirPath = Paths.get(config.dir().trim());
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {

            if (configuredSiteDirPath.isAbsolute() && Files.isDirectory(configuredSiteDirPath)) {
                configuredSiteDirPath = configuredSiteDirPath.normalize();
            } else {
                LOG.warn(
                        "If not absolute, the Site directory is resolved relative to the project root, but Roq was not able to find the project root.");
                return null;
            }
        }

        final Path siteRoot = Objects.requireNonNull(projectRoot).resolve(configuredSiteDirPath).normalize();

        if (!Files.isDirectory(siteRoot)) {
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