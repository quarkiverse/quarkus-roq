package io.quarkiverse.roq.editor.deployment.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig;
import io.quarkiverse.roq.editor.runtime.devui.git.GitStatusInfo;
import io.quarkiverse.roq.editor.runtime.devui.git.GitSyncResult;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;

/**
 * Unit tests for {@link GitSyncServiceImpl}.
 * Validates repository status detection, file filtering, and synchronization flows.
 */
class GitSyncServiceTest {

    private Path localDirectory;
    private Path remoteDirectory;
    private Git localRepository;
    private GitSyncServiceImpl gitSyncService;

    @BeforeEach
    void setUp() throws Exception {
        remoteDirectory = Files.createTempDirectory("roq-git-remote-");
        Git.init().setDirectory(remoteDirectory.toFile()).setBare(true).call().close();

        localDirectory = Files.createTempDirectory("roq-git-local-");
        localRepository = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(localDirectory.toFile())
                .call();

        StoredConfig config = localRepository.getRepository().getConfig();
        config.setString("user", null, "name", "Test User");
        config.setString("user", null, "email", "test@test.com");
        String branch = localRepository.getRepository().getBranch();
        config.setString("branch", branch, "remote", "origin");
        config.setString("branch", branch, "merge", "refs/heads/" + branch);
        config.save();

        Files.createDirectories(localDirectory.resolve("content"));
        Files.writeString(localDirectory.resolve("content/init.md"), "initial content\n");
        localRepository.add().addFilepattern("content/init.md").call();
        localRepository.commit().setMessage("Initial commit").call();
        localRepository.push().call();

        gitSyncService = new GitSyncServiceImpl(createEditorConfig(), createSiteConfig(), localDirectory.toFile());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (localRepository != null) {
            localRepository.close();
        }
        cleanDirectory(localDirectory);
        cleanDirectory(remoteDirectory);
    }

