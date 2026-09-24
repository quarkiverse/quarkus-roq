package io.quarkiverse.roq.plugin.sitemap.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.plugin.sitemap.deployment.RoqPluginSitemapProcessor.LastModifiedIndex;

/**
 * Correctness test for {@link RoqPluginSitemapProcessor#buildIndex}: builds a small, disposable git repo
 * with a known, controlled commit history via JGit's {@link TestRepository}, instead of depending on this
 * repository's own real (and forever-changing) history.
 */
public class RoqPluginSitemapBuildIndexTest {

    @TempDir
    Path tempDir;

    private Repository repository;
    private TestRepository<Repository> testRepo;

    @BeforeEach
    void setUp() throws Exception {
        this.repository = new FileRepositoryBuilder().setGitDir(this.tempDir.resolve(".git").toFile()).build();
        this.repository.create();
        final RefUpdate refUpdate = this.repository.updateRef(Constants.HEAD);
        refUpdate.link("refs/heads/main");
        this.testRepo = new TestRepository<>(this.repository);
    }

    @AfterEach
    void tearDown() {
        this.repository.close();
    }

    @Test
    void indexesEachFileByItsMostRecentCommit() throws Exception {
        final RevCommit first = this.testRepo.branch("main").commit()
                .add("content/index.html", "v1")
                .add("content/about.html", "v1")
                .tick(60)
                .create();
        final RevCommit second = this.testRepo.branch("main").commit()
                .add("content/index.html", "v2")
                .tick(60)
                .create();

        final LastModifiedIndex index = RoqPluginSitemapProcessor.buildIndex(this.repository, second);

        assertThat(index.lastModified(this.tempDir.resolve("content/index.html")))
                .as("touched again in the second commit")
                .isEqualTo(Instant.ofEpochSecond(second.getCommitTime()));
        assertThat(index.lastModified(this.tempDir.resolve("content/about.html")))
                .as("only ever touched in the first commit")
                .isEqualTo(Instant.ofEpochSecond(first.getCommitTime()));
        assertThat(index.lastModified(this.tempDir.resolve("content/missing.html")))
                .as("never existed in the history")
                .isNull();
    }

    @Test
    void ignoresPathsOutsideTheRepository() throws Exception {
        final RevCommit commit = this.testRepo.branch("main").commit()
                .add("content/index.html", "v1")
                .create();

        final LastModifiedIndex index = RoqPluginSitemapProcessor.buildIndex(this.repository, commit);

        assertThat(index.lastModified(Path.of("/somewhere/else/content/index.html"))).isNull();
    }

    @Test
    void returnsAnEmptyIndexWhenHeadIsUnborn() throws Exception {
        final LastModifiedIndex index = RoqPluginSitemapProcessor.buildIndex(this.repository, null);

        assertThat(index.lastModified(this.tempDir.resolve("content/index.html"))).isNull();
    }

    @Test
    void attributesAMergedFileToTheOriginatingCommitNotTheMerge() throws Exception {
        final RevCommit base = this.testRepo.branch("main").commit()
                .add("content/about.md", "v0")
                .tick(60)
                .create();
        final RevCommit featureEdit = this.testRepo.branch("feature").commit()
                .parent(base)
                .add("content/about.md", "v1")
                .tick(60)
                .create();
        this.testRepo.branch("main").commit()
                .add("content/other.html", "v0")
                .tick(60)
                .create();
        // Merge just brings in feature's already-committed version of about.md (no conflict resolution).
        final RevCommit merge = this.testRepo.branch("main").commit()
                .parent(featureEdit)
                .add("content/about.md", "v1")
                .tick(60)
                .create();

        final LastModifiedIndex index = RoqPluginSitemapProcessor.buildIndex(this.repository, merge);

        assertThat(index.lastModified(this.tempDir.resolve("content/about.md")))
                .as("RevFilter.NO_MERGES: attributed to the commit that actually edited it, like `git log`")
                .isEqualTo(Instant.ofEpochSecond(featureEdit.getCommitTime()))
                .isNotEqualTo(Instant.ofEpochSecond(merge.getCommitTime()));
    }

    @Test
    void indexesEveryFileAcrossManyCommits() throws Exception {
        final int commitCount = 300;
        final List<RevCommit> commits = new ArrayList<>(commitCount);
        final TestRepository<Repository>.BranchBuilder main = this.testRepo.branch("main");
        for (int i = 0; i < commitCount; i++) {
            commits.add(main.commit()
                    .add("content/page-%04d.html".formatted(i), "v" + i)
                    .tick(1)
                    .create());
        }

        final LastModifiedIndex index = RoqPluginSitemapProcessor.buildIndex(this.repository, commits.get(commitCount - 1));

        for (int i = 0; i < commitCount; i++) {
            assertThat(index.lastModified(this.tempDir.resolve("content/page-%04d.html".formatted(i))))
                    .as("page %d indexed to its own commit", i)
                    .isEqualTo(Instant.ofEpochSecond(commits.get(i).getCommitTime()));
        }
    }

    @Test
    void attributesADeletedThenReAddedFileToTheReAdd() throws Exception {
        final RevCommit created = this.testRepo.branch("main").commit()
                .add("content/draft.html", "v1")
                .tick(60)
                .create();
        final RevCommit deleted = this.testRepo.branch("main").commit()
                .rm("content/draft.html")
                .tick(60)
                .create();
        final RevCommit reAdded = this.testRepo.branch("main").commit()
                .add("content/draft.html", "v2")
                .tick(60)
                .create();

        final LastModifiedIndex index = RoqPluginSitemapProcessor.buildIndex(this.repository, reAdded);

        assertThat(index.lastModified(this.tempDir.resolve("content/draft.html")))
                .as("re-added after deletion: attributed to the re-add, not the original creation or the deletion")
                .isEqualTo(Instant.ofEpochSecond(reAdded.getCommitTime()))
                .isNotEqualTo(Instant.ofEpochSecond(created.getCommitTime()))
                .isNotEqualTo(Instant.ofEpochSecond(deleted.getCommitTime()));
    }

    @Test
    void resolveIndexForReusesTheCacheUntilHeadMoves() throws Exception {
        final RevCommit first = this.testRepo.branch("main").commit()
                .add("content/index.html", "v1")
                .tick(60)
                .create();

        final LastModifiedIndex beforeSecondCommit = RoqPluginSitemapProcessor.resolveIndexFor(this.repository);
        assertThat(beforeSecondCommit.lastModified(this.tempDir.resolve("content/index.html")))
                .isEqualTo(Instant.ofEpochSecond(first.getCommitTime()));

        // Same HEAD, called again: must return the same cached result (not rebuild) -- same object identity.
        final LastModifiedIndex stillCached = RoqPluginSitemapProcessor.resolveIndexFor(this.repository);
        assertThat(stillCached).isSameAs(beforeSecondCommit);

        final RevCommit second = this.testRepo.branch("main").commit()
                .add("content/index.html", "v2")
                .tick(60)
                .create();
        final ObjectId newHead = this.repository.resolve("HEAD");
        assertThat(newHead).isEqualTo(second.toObjectId());

        // HEAD moved: must rebuild and reflect the new commit, not silently keep serving the old snapshot.
        final LastModifiedIndex afterSecondCommit = RoqPluginSitemapProcessor.resolveIndexFor(this.repository);
        assertThat(afterSecondCommit).isNotSameAs(beforeSecondCommit);
        assertThat(afterSecondCommit.lastModified(this.tempDir.resolve("content/index.html")))
                .isEqualTo(Instant.ofEpochSecond(second.getCommitTime()));
    }
}
