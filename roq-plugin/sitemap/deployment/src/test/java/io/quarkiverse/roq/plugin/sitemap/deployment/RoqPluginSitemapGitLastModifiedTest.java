package io.quarkiverse.roq.plugin.sitemap.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.runtime.LaunchMode;

/**
 * Features tested: the single history walk used for {@code quarkus.roq.sitemap.last-modified=git} returns, for each
 * file, the date of the last commit touching it, and nothing for uncommitted files.
 */
public class RoqPluginSitemapGitLastModifiedTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-02-01T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-03-01T10:00:00Z");

    @TempDir
    Path dir;

    @Test
    void resolvesTheLastCommitDateOfEachFile() throws Exception {
        final Path a = dir.resolve("content/a.md");
        final Path b = dir.resolve("content/posts/b.md");
        final Path c = dir.resolve("content/c.md");
        final Path untracked = dir.resolve("content/untracked.md");
        Files.createDirectories(b.getParent());

        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            Files.writeString(a, "a1");
            Files.writeString(b, "b1");
            commit(git, T1);
            Files.writeString(a, "a2");
            Files.writeString(c, "c1");
            commit(git, T2);
            Files.writeString(c, "c2");
            commit(git, T3);
        }
        Files.writeString(untracked, "u");

        final Map<Path, Instant> dates = RoqPluginSitemapProcessor.gitLastModified(
                List.of(a, b, c, untracked).stream().map(p -> p.toAbsolutePath().normalize()).toList(),
                LaunchMode.TEST);

        assertThat(dates)
                .containsEntry(a.toAbsolutePath().normalize(), T2)
                .containsEntry(b.toAbsolutePath().normalize(), T1)
                .containsEntry(c.toAbsolutePath().normalize(), T3)
                .doesNotContainKey(untracked.toAbsolutePath().normalize());
    }

    private static void commit(Git git, Instant when) throws Exception {
        final PersonIdent ident = new PersonIdent("Roq", "roq@example.com", when, ZoneOffset.UTC);
        git.add().addFilepattern(".").call();
        git.commit().setMessage("commit " + when).setAuthor(ident).setCommitter(ident).setSign(false).call();
    }
}