    private void cleanDirectory(Path directory) throws IOException {
        if (directory != null && Files.exists(directory)) {
            try (var stream = Files.walk(directory)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    @Test
    void shouldReportUpToDateForCleanRepository() {
        GitStatusInfo status = gitSyncService.getStatus(null, false);
        assertThat(status.upToDate()).isTrue();
        assertThat(status.branch()).isNotBlank();
    }

    @Test
    void shouldDetectNewUntrackedContentFiles() throws IOException {
        Files.writeString(localDirectory.resolve("content/new-post.md"), "# New Post");
        GitStatusInfo status = gitSyncService.getStatus(null, false);
        assertThat(status.hasUnpublished()).isTrue();
        assertThat(status.upToDate()).isFalse();
    }

    @Test
    void shouldDetectModifiedTrackedFiles() throws IOException {
        Files.writeString(localDirectory.resolve("content/init.md"), "Modified content\n");
        GitStatusInfo status = gitSyncService.getStatus(null, false);
        assertThat(status.hasUnpublished()).isTrue();
        assertThat(status.upToDate()).isFalse();
    }

    @Test
    void shouldReportAheadStatus() throws Exception {
        Files.writeString(localDirectory.resolve("content/post.md"), "local content");
        localRepository.add().addFilepattern("content/post.md").call();
        localRepository.commit().setMessage("Local commit").call();

        GitStatusInfo status = gitSyncService.getStatus(null, false);
        assertThat(status.ahead()).isEqualTo(1);
    }

    @Test
    void shouldReportBehindStatus() throws Exception {
        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(localRepository.getRepository().getBranch())
                .call()) {
            Files.writeString(otherPersonDir.resolve("content/remote.md"), "remote content");
            otherGit.add().addFilepattern("content/remote.md").call();
            otherGit.commit().setMessage("Remote commit").call();
            otherGit.push().call();
        }

        GitStatusInfo status = gitSyncService.getStatus(null, false);
        assertThat(status.behind()).isEqualTo(1);
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldReportAheadAndBehindStatus() throws Exception {
        Files.writeString(localDirectory.resolve("content/local.md"), "local content");
        localRepository.add().addFilepattern("content/local.md").call();
        localRepository.commit().setMessage("Local commit").call();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(localRepository.getRepository().getBranch())
                .call()) {
            Files.writeString(otherPersonDir.resolve("content/remote.md"), "remote content");
            otherGit.add().addFilepattern("content/remote.md").call();
            otherGit.commit().setMessage("Remote commit").call();
            otherGit.push().call();
        }

        GitStatusInfo status = gitSyncService.getStatus(null, false);
        assertThat(status.ahead()).isEqualTo(1);
        assertThat(status.behind()).isEqualTo(1);
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldReturnSuccessWhenPublishingWithNoChanges() {
        GitSyncResult result = gitSyncService.publish("No changes commit", null, null);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).matches(m -> m.equals("Nothing to publish") || m.contains("successfully"));
    }

    @Test
    void shouldSuccessfullyPublishNewFiles() throws Exception {
        Files.writeString(localDirectory.resolve("content/post.md"), "# New Post Content");
        GitSyncResult result = gitSyncService.publish("Add new post", null, null);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Changes pushed successfully");
    }

    @Test
    void shouldSucceedWhenSyncingWithNoRemoteChanges() {
        GitSyncResult result = gitSyncService.sync(null);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldReportConflictsDuringSyncWithUncommittedChanges() throws Exception {
        String fileName = "content/init.md";
        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(localRepository.getRepository().getBranch())
                .call()) {
            Files.writeString(otherPersonDir.resolve(fileName), "remote content\n");
            otherGit.add().addFilepattern(fileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        Files.writeString(localDirectory.resolve(fileName), "local dirty content\n");
        GitSyncResult result = gitSyncService.sync(null);
        assertThat(result.success()).isFalse();
        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.conflictFiles()).contains(fileName);
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldSucceedSyncWithAutoStashWhenWorktreeIsDirty() throws Exception {
        String fileName = "content/init.md";
        String branch = localRepository.getRepository().getBranch();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(branch)
                .call()) {
            Files.writeString(otherPersonDir.resolve(fileName), "initial content\nremote line 2\n");
            otherGit.add().addFilepattern(fileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        Files.writeString(localDirectory.resolve(fileName), "local line 1\ninitial content\n");
        GitSyncResult result = gitSyncService.sync(null);
        assertThat(result.success()).isTrue();
        String finalContent = Files.readString(localDirectory.resolve(fileName));
        assertThat(finalContent).contains("local line 1");
        assertThat(finalContent).contains("remote line 2");
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldSuccessfullyPerformCombinedPublishAndSync() throws Exception {
        Files.writeString(localDirectory.resolve("content/update.md"), "batch update content");
        GitSyncResult result = gitSyncService.publishAndSync("Full sync test", null, null);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).containsIgnoringCase("successfully");
    }

    @Test
    void shouldSucceedPublishWhenRemoteChangesExist() throws Exception {
        Files.writeString(localDirectory.resolve("content/new-post.md"), "# New Post");
        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(localRepository.getRepository().getBranch())
                .call()) {
            Files.writeString(otherPersonDir.resolve("content/remote.md"), "remote content\n");
            otherGit.add().addFilepattern("content/remote.md").call();
            otherGit.commit().setMessage("Remote change").call();
            otherGit.push().call();
        }

        GitSyncResult result = gitSyncService.publish("Try to publish", null, null);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Changes pushed successfully");
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldDetectConflictsDuringPublish() throws Exception {
        String fileName = "content/init.md";
        String branch = localRepository.getRepository().getBranch();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(branch)
                .call()) {
            Files.writeString(otherPersonDir.resolve(fileName), "REMOTE CHANGE\ninitial content\n");
            otherGit.add().addFilepattern(fileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        Files.writeString(localDirectory.resolve(fileName), "LOCAL CHANGE\ninitial content\n");

        GitSyncResult result = gitSyncService.publish("Local update", null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.conflictFiles()).contains(fileName);
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldSuccessfullyPublishAfterManualConflictResolution() throws Exception {
        String fileName = "content/init.md";
        String branch = localRepository.getRepository().getBranch();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(branch)
                .call()) {
            Files.writeString(otherPersonDir.resolve(fileName), "remote conflicting content\n");
            otherGit.add().addFilepattern(fileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        Files.writeString(localDirectory.resolve(fileName), "local conflicting content\n");
        gitSyncService.sync(null);

        Files.writeString(localDirectory.resolve(fileName), "resolved content\n");
        GitSyncResult result = gitSyncService.publish("Resolve conflict", null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Changes pushed successfully");
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldSkipRebaseStepWhenNothingToCommit() throws Exception {
        String fileName = "content/init.md";
        String branch = localRepository.getRepository().getBranch();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(branch)
                .call()) {
            Files.writeString(otherPersonDir.resolve(fileName), "remote content\n");
            otherGit.add().addFilepattern(fileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        Files.writeString(localDirectory.resolve(fileName), "local content\n");
        localRepository.add().addFilepattern(fileName).call();
        localRepository.commit().setMessage("Local update").call();

        try {
            localRepository.pull().setRebase(true).call();
        } catch (Exception ignored) {
        }

        Files.writeString(localDirectory.resolve(fileName), "remote content\n");

        GitSyncResult result = gitSyncService.publish("Resolve", null, null);

        assertThat(result.success()).isTrue();
        assertThat(localRepository.getRepository().getRepositoryState()).isEqualTo(RepositoryState.SAFE);

        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldDetectConflictsDuringSync() throws Exception {
        String fileName = "content/init.md";
        String branch = localRepository.getRepository().getBranch();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(branch)
                .call()) {
            Files.writeString(otherPersonDir.resolve(fileName), "remote change content\n");
            otherGit.add().addFilepattern(fileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        Files.writeString(localDirectory.resolve(fileName), "local conflicting content\n");
        localRepository.add().addFilepattern(fileName).call();
        localRepository.commit().setMessage("Local update").call();

        GitSyncResult result = gitSyncService.sync(null);
        assertThat(result.success()).isFalse();
        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.conflictFiles()).contains(fileName);
        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldReportAuthFailedWhenFetchFailsOnSshRemote() throws Exception {
        StoredConfig config = localRepository.getRepository().getConfig();
        config.setString("remote", "origin", "url", "git@github.com:quarkiverse/quarkus-roq.git");
        config.save();

        GitStatusInfo status = gitSyncService.getStatus(null, false);

        assertThat(status.isSsh()).isTrue();
        assertThat(status.authFailed()).isTrue();
    }

    @Test
    void shouldPreserveLocalStatusEvenWhenAuthIsRequired() throws Exception {
        Files.writeString(localDirectory.resolve("content/ahead.md"), "ahead content");
        localRepository.add().addFilepattern("content/ahead.md").call();
        localRepository.commit().setMessage("Ahead commit").call();
        Files.writeString(localDirectory.resolve("content/dirty.md"), "dirty content");

        StoredConfig config = localRepository.getRepository().getConfig();
        config.setString("remote", "origin", "url", "git@github.com:quarkiverse/quarkus-roq.git");
        config.save();

        GitStatusInfo status = gitSyncService.getStatus(null, false);

        assertThat(status.isSsh()).isTrue();
        assertThat(status.authFailed()).isTrue();

        assertThat(status.ahead()).isEqualTo(1);
        assertThat(status.hasUnpublished()).isTrue();
        assertThat(status.pendingFiles()).contains("content/dirty.md");
    }

    @Test
    void shouldReportStatusEvenForBranchWithoutUpstream() throws Exception {
        String newBranch = "feature-branch";
        localRepository.branchCreate().setName(newBranch).call();
        localRepository.checkout().setName(newBranch).call();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .call()) {
            otherGit.checkout().setCreateBranch(true).setName(newBranch).call();
            Files.writeString(otherPersonDir.resolve("content/remote-feature.md"), "remote content");
            otherGit.add().addFilepattern("content/remote-feature.md").call();
            otherGit.commit().setMessage("Remote feature commit").call();
            otherGit.push().setRemote("origin").call();
        }

        Files.writeString(localDirectory.resolve("content/local-feature.md"), "local content");
        localRepository.add().addFilepattern("content/local-feature.md").call();
        localRepository.commit().setMessage("Local feature commit").call();

        assertThat(BranchTrackingStatus.of(localRepository.getRepository(), newBranch)).isNull();

        GitStatusInfo status = gitSyncService.getStatus(null, false);

        assertThat(status.branch()).isEqualTo(newBranch);
        assertThat(status.behind()).isEqualTo(1);
        assertThat(status.ahead()).isEqualTo(1);

        assertThat(BranchTrackingStatus.of(localRepository.getRepository(), newBranch)).isNull();

        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldAutoConfigureTrackingDuringSync() throws Exception {
        String newBranch = "feature-sync-branch";
        localRepository.branchCreate().setName(newBranch).call();
        localRepository.checkout().setName(newBranch).call();

        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .call()) {
            otherGit.checkout().setCreateBranch(true).setName(newBranch).call();
            Files.writeString(otherPersonDir.resolve("content/feature.md"), "feature content");
            otherGit.add().addFilepattern("content/feature.md").call();
            otherGit.commit().setMessage("Feature commit").call();
            otherGit.push().setRemote("origin").call();
        }

        assertThat(BranchTrackingStatus.of(localRepository.getRepository(), newBranch)).isNull();

        GitSyncResult result = gitSyncService.sync(null);

        assertThat(result.success()).isTrue();
        assertThat(BranchTrackingStatus.of(localRepository.getRepository(), newBranch)).isNotNull();

        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldPreserveUntrackedFilesDuringSyncWithAutoStash() throws Exception {
        String remoteFileName = "content/remote.md";
        String untrackedFileName = "content/new-untracked.md";
        String branch = localRepository.getRepository().getBranch();

        // Remote changes
        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(branch)
                .call()) {
            Files.writeString(otherPersonDir.resolve(remoteFileName), "remote content\n");
            otherGit.add().addFilepattern(remoteFileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        // Create a new untracked file locally
        Files.writeString(localDirectory.resolve(untrackedFileName), "untracked content\n");

        GitSyncResult result = gitSyncService.sync(null);

        assertThat(result.success()).isTrue();
        assertThat(Files.exists(localDirectory.resolve(remoteFileName))).isTrue();
        assertThat(Files.exists(localDirectory.resolve(untrackedFileName))).isTrue();
        assertThat(Files.readString(localDirectory.resolve(untrackedFileName))).contains("untracked content");

        cleanDirectory(otherPersonDir);
    }

    @Test
    void shouldHandleConcurrentGitOperationsSafely() throws Exception {
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    GitStatusInfo status = gitSyncService.getStatus(null, false);
                    if (status.branch() != null) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    private RoqSiteConfig createSiteConfig() {
        return new RoqSiteConfig() {
            @Override
            public Optional<String> urlOptional() {
                return Optional.empty();
            }

            @Override
            public int routeOrder() {
                return 1100;
            }

            @Override
            public Optional<List<String>> ignoredFiles() {
                return Optional.empty();
            }

            @Override
            public List<String> defaultIgnoredFiles() {
                return List.of();
            }

            @Override
            public Optional<List<String>> escapedPages() {
                return Optional.empty();
            }

            @Override
            public Optional<String> pageLayout() {
                return Optional.of("page");
            }

            @Override
            public String contentDir() {
                return "content";
            }

            @Override
            public String staticDir() {
                return "static";
            }

            @Override
            public String publicDir() {
                return "public";
            }

            @Override
            public String imagesPath() {
                return "images/";
            }

            @Override
            public boolean generator() {
                return true;
            }

            @Override
            public boolean future() {
                return false;
            }

            @Override
            public Optional<String> theme() {
                return Optional.empty();
            }

            @Override
            public boolean draft() {
                return false;
            }

            @Override
            public String draftDirectory() {
                return "drafts";
            }

            @Override
            public String dateFormat() {
                return "yyyy-MM-dd";
            }

            @Override
            public Optional<String> timeZone() {
                return Optional.empty();
            }

            @Override
            public String defaultLocale() {
                return "en";
            }

            @Override
            public boolean slugifyFiles() {
                return true;
            }

            @Override
            public Map<String, CollectionConfig> collectionsMap() {
                return Map.of();
            }

            @Override
            public String generatedTemplatesOutputDir() {
                return "roq-templates";
            }

            @Override
            public Optional<String> pathPrefix() {
                return Optional.empty();
            }
        };
    }

    private RoqEditorConfig createEditorConfig() {
        return new RoqEditorConfig() {
            @Override
            public Markup pageMarkup() {
                return Markup.MARKDOWN;
            }

            @Override
            public Markup docMarkup() {
                return Markup.MARKDOWN;
            }

            @Override
            public VisualEditorConfig visualEditor() {
                return new VisualEditorConfig() {
                    @Override
                    public boolean enabled() {
                        return true;
                    }

                    @Override
                    public boolean safe() {
                        return true;
                    }
                };
            }

            @Override
            public SuggestedPathConfig suggestedPath() {
                return () -> true;
            }

            @Override
            public SyncConfig sync() {
                return new SyncConfig() {
                    @Override
                    public boolean enabled() {
                        return true;
                    }

                    @Override
                    public AutoSyncConfig autoSync() {
                        return new AutoSyncConfig() {
                            @Override
                            public boolean enabled() {
                                return false;
                            }

                            @Override
                            public int intervalSeconds() {
                                return 60;
                            }
                        };
                    }

                    @Override
                    public AutoPublishConfig autoPublish() {
                        return new AutoPublishConfig() {
                            @Override
                            public boolean enabled() {
                                return false;
                            }

                            @Override
                            public int intervalSeconds() {
                                return 300;
                            }
                        };
                    }

                    @Override
                    public CommitMessageConfig commitMessage() {
                        return () -> "Update content via Roq Editor";
                    }
                };
            }
        };
    }
}
