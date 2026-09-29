package io.quarkiverse.roq.plugin.sitemap.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LAST_MODIFIED_AT;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPluginException;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedContentBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.plugin.sitemap.runtime.RoqPluginSitemapTemplateExtension;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class RoqPluginSitemapProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqPluginSitemapProcessor.class);
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
    RoqFrontMatterDataModificationBuildItem addLastModifiedData(RoqSiteConfig config, RoqPluginSitemapConfig sitemapConfig,
            LaunchModeBuildItem launchMode, List<RoqFrontMatterScannedContentBuildItem> scannedContent) {
        final ZoneId zone = config.timeZoneOrDefault();
        return switch (sitemapConfig.lastModified()) {
            case NONE -> null;
            case FS -> lastModifiedModification(zone, RoqPluginSitemapProcessor::fsLastModified);
            case GIT -> {
                // Resolve all the dates at once: a single history walk is much faster than a git log per file
                final List<Path> files = scannedContent.stream()
                        .filter(c -> !c.metadata().parsedHeaders().data().containsKey(LAST_MODIFIED_AT))
                        .map(c -> c.metadata().filePath())
                        .filter(p -> p != null && p.getFileSystem() == FileSystems.getDefault())
                        .map(RoqPluginSitemapProcessor::normalize)
                        .toList();
                final Map<Path, Instant> gitDates = gitLastModified(files, launchMode.getLaunchMode());
                yield lastModifiedModification(zone, path -> {
                    final Instant date = gitDates.get(normalize(path));
                    // Not committed yet
                    return date != null ? date : fsLastModified(path);
                });
            }
        };
    }

    private static RoqFrontMatterDataModificationBuildItem lastModifiedModification(ZoneId zone,
            Function<Path, Instant> lastModified) {
        return new RoqFrontMatterDataModificationBuildItem(source -> {
            var fm = source.fm();
            if (!source.isPage() || source.path() == null || fm.containsKey(LAST_MODIFIED_AT)) {
                return fm;
            }
            final Instant date = lastModified.apply(source.path());
            if (date != null) {
                fm.put(LAST_MODIFIED_AT,
                        ZonedDateTime.ofInstant(date, zone).format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
            }
            return fm;
        });
    }

    private static Instant fsLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException | UnsupportedOperationException e) {
            LOGGER.debugf(e, "Unable to read the last modified time of file: %s", path);
            return null;
        }
    }

    /**
     * Walk the git history once (newest first) and record, for each file, the date of the last commit touching it.
     * The walk stops as soon as every file has been found.
     */
    static Map<Path, Instant> gitLastModified(List<Path> files, LaunchMode launchMode) {
        final Map<Path, Instant> result = new HashMap<>();
        if (files.isEmpty()) {
            return result;
        }
        try {
            final FileRepositoryBuilder builder = new FileRepositoryBuilder()
                    .readEnvironment()
                    .findGitDir(files.get(0).toFile());
            if (builder.getGitDir() == null) {
                return result;
            }
            try (Repository repository = builder.build();
                    RevWalk walk = new RevWalk(repository);
                    TreeWalk treeWalk = new TreeWalk(repository)) {
                if (repository.isBare()) {
                    return result;
                }
                final ObjectId head = repository.resolve(Constants.HEAD);
                if (head == null) {
                    return result;
                }
                final Path repoDir = normalize(repository.getWorkTree().toPath());
                final Map<String, Path> remaining = new HashMap<>();
                for (Path file : files) {
                    if (file.startsWith(repoDir)) {
                        remaining.put(repoDir.relativize(file).toString().replace('\\', '/'), file);
                    }
                }
                if (remaining.isEmpty()) {
                    return result;
                }

                walk.sort(RevSort.COMMIT_TIME_DESC);
                // We only need the commit time, not the message
                walk.setRetainBody(false);
                // Like git log: the changes are attributed to the merged commits, not to the merge
                walk.setRevFilter(RevFilter.NO_MERGES);
                walk.markStart(walk.parseCommit(head));

                // Built once: already resolved files may still match, but remaining.remove() ignores them
                final TreeFilter filter = AndTreeFilter.create(PathFilterGroup.createFromStrings(remaining.keySet()),
                        TreeFilter.ANY_DIFF);
                treeWalk.setRecursive(true);
                for (RevCommit commit : walk) {
                    treeWalk.reset();
                    treeWalk.setFilter(filter);
                    if (commit.getParentCount() == 1) {
                        treeWalk.addTree(walk.parseCommit(commit.getParent(0)).getTree());
                    } else {
                        // Initial commit: diff against an empty tree to detect all the added files
                        treeWalk.addTree(new EmptyTreeIterator());
                    }
                    treeWalk.addTree(commit.getTree());
                    final Instant date = Instant.ofEpochSecond(commit.getCommitTime());
                    while (treeWalk.next()) {
                        final Path file = remaining.remove(treeWalk.getPathString());
                        if (file != null) {
                            result.put(file, date);
                        }
                    }
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (launchMode == LaunchMode.DEVELOPMENT) {
                LOGGER.warn("Error while reading git last commit dates, falling back to the file system dates", e);
            } else {
                throw new RoqPluginException(
                        RoqException.builder("Sitemap plugin error")
                                .detail("Error while reading git last commit dates")
                                .cause(e));
            }
        }
        return result;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

}
