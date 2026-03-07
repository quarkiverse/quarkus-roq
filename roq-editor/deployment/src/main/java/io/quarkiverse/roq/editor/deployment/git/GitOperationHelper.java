package io.quarkiverse.roq.editor.deployment.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.StashApplyFailureException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig;
import io.quarkiverse.roq.editor.runtime.devui.git.GitSyncResult;

/**
 * Helper class for common Git operations like staging, finalizing state, and stashing.
 */
public class GitOperationHelper {

    private static final Logger LOG = Logger.getLogger(GitOperationHelper.class);

    private final RoqEditorConfig editorConfig;
    private final GitContentFilter contentFilter;

    public GitOperationHelper(RoqEditorConfig editorConfig, GitContentFilter contentFilter) {
        this.editorConfig = editorConfig;
        this.contentFilter = contentFilter;
    }

    /**
     * Stages all significant content changes and conflicting files.
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @throws GitAPIException if staging fails
     */
    public void stageChanges(Git git, Repository repository) throws GitAPIException {
        Status status = git.status().call();
        List<String> filesToAdd = new ArrayList<>(
                contentFilter.extractSignificantContentChanges(status, contentFilter.resolveWorkingPrefix(repository)));
        filesToAdd.addAll(status.getConflicting());

        if (!filesToAdd.isEmpty()) {
            for (String path : filesToAdd) {
                git.add().addFilepattern(path).call();
            }
        }
    }

    /**
     * Finalizes the repository state by continuing a rebase or creating a new commit.
     * Returns a GitSyncResult if the operation should stop early, null otherwise.
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @param commitMessage the commit message to use
     * @return a result if resolution is partial or fails, null if finalized successfully
     * @throws GitAPIException if Git operations fail
     */
    public GitSyncResult finalizeState(Git git, Repository repository, String commitMessage) throws GitAPIException {
        RepositoryState state = repository.getRepositoryState();
        if (state.isRebasing()) {
            try {
                RebaseResult res = git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call();
                if (res.getStatus() == RebaseResult.Status.NOTHING_TO_COMMIT) {
                    res = git.rebase().setOperation(RebaseCommand.Operation.SKIP).call();
                }
                if (res.getStatus() == RebaseResult.Status.STOPPED) {
                    return new GitSyncResult(false,
                            "Rebase stopped: conflicts in next commit. Resolve and click Publish again.", true,
                            new ArrayList<>(git.status().call().getConflicting()), false);
                }
            } catch (Exception e) {
                return new GitSyncResult(false, "Failed to continue rebase: " + e.getMessage(), false,
                        Collections.emptyList(), false);
            }
        } else if (state == RepositoryState.MERGING || !isClean(git)) {
            String msg = (commitMessage == null || commitMessage.isBlank()) ? editorConfig.sync().commitMessage().template()
                    : commitMessage;
            git.commit().setMessage(msg).call();
        }
        return null;
    }

    /**
     * Checks if there are any significant changes that need to be committed.
     *
     * @param git the Git instance
     * @return true if the repository status is clean
     * @throws GitAPIException if status check fails
     */
    public boolean isClean(Git git) throws GitAPIException {
        Status status = git.status().call();
        return status.isClean();
    }

    /**
     * Restores stashed changes after a sync operation, handling potential conflicts.
     *
     * @param git the Git instance
     * @param syncResult the result from the previous pull operation
     * @param msgRestore the log message for restoring stash
     * @return the updated result, potentially indicating conflicts during stash apply
     */
    public GitSyncResult restoreStash(Git git, GitSyncResult syncResult, String msgRestore) {
        try {
            LOG.debug(msgRestore);
            git.stashApply().call();
            git.stashDrop().setStashRef(0).call();
            return syncResult;
        } catch (StashApplyFailureException e) {
            LOG.warn("Conflicts detected while restoring local changes.");
            try {
                Status status = git.status().call();
                return new GitSyncResult(false,
                        "Conflicts detected while restoring local changes. Resolve them manually.", true,
                        new ArrayList<>(status.getConflicting()), false);
            } catch (GitAPIException ex) {
                return new GitSyncResult(false, "Conflicts detected in stash restore", true, Collections.emptyList(),
                        false);
            }
        } catch (Exception e) {
            LOG.error("Failed to restore stashed changes", e);
            return syncResult;
        }
    }

    /**
     * Analyzes a failed pull result and generates a descriptive error result.
     *
     * @param result the PullResult from JGit
     * @param git the Git instance
     * @return a GitSyncResult describing the failure and conflicting files
     * @throws Exception if status retrieval fails
     */
    public GitSyncResult handleFailedPull(PullResult result, Git git) throws Exception {
        Status status = git.status().call();
        if (!status.getConflicting().isEmpty())
            return new GitSyncResult(false, "Merge conflicts detected", true, new ArrayList<>(status.getConflicting()), false);
        if (result.getMergeResult() != null) {
            MergeResult mergeResult = result.getMergeResult();
            if (mergeResult.getMergeStatus() == MergeResult.MergeStatus.FAILED) {
                if (mergeResult.getFailingPaths() != null && !mergeResult.getFailingPaths().isEmpty()) {
                    List<String> failing = new ArrayList<>(mergeResult.getFailingPaths().keySet());
                    return new GitSyncResult(false, "Sync failed: local changes conflict.", false, failing, false);
                }
            }
            return new GitSyncResult(false, "Sync failed: " + mergeResult.getMergeStatus(), false, Collections.emptyList(),
                    false);
        }
        if (!status.isClean()) {
            List<String> dirty = new ArrayList<>(status.getModified());
            dirty.addAll(status.getUntracked());
            return new GitSyncResult(false, "Sync failed: local edits conflict.", false, dirty, false);
        }
        return new GitSyncResult(false, "Sync failed", false, Collections.emptyList(), false);
    }
}
