package io.quarkiverse.roq.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkiverse.roq.deployment.config.RoqConfig;
import io.quarkiverse.roq.deployment.config.RoqJacksonConfig;
import io.quarkiverse.roq.deployment.items.RoqObjectMapperBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class RoqProjectProcessor {

    private static final Logger LOG = Logger.getLogger(RoqProjectProcessor.class);

    @BuildStep
    RoqProjectBuildItem findProject(RoqConfig config, OutputTargetBuildItem outputTarget,
            CurateOutcomeBuildItem curateOutcome) {
        final RoqProjectBuildItem.RoqProject project = resolveProjectDirs(config, curateOutcome, outputTarget);

        String resourceSiteDir;
        try {
            final boolean hasResourceSiteDir = Thread.currentThread().getContextClassLoader()
                    .getResources(config.resourceSiteDir()).hasMoreElements();
            resourceSiteDir = hasResourceSiteDir ? config.resourceSiteDir() : null;
        } catch (IOException e) {
            resourceSiteDir = null;
        }
        final RoqProjectBuildItem roqProject = new RoqProjectBuildItem(project, resourceSiteDir);
        if (!roqProject.isActive()) {
            LOG.warn("Not Roq site directory found. It is recommended to remove the quarkus-roq extension if not used.");
        }
        return roqProject;
    }

    @BuildStep
    RoqObjectMapperBuildItem findProject(RoqJacksonConfig jacksonConfig) {
        ObjectMapper objectMapper = new ObjectMapper();
        if (!jacksonConfig.failOnUnknownProperties()) {
            // this feature is enabled by default, so we disable it
            objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
        if (!jacksonConfig.failOnEmptyBeans()) {
            // this feature is enabled by default, so we disable it
            objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        }
        if (jacksonConfig.acceptCaseInsensitiveEnums()) {
            objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        }
        return new RoqObjectMapperBuildItem(objectMapper);
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
            CurateOutcomeBuildItem curateOutcome,
            OutputTargetBuildItem outputTarget) {
        Path projectRoot = findProjectRoot(outputTarget.getOutputDirectory());
        Path configuredSiteDirPath = Paths.get(config.siteDir().trim());
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {

            if (configuredSiteDirPath.isAbsolute() && Files.isDirectory(configuredSiteDirPath)) {
                configuredSiteDirPath = configuredSiteDirPath.normalize();
            } else {
                LOG.warn(
                        "If not absolute, the Site directory is resolved relative to the project root, but Roq was not able to find the project root..");
                return null;
            }
        }

        final Path siteRoot = projectRoot.resolve(configuredSiteDirPath).normalize();

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
