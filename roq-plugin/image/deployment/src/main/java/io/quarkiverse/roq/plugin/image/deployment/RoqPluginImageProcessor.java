package io.quarkiverse.roq.plugin.image.deployment;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.plugin.image.runtime.RoqImageCustomizer;
import io.quarkiverse.roq.plugin.image.runtime.RoqRuntimeImageListener;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginImageProcessor {

    private static final String FEATURE = "roq-plugin-image";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqRuntimeImageListener.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqImageCustomizer.class));
    }

    @BuildStep
    void registerImageDirs(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<ImagesDirBuildItem> imageDirProducer) {
        if (!roqProject.isActive()) {
            return;
        }
        // Site-level originals: content/_images/ → indexed as images/
        String originalsDir = config.contentDir() + "/" + ImageUtils.IMAGES_DIR;
        addLocalImageDir(imageDirProducer, roqProject, originalsDir, config.imagesPath());

        // Classpath originals (themes)
        String classpathOriginals = roqProject.isRoqResourcesInRoot()
                ? originalsDir
                : roqProject.resolveRoqResourceSubDir(originalsDir);
        imageDirProducer.produce(ImagesDirBuildItem.of(classpathOriginals, config.imagesPath()));

        // Public and static dirs (for images already in public/)
        addLocalImageDir(imageDirProducer, roqProject, config.publicDir(), config.publicDir());
        addLocalImageDir(imageDirProducer, roqProject, config.staticDir(), config.staticDir());

        imageDirProducer.produce(ImagesDirBuildItem.of(roqProject.isRoqResourcesInRoot()
                ? config.publicDir()
                : roqProject.resolveRoqResourceSubDir(config.publicDir())));
        imageDirProducer.produce(ImagesDirBuildItem.of(roqProject.isRoqResourcesInRoot()
                ? config.staticDir()
                : roqProject.resolveRoqResourceSubDir(config.staticDir())));
    }

    private static void addLocalImageDir(BuildProducer<ImagesDirBuildItem> producer,
            RoqProjectBuildItem roqProject, String subDir, String prefix) {
        Path localDir = roqProject.fromLocalRoqDir(subDir);
        if (localDir != null && Files.isDirectory(localDir)) {
            producer.produce(ImagesDirBuildItem.of(prefix, localDir));
        }
    }
}
