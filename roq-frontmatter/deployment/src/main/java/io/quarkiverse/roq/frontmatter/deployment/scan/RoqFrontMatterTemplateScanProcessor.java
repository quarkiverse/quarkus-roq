package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.*;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterProcessor;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplateRootBuildItem;
import io.quarkus.qute.runtime.QuteConfig;

public class RoqFrontMatterTemplateScanProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterTemplateScanProcessor.class);

    @BuildStep
    void scanTemplates(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            RoqSiteConfig config,
            QuteConfig quteConfig,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            BuildProducer<TemplateRootBuildItem> templateRootProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch) throws IOException {
        if (!roqProject.isActive()) {
            return;
        }

        // When roq resources are not at classpath root, register template root
        if (!roqProject.isRoqResourcesInRoot()) {
            templateRootProducer.produce(new TemplateRootBuildItem(
                    StringPaths.join(roqProject.roqResourceDir(), TEMPLATES_DIR)));
        }

        List<ProjectFile> files = scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .matching(buildTemplateGlob(quteConfig))
                .exclude("glob:" + LAYOUTS_DIR + "/**")
                .exclude("glob:" + THEME_LAYOUTS_DIR_PREFIX + "**")
                .addExcluded(buildIgnoredPatterns(config))
                .list();

        for (ProjectFile file : files) {
            LOGGER.debugf("Roq template scan found in local dir: scopedPath=%s, path=%s",
                    file.scopedPath(), file.origin(), file.path());
            String link = toUnixPath(file.scopedPath());
            String content = new String(file.content(), file.charset());

            if (content.length() > 65535) {
                LOGGER.warnf(
                        "Template '%s' is too large for recording and will be ignored. Consider splitting it into smaller parts.",
                        link);
                continue;
            }

            generatedResourceProducer
                    .produce(new GeneratedResourceBuildItem(
                            "templates/" + link,
                            content.getBytes(StandardCharsets.UTF_8)));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem("templates/" + link));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .priority(ROOT_ARCHIVE_PRIORITY)
                    .path(link)
                    .fullPath(file.path())
                    .content(content)
                    .extensionInfo(RoqFrontMatterProcessor.FEATURE)
                    .build());

            String watchPath = file.watchPath();
            if (watchPath != null) {
                watch.produce(HotDeploymentWatchedFileBuildItem.builder()
                        .setLocation(watchPath).build());
            }
        }
    }
}
