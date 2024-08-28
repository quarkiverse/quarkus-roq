package io.quarkiverse.roq.deployment;

import java.io.IOException;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.config.RoqConfig;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class RoqProjectProcessor {

    private static final Logger LOG = Logger.getLogger(RoqProjectProcessor.class);

    @BuildStep
    RoqProjectBuildItem findProject(RoqConfig config, OutputTargetBuildItem outputTarget,
            CurateOutcomeBuildItem curateOutcome) {
        String resourceSiteDir;
        try {
            final boolean hasResourceSiteDir = Thread.currentThread().getContextClassLoader()
                    .getResources(config.siteDir()).hasMoreElements();
            resourceSiteDir = hasResourceSiteDir ? config.siteDir() : null;
        } catch (IOException e) {
            resourceSiteDir = null;
        }
        final RoqProjectBuildItem roqProject = new RoqProjectBuildItem(resourceSiteDir);
        if (!roqProject.isActive()) {
            LOG.warn("Not Roq site directory found. It is recommended to remove the quarkus-roq extension if not used.");
        }
        return roqProject;
    }

}
