package io.quarkiverse.roq.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.nio.file.Path;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.config.RoqConfig;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem.RoqLocalDir;
import io.quarkiverse.tools.projectscanner.ProjectRootBuildItem;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqProjectProcessor {

    private static final Logger LOG = Logger.getLogger(RoqProjectProcessor.class);

    @BuildStep
    RoqProjectBuildItem findProject(RoqConfig config, ProjectRootBuildItem projectRoot) {
        final RoqLocalDir project = resolveProjectDirs(config, projectRoot);

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
            LOG.warn("Roq site directory not found. It is recommended to remove the quarkus-roq extension if not used.");
        }
        return roqProject;
    }

    private static RoqLocalDir resolveProjectDirs(RoqConfig config,
            ProjectRootBuildItem projectRoot) {
        final String configuredDir = toUnixPath(config.dir().trim());
        if (configuredDir.isEmpty()) {
            return new RoqLocalDir(projectRoot.path(), projectRoot.path());
        }
        final Path siteRoot = projectRoot.resolveSubDir(configuredDir);
        if (siteRoot == null) {
            return null;
        }
        return new RoqLocalDir(projectRoot.path(), siteRoot);
    }

}
