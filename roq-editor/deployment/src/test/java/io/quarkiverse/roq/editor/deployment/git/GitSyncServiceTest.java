package io.quarkiverse.roq.editor.deployment.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
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
        // Create a bare remote repository to simulate a central server
        remoteDirectory = Files.createTempDirectory("roq-git-remote-");
        Git.init().setDirectory(remoteDirectory.toFile()).setBare(true).call().close();

        // Clone the remote repository to a temporary local directory
        localDirectory = Files.createTempDirectory("roq-git-local-");
        localRepository = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(localDirectory.toFile())
                .call();

        // Configure local git user identity
        StoredConfig config = localRepository.getRepository().getConfig();
        config.setString("user", null, "name", "Test User");
        config.setString("user", null, "email", "test@test.com");

        // Configure branch tracking explicitly for JGit pull to work in tests
        String branch = localRepository.getRepository().getBranch();
        config.setString("branch", branch, "remote", "origin");
        config.setString("branch", branch, "merge", "refs/heads/" + branch);
        config.save();

        // Establish the repository with an initial content file
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
    void shouldReturnSuccessWhenPublishingWithNoChanges() {
        GitSyncResult result = gitSyncService.publish("No changes commit", null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Nothing to publish");
    }

    @Test
    void shouldSuccessfullyPublishNewFiles() throws Exception {
        Files.writeString(localDirectory.resolve("content/post.md"), "# New Post Content");

        GitSyncResult result = gitSyncService.publish("Add new post", null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Changes pushed successfully");

        String lastCommitMessage = localRepository.log().setMaxCount(1).call().iterator().next().getFullMessage();
        assertThat(lastCommitMessage).isEqualTo("Add new post");
    }

    @Test
    void shouldUseDefaultTemplateWhenPublishingWithNullMessage() throws Exception {
        Files.writeString(localDirectory.resolve("content/post.md"), "New Content");
        gitSyncService.publish(null, null, null);

        String lastCommitMessage = localRepository.log().setMaxCount(1).call().iterator().next().getFullMessage();
        assertThat(lastCommitMessage).isEqualTo("Update content via Roq Editor");
    }

    @Test
    void shouldSucceedWhenSyncingWithNoRemoteChanges() {
        GitSyncResult result = gitSyncService.sync(null);
        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldReportErrorWhenSyncingWithUncommittedChanges() throws IOException {
        Files.writeString(localDirectory.resolve("content/dirty.md"), "Dirty content");

        GitSyncResult result = gitSyncService.sync(null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("uncommitted content changes");
    }

    @Test
    void shouldSuccessfullyPerformCombinedPublishAndSync() throws Exception {
        Files.writeString(localDirectory.resolve("content/update.md"), "batch update content");

        GitSyncResult result = gitSyncService.publishAndSync("Full sync", null, null);

        assertThat(result.success()).isTrue();
        GitStatusInfo status = gitSyncService.getStatus(null, false);
        assertThat(status.upToDate()).isTrue();
    }

    @Test
    void shouldDetectConflictsDuringSync() throws Exception {
        String fileName = "content/init.md";
        String branch = localRepository.getRepository().getBranch();

        // 1. Remote change: Create another clone to simulate another person pushing
        Path otherPersonDir = Files.createTempDirectory("roq-other-person-");
        try (Git otherGit = Git.cloneRepository()
                .setURI(remoteDirectory.toUri().toString())
                .setDirectory(otherPersonDir.toFile())
                .setBranch(branch)
                .call()) {
            // Modify the same line
            Files.writeString(otherPersonDir.resolve(fileName), "remote change content\n");
            otherGit.add().addFilepattern(fileName).call();
            otherGit.commit().setMessage("Remote update").call();
            otherGit.push().call();
        }

        // 2. Local change: Modify the SAME line in the main local repo and commit
        Files.writeString(localDirectory.resolve(fileName), "local conflicting content\n");
        localRepository.add().addFilepattern(fileName).call();
        localRepository.commit().setMessage("Local update").call();

        // 3. Execute sync via service
        GitSyncResult result = gitSyncService.sync(null);

        // 4. Verify conflict detection
        assertThat(result.success()).isFalse();
        assertThat(result.hasConflicts()).isTrue();
        assertThat(result.conflictFiles()).contains(fileName);

        cleanDirectory(otherPersonDir);
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
                return Collections.emptyMap();
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
                        return new CommitMessageConfig() {
                            @Override
                            public String template() {
                                return "Update content via Roq Editor";
                            }
                        };
                    }
                };
            }
        };
    }
}
