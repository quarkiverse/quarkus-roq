package io.quarkiverse.roq.plugin.sitemap.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.runtime.LaunchMode;

/**
 * Fixture: JGit repositories created in a temp directory, with fixed commit dates.
 * <p>
 * Features tested: the single history walk used for {@code quarkus.roq.sitemap.last-modified=git} returns, for each
 * file, the committer date of the last non-merge commit touching it (a rename or a re-add counts as a change). It
 * returns nothing for uncommitted files, files outside the repository, a repository without commits, or a directory
 * outside any repository. A shallow clone logs a warning when files get the date of its oldest fetched commit.
 */
public class RoqPluginSitemapGitLastModifiedTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-02-01T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T4 = Instant.parse("2026-04-01T10:00:00Z");

    @TempDir
    Path dir;

    @Test
    void resolvesTheLastCommitDateOfEachFile() throws Exception {
        final Path a = dir.resolve("content/a.md");
        final Path b = dir.resolve("content/posts/b.md");
        final Path c = dir.resolve("content/c.md");
        final Path untracked = dir.resolve("content/untracked.md");

        try (Git git = init(dir)) {
            write(a, "a1");
            write(b, "b1");
            commit(git, T1);
            write(a, "a2");
            write(c, "c1");
            commit(git, T2);
            write(c, "c2");
            commit(git, T3);
        }
        write(untracked, "u");

        assertThat(gitLastModified(a, b, c, untracked))
                .containsEntry(normalize(a), T2)
                .containsEntry(normalize(b), T1)
                .containsEntry(normalize(c), T3)
                .doesNotContainKey(normalize(untracked));
    }

    @Test
    void attributesMergedChangesToTheOriginatingCommit() throws Exception {
        final Path a = dir.resolve("content/a.md");
        final Path b = dir.resolve("content/b.md");

        try (Git git = init(dir)) {
            write(a, "a1");
            write(b, "b1");
            commit(git, T1);
            git.checkout().setCreateBranch(true).setName("feature").call();
            write(a, "a2");
            commit(git, T2);
            git.checkout().setName("main").call();
            write(b, "b2");
            commit(git, T3);
            git.merge().include(git.getRepository().resolve("feature"))
                    .setFastForward(FastForwardMode.NO_FF).setCommit(false).call();
            // Committed separately to control the merge commit time
            commit(git, T4);
            assertThat(git.log().setMaxCount(1).call().iterator().next().getParentCount())
                    .as("a merge commit")
                    .isEqualTo(2);
        }

        assertThat(gitLastModified(a, b))
                .as("like git log, the merge itself is skipped")
                .containsEntry(normalize(a), T2)
                .containsEntry(normalize(b), T3);
    }

    @Test
    void usesTheCommitterDateNotTheAuthorDate() throws Exception {
        final Path a = dir.resolve("content/a.md");

        try (Git git = init(dir)) {
            write(a, "a1");
            // e.g. a rebased or cherry-picked commit
            commit(git, T1, T3);
        }

        assertThat(gitLastModified(a)).containsEntry(normalize(a), T3);
    }

    @Test
    void usesTheReAddDateOfADeletedThenReAddedFile() throws Exception {
        final Path a = dir.resolve("content/a.md");
        final Path b = dir.resolve("content/b.md");

        try (Git git = init(dir)) {
            write(a, "a1");
            write(b, "b1");
            commit(git, T1);
            Files.delete(a);
            write(b, "b2");
            commit(git, T2);
            write(a, "a1");
            commit(git, T3);
        }

        assertThat(gitLastModified(a, b))
                .containsEntry(normalize(a), T3)
                .containsEntry(normalize(b), T2);
    }

    @Test
    void usesTheRenameDateOfARenamedFile() throws Exception {
        final Path a = dir.resolve("content/a.md");
        final Path renamed = dir.resolve("content/renamed.md");

        try (Git git = init(dir)) {
            write(a, "same content");
            commit(git, T1);
            Files.move(a, renamed);
            commit(git, T2);
        }

        assertThat(gitLastModified(renamed))
                .as("like git log without --follow")
                .containsEntry(normalize(renamed), T2);
    }

    @Test
    void ignoresFilesOutsideTheRepository() throws Exception {
        final Path repo = dir.resolve("repo");
        final Path inside = repo.resolve("content/a.md");
        final Path outside = dir.resolve("elsewhere/b.md");

        try (Git git = init(repo)) {
            write(inside, "a1");
            commit(git, T1);
        }
        write(outside, "b1");

        assertThat(gitLastModified(inside, outside))
                .containsOnlyKeys(normalize(inside))
                .containsEntry(normalize(inside), T1);
    }

    @Test
    void warnsWhenAShallowCloneHidesTheHistory() throws Exception {
        final Path origin = dir.resolve("origin");
        try (Git git = init(origin)) {
            write(origin.resolve("content/a.md"), "a1");
            write(origin.resolve("content/b.md"), "b1");
            commit(git, T1);
            write(origin.resolve("content/c.md"), "c1");
            commit(git, T2);
        }
        final Path clone = dir.resolve("clone");
        // Like actions/checkout by default
        Git.cloneRepository().setURI(origin.toUri().toString()).setDirectory(clone.toFile()).setDepth(1).call().close();
        final Path a = clone.resolve("content/a.md");
        final Path c = clone.resolve("content/c.md");

        final List<LogRecord> warnings = captureWarnings(() -> assertThat(gitLastModified(a, c))
                .as("the history before the fetched commit is missing")
                .containsEntry(normalize(a), T2)
                .containsEntry(normalize(c), T2));

        assertThat(warnings).singleElement()
                .satisfies(r -> assertThat(r.getMessage()).contains("shallow clone: 2 file(s)"));
    }

    @Test
    void doesNotWarnWithTheFullHistory() throws Exception {
        final Path origin = dir.resolve("origin");
        try (Git git = init(origin)) {
            write(origin.resolve("content/a.md"), "a1");
            commit(git, T1);
        }
        final Path clone = dir.resolve("clone");
        Git.cloneRepository().setURI(origin.toUri().toString()).setDirectory(clone.toFile()).call().close();
        final Path a = clone.resolve("content/a.md");

        final List<LogRecord> warnings = captureWarnings(
                () -> assertThat(gitLastModified(a)).containsEntry(normalize(a), T1));

        assertThat(warnings).isEmpty();
    }

    @Test
    void returnsNothingWithoutCommits() throws Exception {
        final Path a = dir.resolve("content/a.md");

        try (Git git = init(dir)) {
            write(a, "a1");
            git.add().addFilepattern(".").call();
        }

        assertThat(gitLastModified(a)).isEmpty();
    }

    @Test
    void returnsNothingOutsideAGitRepository() throws Exception {
        final Path a = dir.resolve("content/a.md");
        write(a, "a1");

        assertThat(gitLastModified(a)).isEmpty();
    }

    @Test
    void returnsNothingForNoFiles() {
        assertThat(RoqPluginSitemapProcessor.gitLastModified(List.of(), LaunchMode.TEST)).isEmpty();
    }

    private static Map<Path, Instant> gitLastModified(Path... files) {
        return RoqPluginSitemapProcessor.gitLastModified(
                List.of(files).stream().map(RoqPluginSitemapGitLastModifiedTest::normalize).toList(),
                LaunchMode.TEST);
    }

    private static List<LogRecord> captureWarnings(Runnable action) {
        final List<LogRecord> records = new ArrayList<>();
        final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        final Logger logger = Logger.getLogger(RoqPluginSitemapProcessor.class.getName());
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Git init(Path dir) throws Exception {
        return Git.init().setDirectory(dir.toFile()).setInitialBranch("main").call();
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void commit(Git git, Instant when) throws Exception {
        commit(git, when, when);
    }

    private static void commit(Git git, Instant authored, Instant committed) throws Exception {
        git.add().addFilepattern(".").call();
        // Also stage the deletions
        git.add().setUpdate(true).addFilepattern(".").call();
        git.commit().setMessage("commit " + committed)
                .setAuthor(new PersonIdent("Roq", "roq@example.com", authored, ZoneOffset.UTC))
                .setCommitter(new PersonIdent("Roq", "roq@example.com", committed, ZoneOffset.UTC))
                .setSign(false)
                .call();
    }
}
