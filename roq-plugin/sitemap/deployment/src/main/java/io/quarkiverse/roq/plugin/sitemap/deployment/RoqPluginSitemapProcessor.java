package io.quarkiverse.roq.plugin.sitemap.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LAST_MODIFIED_AT;
import static io.quarkiverse.roq.plugin.sitemap.runtime.RoqSitemapKeys.SITEMAP;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPluginException;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
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

    // Not a real git repo, or a bare one with no working tree: use as a sentinel repoDir that no real page
    // path can ever start with, so lookups correctly come back empty instead of matching by accident.
    private static final Path NO_WORK_TREE = Path.of(".").toAbsolutePath().normalize();

    // Keyed by work tree + HEAD so the walk survives dev-mode reloads, only rerunning when HEAD actually
    // moves, and so two different repositories never share a cached result even if their HEAD collides.
    private static CachedIndex cachedIndex;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem process() {
        return new AdditionalBeanBuildItem(RoqPluginSitemapTemplateExtension.class);
    }

    @BuildStep
    RoqFrontMatterDataModificationBuildItem addLastModifiedData(final RoqSiteConfig config,
            final LaunchModeBuildItem launchMode) {
        LastModifiedIndex index;
        try {
            index = resolveIndex();
        } catch (final IOException e) {
            if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
                LOGGER.warnf(e, "Error while reading git history for last-modified-at; pages will be missing it");
                index = null;
            } else {
                throw new RoqPluginException(
                        RoqException.builder("Sitemap plugin error")
                                .detail("Error while reading git history for last-modified-at")
                                .cause(e));
            }
        }
        final LastModifiedIndex resolvedIndex = index;

        return new RoqFrontMatterDataModificationBuildItem(source -> {
            final var fm = source.fm();
            // Layouts/partials never appear in a sitemap, and a page can opt out with sitemap: false.
            if (resolvedIndex != null && source.isPage() && fm.getBoolean(SITEMAP, true)
                    && !fm.containsKey(LAST_MODIFIED_AT)) {
                final Instant commitTime = resolvedIndex.lastModified(source.path());
                if (commitTime != null) {
                    final ZonedDateTime lastModifiedDate = ZonedDateTime.ofInstant(commitTime,
                            config.timeZone().isPresent() ? ZoneId.of(config.timeZone().get())
                                    : ZoneId.systemDefault());
                    final var lastModifiedString = lastModifiedDate.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
                    fm.put(LAST_MODIFIED_AT, lastModifiedString);
                }
            }
            return fm;
        });
    }

    /**
     * Returns the cached index if HEAD hasn't moved since it was built, otherwise rebuilds it.
     */
    private static LastModifiedIndex resolveIndex() throws IOException {
        final FileRepositoryBuilder builder = new FileRepositoryBuilder().readEnvironment().findGitDir();
        if (builder.getGitDir() == null) {
            // Not inside a git repository (e.g. a freshly scaffolded site): no history to index.
            return new LastModifiedIndex(NO_WORK_TREE, Map.of());
        }
        try (Repository repository = builder.build()) {
            return resolveIndexFor(repository);
        }
    }

    static synchronized LastModifiedIndex resolveIndexFor(final Repository repository) throws IOException {
        final Path repoDir = workTreeOf(repository);
        if (repoDir == null) {
            // Bare repository: no working tree, so no page path can ever live under it.
            return new LastModifiedIndex(NO_WORK_TREE, Map.of());
        }
        final ObjectId head = repository.resolve("HEAD");
        if (cachedIndex == null || !cachedIndex.matches(repoDir, head)) {
            cachedIndex = new CachedIndex(repoDir, head, buildIndex(repository, head, repoDir));
        }
        return cachedIndex.index();
    }

    static LastModifiedIndex buildIndex(final Repository repository, final ObjectId head) throws IOException {
        final Path repoDir = workTreeOf(repository);
        return buildIndex(repository, head, repoDir != null ? repoDir : NO_WORK_TREE);
    }

    // Linked worktrees (`git worktree add`) have their own working directory, but getDirectory() there
    // resolves to the *main* repo's `.git/worktrees/<name>` -- nowhere near where content files actually
    // live. getWorkTree() is the one that's always correct, for both regular and linked-worktree repos.
    private static Path workTreeOf(final Repository repository) {
        try {
            return repository.getWorkTree().toPath();
        } catch (final NoWorkTreeException e) {
            return null;
        }
    }

    private static LastModifiedIndex buildIndex(final Repository repository, final ObjectId head, final Path repoDir)
            throws IOException {
        final Map<String, Instant> lastModifiedByPath = new HashMap<>();

        if (head == null) {
            return new LastModifiedIndex(repoDir, lastModifiedByPath);
        }
        try (RevWalk revWalk = new RevWalk(repository);
                DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(false);
            // We only read commit time and parents, never the message.
            revWalk.setRetainBody(false);
            // Like `git log`: attribute changes to the commit that made them, not to the merge that brought them
            revWalk.setRevFilter(RevFilter.NO_MERGES);
            revWalk.markStart(revWalk.parseCommit(head));

            for (final RevCommit commit : revWalk) {
                final Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());
                final List<DiffEntry> diffs = commit.getParentCount() == 0
                        ? diffFormatter.scan(null, commit.getTree())
                        : diffFormatter.scan(revWalk.parseCommit(commit.getParent(0).getId()).getTree(),
                                commit.getTree());
                for (final DiffEntry entry : diffs) {
                    final String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath()
                            : entry.getNewPath();
                    // Keep-the-later: walk order isn't newest-first, and real commit times aren't monotonic.
                    lastModifiedByPath.merge(path, commitTime, (a, b) -> a.isAfter(b) ? a : b);
                }
            }
        }
        return new LastModifiedIndex(repoDir, lastModifiedByPath);
    }

    private record CachedIndex(Path repoDir, ObjectId headSha, LastModifiedIndex index) {
        boolean matches(final Path currentRepoDir, final ObjectId currentHead) {
            return this.repoDir.equals(currentRepoDir)
                    && (this.headSha == null ? currentHead == null : this.headSha.equals(currentHead));
        }
    }

    /**
     * Every file touched in the git history, mapped to the time of the most recent commit that touched it.
     */
    record LastModifiedIndex(Path repoDir, Map<String, Instant> lastModifiedByPath) {

        Instant lastModified(final Path sourcePath) {
            if (!sourcePath.startsWith(this.repoDir)) {
                return null;
            }
            final String relative = this.repoDir.relativize(sourcePath).toString().replace('\\', '/');
            return this.lastModifiedByPath.get(relative);
        }
    }

}
