package io.quarkiverse.roq.plugin.sitemap.deployment;

import static io.quarkiverse.roq.plugin.sitemap.runtime.RoqPluginSitemapTemplateExtension.LAST_MODIFIED_AT;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPluginException;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.plugin.sitemap.runtime.RoqPluginSitemapTemplateExtension;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class RoqPluginSitemapProcessor {
    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterScanProcessor.class);
    private static final String FEATURE = "roq-plugin-sitemap";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem process() {
        return new AdditionalBeanBuildItem(RoqPluginSitemapTemplateExtension.class);
    }

    @BuildStep
    RoqFrontMatterDataModificationBuildItem addLastModifiedData(RoqSiteConfig config, LaunchModeBuildItem launchMode) {
        return new RoqFrontMatterDataModificationBuildItem((source) -> {
            var fm = source.fm();
            if (!fm.containsKey(LAST_MODIFIED_AT)) {
                try {
                    FileRepositoryBuilder builder = new FileRepositoryBuilder();
                    Repository repository = builder
                            .readEnvironment()
                            .findGitDir()
                            .build();
                    Path repoDir = repository.getDirectory().toPath().getParent();

                    if (source.path().startsWith(repoDir)) {
                        try (Git git = new Git(repository)) {
                            Iterable<RevCommit> logs = git.log().addPath(repoDir.relativize(source.path()).toString()).call();
                            RevCommit latestCommit = logs.iterator().next();
                            if (latestCommit != null) {
                                ZonedDateTime lastModifiedDate = ZonedDateTime.ofInstant(
                                        Instant.ofEpochSecond(latestCommit.getCommitTime()),
                                        config.timeZone().isPresent() ? ZoneId.of(config.timeZone().get())
                                                : ZoneId.systemDefault());
                                String lastModifiedString = lastModifiedDate.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
                                fm.put(LAST_MODIFIED_AT, lastModifiedString);
                            }
                        }
                    }

                } catch (Exception e) {
                    if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
                        LOGGER.warnf(e, "Error while reading git last commit date for file: %s", source.path());
                    } else {
                        throw new RoqPluginException(
                                "Error while reading git last commit date for file: %s".formatted(source.path()), e);
                    }
                }
            }
            return fm;
        });
    }

}
