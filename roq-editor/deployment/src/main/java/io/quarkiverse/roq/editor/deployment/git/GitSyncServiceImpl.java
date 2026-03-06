package io.quarkiverse.roq.editor.deployment.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.SshdSession;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.util.FS;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig;
import io.quarkiverse.roq.editor.runtime.devui.git.GitStatusInfo;
import io.quarkiverse.roq.editor.runtime.devui.git.GitSyncResult;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;

/**
 * Service providing Git operations for the Roq Editor Dev UI.
 */
public class GitSyncServiceImpl implements GitSyncService {

    private static final Logger LOG = Logger.getLogger(GitSyncServiceImpl.class);
    private static final Pattern SCP_LIKE_SSH_URL = Pattern.compile("^[^@]+@[^:]+:[^/].*$");

    private final RoqEditorConfig editorConfig;
    private final RoqSiteConfig siteConfig;
    private final File rootDirectory;

    public GitSyncServiceImpl(RoqEditorConfig editorConfig, RoqSiteConfig siteConfig, File rootDirectory) {
        this.editorConfig = editorConfig;
        this.siteConfig = siteConfig;
        this.rootDirectory = rootDirectory;
    }

    @Override
    public GitStatusInfo getStatus(String passphrase, boolean skipFetch) {
        try (Repository repository = openRepository()) {
            if (repository == null) {
                return new GitStatusInfo(false, false, false, "no-git-repo", 0, 0, Collections.emptyList(), false);
            }

            String currentBranch = repository.getBranch();
            String remoteUrl = repository.getConfig().getString("remote", "origin", "url");

            if (isSshUrl(remoteUrl) && (passphrase == null || passphrase.isEmpty())) {
                if (!skipFetch) {
                    LOG.debug("SSH authentication required for remote status check.");
                    return new GitStatusInfo(false, false, false, currentBranch, 0, 0, Collections.emptyList(), true);
                }
            }

            try (Git git = new Git(repository)) {
                if (!skipFetch) {
                    performBackgroundFetch(git, passphrase);
                }

                Status status = git.status().call();
                String rootPrefix = resolveWorkingPrefix(repository);
                List<String> contentChanges = extractSignificantContentChanges(status, rootPrefix);

                BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, currentBranch);
                int aheadCount = (trackingStatus != null) ? trackingStatus.getAheadCount() : 0;
                int behindCount = (trackingStatus != null) ? trackingStatus.getBehindCount() : 0;

                boolean isUpToDate = aheadCount == 0 && behindCount == 0 && contentChanges.isEmpty();

                return new GitStatusInfo(isUpToDate, !contentChanges.isEmpty(), behindCount > 0, currentBranch,
                        aheadCount, behindCount, contentChanges, false);
            }
        } catch (Exception e) {
            LOG.error("Failed to retrieve Git repository status", e);
            return new GitStatusInfo(false, false, false, "", 0, 0, Collections.emptyList(), false);
        }
    }

    @Override
    public GitSyncResult sync(String passphrase) {
        try (Repository repository = openRepository(); Git git = new Git(repository)) {
            if (repository == null) {
                return new GitSyncResult(false, "Git repository not found", false, Collections.emptyList(), false);
            }

            Status status = git.status().call();
            String rootPrefix = resolveWorkingPrefix(repository);
            boolean hasLocalChanges = !extractSignificantContentChanges(status, rootPrefix).isEmpty();

            if (hasLocalChanges) {
                return new GitSyncResult(false, "Sync cancelled: uncommitted content changes detected.",
                        false, Collections.emptyList(), false);
            }

            PullResult pullResult = git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName(repository.getBranch())
                    .setTransportConfigCallback(createTransportCallback(passphrase))
                    .call();

            if (!pullResult.isSuccessful()) {
                // Check MergeResult for conflicts first
                if (pullResult.getMergeResult() != null &&
                        (pullResult.getMergeResult()
                                .getMergeStatus() == org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING ||
                                pullResult.getMergeResult().getConflicts() != null)) {
                    return handleFailedPull(pullResult);
                }

                // Fallback to manual index check if MergeResult is inconclusive
                Status statusAfterPull = git.status().call();
                if (!statusAfterPull.getConflicting().isEmpty()) {
                    List<String> conflictFiles = new ArrayList<>(statusAfterPull.getConflicting());
                    return new GitSyncResult(false, "Merge conflicts detected", true, conflictFiles, false);
                }

                return handleFailedPull(pullResult);
            }

            return new GitSyncResult(true, "Synchronized successfully with remote", false, Collections.emptyList(), false);

        } catch (Exception e) {
            LOG.error("Content synchronization failed", e);
            return new GitSyncResult(false, "Error: " + e.getMessage(), false, Collections.emptyList(),
                    isAuthenticationError(e));
        }
    }

    @Override
    public GitSyncResult push(String passphrase) {
        try (Repository repository = openRepository(); Git git = new Git(repository)) {
            if (repository == null) {
                return new GitSyncResult(false, "Git repository not found", false, Collections.emptyList(), false);
            }

            git.push()
                    .setTransportConfigCallback(createTransportCallback(passphrase))
                    .call();

            return new GitSyncResult(true, "Changes pushed successfully", false, Collections.emptyList(), false);
        } catch (Exception e) {
            LOG.error("Push operation failed", e);
            return new GitSyncResult(false, "Push error: " + e.getMessage(), false, Collections.emptyList(),
                    isAuthenticationError(e));
        }
    }

    @Override
    public GitSyncResult publish(String commitMessage, String passphrase, List<String> filePaths) {
        try (Repository repository = openRepository(); Git git = new Git(repository)) {
            if (repository == null) {
                return new GitSyncResult(false, "Git repository not found", false, Collections.emptyList(), false);
            }

            String rootPrefix = resolveWorkingPrefix(repository);
            Status status = git.status().call();
            List<String> contentFilesToPublish = extractSignificantContentChanges(status, rootPrefix);

            if (contentFilesToPublish.isEmpty()) {
                return new GitSyncResult(true, "Nothing to publish", false, Collections.emptyList(), false);
            }

            for (String filePath : contentFilesToPublish) {
                git.add().addFilepattern(filePath).call();
            }

            String finalMessage = (commitMessage == null || commitMessage.isBlank())
                    ? editorConfig.sync().commitMessage().template()
                    : commitMessage;

            git.commit().setMessage(finalMessage).call();

            return push(passphrase);
        } catch (Exception e) {
            LOG.error("Publishing operation failed", e);
            return new GitSyncResult(false, "Publish error: " + e.getMessage(), false, Collections.emptyList(),
                    isAuthenticationError(e));
        }
    }

    @Override
    public GitSyncResult publishAndSync(String commitMessage, String passphrase, List<String> filePaths) {
        GitSyncResult publishResult = publish(commitMessage, passphrase, filePaths);
        if (!publishResult.success()) {
            return publishResult;
        }
        return sync(passphrase);
    }

    protected File resolveWorkingDirectory() {
        return rootDirectory;
    }

    private Repository openRepository() throws IOException {
        File workingDir = resolveWorkingDirectory();
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(workingDir);

        if (builder.getGitDir() == null) {
            LOG.debug("Git directory not found starting from " + workingDir.getAbsolutePath());
            return null;
        }
        return builder.build();
    }

    private void performBackgroundFetch(Git git, String passphrase) {
        try {
            git.fetch()
                    .setTransportConfigCallback(createTransportCallback(passphrase))
                    .call();
        } catch (Exception e) {
            LOG.debug("Background remote fetch skipped: " + e.getMessage());
        }
    }

    private String resolveWorkingPrefix(Repository repository) {
        Path rootPath = repository.getWorkTree().toPath().toAbsolutePath().normalize();
        Path currentPath = rootDirectory.toPath().toAbsolutePath().normalize();
        if (currentPath.equals(rootPath)) {
            return "";
        }
        return rootPath.relativize(currentPath).toString().replace(File.separatorChar, '/') + "/";
    }

    private List<String> extractSignificantContentChanges(Status status, String prefix) {
        return Stream.of(
                status.getUncommittedChanges(),
                status.getUntracked(),
                status.getAdded(),
                status.getChanged(),
                status.getRemoved())
                .flatMap(Set::stream)
                .filter(path -> isSignificantContentFile(path, prefix))
                .distinct()
                .toList();
    }

    private boolean isSignificantContentFile(String path, String prefix) {
        if (path.startsWith(".git") || path.contains("/.git/")) {
            return false;
        }
        if (!prefix.isEmpty() && !path.startsWith(prefix)) {
            return false;
        }

        String relativePath = prefix.isEmpty() ? path : (path.startsWith(prefix) ? path.substring(prefix.length()) : path);
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        return relativePath.startsWith(siteConfig.contentDir()) ||
                relativePath.startsWith(siteConfig.publicDir()) ||
                relativePath.startsWith(siteConfig.staticDir()) ||
                relativePath.startsWith("posts/") ||
                relativePath.startsWith("data/") ||
                relativePath.startsWith("templates/") ||
                relativePath.equals("roq.java");
    }

    private GitSyncResult handleFailedPull(PullResult result) {
        if (result.getMergeResult() != null && result.getMergeResult().getConflicts() != null) {
            List<String> conflictFiles = new ArrayList<>(result.getMergeResult().getConflicts().keySet());
            return new GitSyncResult(false, "Merge conflicts detected", true, conflictFiles, false);
        }
        String errorMsg = "Sync failed";
        if (result.getMergeResult() != null) {
            errorMsg += ": " + result.getMergeResult().getMergeStatus();
        } else if (result.getFetchResult() != null) {
            errorMsg += " (Fetch messages: " + result.getFetchResult().getMessages() + ")";
        }
        return new GitSyncResult(false, errorMsg, false, Collections.emptyList(), false);
    }

    private boolean isSshUrl(String url) {
        if (url == null)
            return false;
        return url.startsWith("ssh://") || url.startsWith("git@") || SCP_LIKE_SSH_URL.matcher(url).matches();
    }

    private boolean isAuthenticationError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && (msg.toLowerCase().contains("auth fail") || msg.toLowerCase().contains("passphrase"))) {
                return true;
            }
            if (current instanceof javax.security.auth.login.FailedLoginException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private TransportConfigCallback createTransportCallback(String passphrase) {
        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                SshdSessionFactory factory = new SshdSessionFactoryBuilder()
                        .setPreferredAuthentications("publickey")
                        .setHomeDirectory(FS.DETECTED.userHome())
                        .setSshDirectory(new File(FS.DETECTED.userHome(), ".ssh"))
                        .build(null);

                sshTransport.setSshSessionFactory(new PassphraseAwareSessionFactory(factory, passphrase));
            }
        };
    }

    private static class PassphraseAwareSessionFactory extends SshdSessionFactory {
        private final SshdSessionFactory delegate;
        private final String passphrase;

        public PassphraseAwareSessionFactory(SshdSessionFactory delegate, String passphrase) {
            this.delegate = delegate;
            this.passphrase = passphrase;
        }

        @Override
        public SshdSession getSession(org.eclipse.jgit.transport.URIish uri,
                org.eclipse.jgit.transport.CredentialsProvider credentialsProvider, FS fs, int timeout)
                throws org.eclipse.jgit.errors.TransportException {

            org.eclipse.jgit.transport.CredentialsProvider provider = credentialsProvider;
            if (passphrase != null && !passphrase.isEmpty()) {
                provider = new org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider("git", passphrase);
            }
            return delegate.getSession(uri, provider, fs, timeout);
        }
    }
}
