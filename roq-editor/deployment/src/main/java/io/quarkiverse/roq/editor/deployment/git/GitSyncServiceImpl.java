package io.quarkiverse.roq.editor.deployment.git;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
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

    private static final String MSG_STASH_BEFORE_SYNC = "Stashing local changes before sync.";
    private static final String MSG_RESTORE_STASH_AFTER_SYNC = "Restoring local changes after sync.";
    private static final String MSG_AUTO_MERGE_DURING_PUBLISH = "Performing auto-merge during publish due to remote changes.";
    private static final String MSG_SYNC_SUCCESS = "Synchronized successfully with remote";
    private static final String MSG_PUSH_SUCCESS = "Changes pushed successfully";
    private static final String MSG_REPO_NOT_FOUND = "Git repository not found";
    private static final String MSG_NO_GIT_REPO = "no-git-repo";

    private final File rootDirectory;
    private final GitContentFilter contentFilter;
    private final GitOperationHelper operationHelper;

    /**
     * Creates a new instance of GitSyncServiceImpl.
     *
     * @param editorConfig the editor configuration
     * @param siteConfig the site configuration
     * @param rootDirectory the root directory of the project
     */
    public GitSyncServiceImpl(RoqEditorConfig editorConfig, RoqSiteConfig siteConfig, File rootDirectory) {
        this.rootDirectory = rootDirectory;
        this.contentFilter = new GitContentFilter(siteConfig, rootDirectory);
        this.operationHelper = new GitOperationHelper(editorConfig, contentFilter);
    }

    /**
     * Retrieves the current status of the Git repository including local changes,
     * remote tracking status (ahead/behind), and repository state.
     *
     * @param passphrase the SSH passphrase if required
     * @param skipFetch if true, skip the network fetch operation
     * @return an object containing detailed Git status information
     */
    @Override
    public GitStatusInfo getStatus(String passphrase, boolean skipFetch) {
        try (Repository repository = openRepository()) {
            if (repository == null) {
                return new GitStatusInfo(false, false, false, MSG_NO_GIT_REPO, 0, 0, Collections.emptyList(), false, false,
                        "SAFE", Collections.emptyList(), false);
            }

            String currentBranch = repository.getBranch();
            RepositoryState repoState = repository.getRepositoryState();
            boolean isSsh = GitTransportHelper.isSsh(repository);

            try (Git git = new Git(repository)) {
                Status status = git.status().call();
                String rootPrefix = contentFilter.resolveWorkingPrefix(repository);
                List<String> contentChanges = contentFilter.extractSignificantContentChanges(status, rootPrefix);
                boolean hasUnpublished = !contentChanges.isEmpty();
                List<String> conflictFiles = new ArrayList<>(status.getConflicting());
                boolean hasConflicts = !conflictFiles.isEmpty() || repoState != RepositoryState.SAFE;

                boolean authRequired = GitTransportHelper.isAuthRequired(repository, passphrase);
                boolean authFailed = false;

                if (!skipFetch && !authRequired) {
                    authFailed = tryFetch(git, passphrase, isSsh);
                }

                BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, currentBranch);
                int aheadCount = (trackingStatus != null) ? trackingStatus.getAheadCount() : 0;
                int behindCount = (trackingStatus != null) ? trackingStatus.getBehindCount() : 0;

                boolean isUpToDate = aheadCount == 0 && behindCount == 0 && !hasUnpublished && repoState == RepositoryState.SAFE
                        && !hasConflicts && !authFailed && !authRequired;

                return new GitStatusInfo(isUpToDate, hasUnpublished, behindCount > 0, currentBranch,
                        aheadCount, behindCount, contentChanges, authFailed || authRequired, hasConflicts, repoState.name(),
                        conflictFiles, isSsh);
            }
        } catch (Exception e) {
            return handleStatusFailure(e);
        }
    }

    /**
     * Synchronizes the local repository with the remote (git pull).
     * If there are local changes, it uses stash to ensure a clean pull.
     *
     * @param passphrase the SSH passphrase if required
     * @return the result of the synchronization operation
     */
    @Override
    public GitSyncResult sync(String passphrase) {
        try (Repository repository = openRepository()) {
            if (repository == null) {
                return new GitSyncResult(false, MSG_REPO_NOT_FOUND, false, Collections.emptyList(), false);
            }

            try (Git git = new Git(repository)) {
                if (GitTransportHelper.isAuthRequired(repository, passphrase)) {
                    return new GitSyncResult(false, GitTransportHelper.ERR_AUTH_REQUIRED, false, Collections.emptyList(), true);
                }

                boolean wasDirty = !git.status().call().isClean();
                if (wasDirty) {
                    LOG.debug(MSG_STASH_BEFORE_SYNC);
                    git.stashCreate().setIncludeUntracked(true).call();
                }

                GitSyncResult syncResult = performPull(git, repository, passphrase);

                if (wasDirty) {
                    syncResult = operationHelper.restoreStash(git, syncResult, MSG_RESTORE_STASH_AFTER_SYNC);
                }
                return syncResult;
            }
        } catch (Exception e) {
            return handleSyncFailure(e);
        }
    }

    /**
     * Pushes local commits to the remote repository.
     *
     * @param passphrase the SSH passphrase if required
     * @return the result of the push operation
     */
    @Override
    public GitSyncResult push(String passphrase) {
        try (Repository repository = openRepository()) {
            if (repository == null) {
                return new GitSyncResult(false, MSG_REPO_NOT_FOUND, false, Collections.emptyList(), false);
            }

            try (Git git = new Git(repository)) {
                if (GitTransportHelper.isAuthRequired(repository, passphrase)) {
                    return new GitSyncResult(false, GitTransportHelper.ERR_AUTH_REQUIRED, false, Collections.emptyList(), true);
                }

                RepositoryState state = repository.getRepositoryState();
                if (state != RepositoryState.SAFE) {
                    return new GitSyncResult(false,
                            "Push blocked: Repository is in state " + state + ". Please finalize your merge/rebase first.",
                            false,
                            Collections.emptyList(), false);
                }

                Iterable<PushResult> results = git.push().setRemote("origin")
                        .setTransportConfigCallback(GitTransportHelper.createTransportCallback(passphrase)).call();

                for (PushResult pushResult : results) {
                    for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
                        if (update.getStatus() != RemoteRefUpdate.Status.OK
                                && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                            return new GitSyncResult(false,
                                    "Push failed: " + update.getStatus() + " (" + update.getMessage() + ")",
                                    false, Collections.emptyList(), false);
                        }
                    }
                }

                return new GitSyncResult(true, MSG_PUSH_SUCCESS, false, Collections.emptyList(), false);
            }
        } catch (Exception e) {
            return handlePushFailure(e);
        }
    }

    /**
     * Publishes changes by staging, committing, and pushing to the remote repository.
     *
     * @param commitMessage the commit message
     * @param passphrase the SSH passphrase if required
     * @param filePaths the file paths to publish
     * @return the result of the publish operation
     */
    @Override
    public GitSyncResult publish(String commitMessage, String passphrase, List<String> filePaths) {
        try (Repository repository = openRepository()) {
            if (repository == null) {
                return new GitSyncResult(false, MSG_REPO_NOT_FOUND, false, Collections.emptyList(), false);
            }

            try (Git git = new Git(repository)) {
                if (GitTransportHelper.isAuthRequired(repository, passphrase)) {
                    return new GitSyncResult(false, GitTransportHelper.ERR_AUTH_REQUIRED, false, Collections.emptyList(), true);
                }

                operationHelper.stageChanges(git, repository);

                GitSyncResult stateResult = operationHelper.finalizeState(git, repository, commitMessage);
                if (stateResult != null) {
                    return stateResult;
                }

                tryFetch(git, passphrase, GitTransportHelper.isSsh(repository));
                GitSyncResult syncResult = smartSyncIfBehind(repository, passphrase);
                if (syncResult != null && !syncResult.success()) {
                    return syncResult;
                }

                RepositoryState state = repository.getRepositoryState();
                if (state != RepositoryState.SAFE) {
                    return new GitSyncResult(true,
                            "Partial resolution successful (State: " + state + "). Continue resolving/publishing.", false,
                            Collections.emptyList(), false);
                }

                return push(passphrase);
            }
        } catch (Exception e) {
            return handlePublishFailure(e);
        }
    }

    /**
     * Publishes changes and then synchronizes with the remote repository.
     *
     * @param commitMessage the commit message
     * @param passphrase the SSH passphrase if required
     * @param filePaths the file paths to publish
     * @return the result of the publish and sync operation
     */
    @Override
    public GitSyncResult publishAndSync(String commitMessage, String passphrase, List<String> filePaths) {
        GitSyncResult publishResult = publish(commitMessage, passphrase, filePaths);
        if (!publishResult.success())
            return publishResult;
        return sync(passphrase);
    }

    /**
     * Attempts to fetch from remote, handling authentication errors specifically for SSH.
     *
     * @param git the Git instance
     * @param passphrase the SSH passphrase
     * @param isSsh true if the remote is an SSH URL
     * @return true if authentication failed
     */
    private boolean tryFetch(Git git, String passphrase, boolean isSsh) {
        try {
            performFetch(git, passphrase);
            return false;
        } catch (Exception fetchEx) {
            if (GitTransportHelper.isAuthenticationError(fetchEx) && isSsh) {
                LOG.warn("SSH authentication failed during fetch poll: " + fetchEx.getMessage());
                return true;
            }
            return false;
        }
    }

    /**
     * Handles failures during status retrieval, attempting to return as much information as possible.
     *
     * @param e the exception that occurred
     * @return an object containing as much status information as could be retrieved
     */
    private GitStatusInfo handleStatusFailure(Exception e) {
        boolean isSsh = false;
        try (Repository repository = openRepository()) {
            if (repository != null) {
                isSsh = GitTransportHelper.isSsh(repository);
            }
        } catch (Exception ignored) {
        }

        boolean authFailed = GitTransportHelper.isAuthenticationError(e) && isSsh;
        if (authFailed) {
            LOG.warn("SSH authentication failed: " + e.getMessage());
        } else if (!GitTransportHelper.isAuthenticationError(e)) {
            LOG.error("Failed to retrieve Git repository status", e);
        }

        try (Repository repository = openRepository()) {
            if (repository != null) {
                try (Git git = new Git(repository)) {
                    Status status = git.status().call();
                    String currentBranch = repository.getBranch();
                    List<String> conflictFiles = new ArrayList<>(status.getConflicting());
                    return new GitStatusInfo(false, false, false, currentBranch, 0, 0, Collections.emptyList(), authFailed,
                            !conflictFiles.isEmpty(), repository.getRepositoryState().name(), conflictFiles, isSsh);
                }
            }
            return new GitStatusInfo(false, false, false, "", 0, 0, Collections.emptyList(), authFailed, false, "ERROR",
                    Collections.emptyList(), isSsh);
        } catch (Exception inner) {
            return new GitStatusInfo(false, false, false, "", 0, 0, Collections.emptyList(), authFailed, false, "ERROR",
                    Collections.emptyList(), isSsh);
        }
    }

    /**
     * Performs a git pull and handles the result or potential authentication errors.
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @param passphrase the SSH passphrase
     * @return the result of the pull operation
     */
    private GitSyncResult performPull(Git git, Repository repository, String passphrase) {
        try {
            PullResult pullResult = git.pull()
                    .setRemote("origin")
                    .setRemoteBranchName(repository.getBranch())
                    .setTransportConfigCallback(GitTransportHelper.createTransportCallback(passphrase))
                    .call();

            if (!pullResult.isSuccessful()) {
                return operationHelper.handleFailedPull(pullResult, git);
            }
            return new GitSyncResult(true, MSG_SYNC_SUCCESS, false, Collections.emptyList(), false);
        } catch (Exception e) {
            boolean isSsh = GitTransportHelper.isSsh(repository);
            if (GitTransportHelper.isAuthenticationError(e) && isSsh) {
                LOG.warn("Sync authentication failed: " + e.getMessage());
                return new GitSyncResult(false, GitTransportHelper.ERR_AUTH_FAILED, false, Collections.emptyList(), true);
            }
            LOG.error("Content synchronization failed", e);
            return new GitSyncResult(false, "Sync failed: " + e.getMessage(), false, Collections.emptyList(), false);
        }
    }

    /**
     * General failure handler for sync operations.
     *
     * @param e the exception that occurred
     * @return a result object describing the failure
     */
    private GitSyncResult handleSyncFailure(Exception e) {
        boolean isSsh = false;
        try (Repository repository = openRepository()) {
            if (repository != null) {
                isSsh = GitTransportHelper.isSsh(repository);
            }
        } catch (Exception ignored) {
        }
        return new GitSyncResult(false, "Sync operation failed: " + e.getMessage(), false, Collections.emptyList(),
                GitTransportHelper.isAuthenticationError(e) && isSsh);
    }

    /**
     * Handles failures during push operations.
     */
    private GitSyncResult handlePushFailure(Exception e) {
        boolean isSsh = false;
        try (Repository repository = openRepository()) {
            if (repository != null) {
                isSsh = GitTransportHelper.isSsh(repository);
            }
        } catch (Exception ignored) {
        }
        boolean isAuth = GitTransportHelper.isAuthenticationError(e) && isSsh;
        if (isAuth) {
            LOG.warn("Push authentication failed: " + e.getMessage());
        } else {
            LOG.error("Push operation failed", e);
        }
        return new GitSyncResult(false, "Push error: " + e.getMessage(), false, Collections.emptyList(), isAuth);
    }

    /**
     * Performs a sync operation if the local repository is behind the remote.
     */
    private GitSyncResult smartSyncIfBehind(Repository repository, String passphrase) throws IOException {
        BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, repository.getBranch());
        int behindCount = (trackingStatus != null) ? trackingStatus.getBehindCount() : 0;
        if (behindCount > 0 && repository.getRepositoryState() == RepositoryState.SAFE) {
            LOG.debug(MSG_AUTO_MERGE_DURING_PUBLISH);
            return sync(passphrase);
        }
        return null;
    }

    /**
     * Handles failures during publish operations.
     */
    private GitSyncResult handlePublishFailure(Exception e) {
        boolean isSsh = false;
        try (Repository repository = openRepository()) {
            if (repository != null) {
                isSsh = GitTransportHelper.isSsh(repository);
            }
        } catch (Exception ignored) {
        }
        boolean isAuth = GitTransportHelper.isAuthenticationError(e) && isSsh;
        if (isAuth) {
            LOG.warn("Publish authentication failed: " + e.getMessage());
        } else {
            LOG.error("Publishing operation failed", e);
        }
        return new GitSyncResult(false, "Publish error: " + e.getMessage(), false, Collections.emptyList(), isAuth);
    }

    /**
     * Opens the Git repository in the working directory.
     *
     * @return the JGit repository or null if not found
     * @throws IOException if an error occurs while opening the repository
     */
    private Repository openRepository() throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder().readEnvironment().findGitDir(this.rootDirectory);
        if (builder.getGitDir() == null)
            return null;
        return builder.build();
    }

    /**
     * Performs a fetch operation from the remote repository.
     *
     * @param git the Git instance
     * @param passphrase the SSH passphrase
     * @throws Exception if an error occurs during fetch
     */
    private void performFetch(Git git, String passphrase) throws Exception {
        git.fetch().setTransportConfigCallback(GitTransportHelper.createTransportCallback(passphrase)).call();
    }
}