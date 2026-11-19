package io.quarkiverse.roq.editor.deployment.git;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig;
import io.quarkiverse.roq.editor.runtime.devui.RoqEditorConfig.SyncConfig.PrFlowConfig.CommitStrategy;
import io.quarkiverse.roq.editor.runtime.devui.git.GitSyncResult;

/**
 * Publish flow that routes edits through a content branch and a PR.
 */
public class GitPrFlowPublisher {

    private static final Logger LOG = Logger.getLogger(GitPrFlowPublisher.class);

    private static final String MSG_NOTHING_TO_PUBLISH = "Nothing to publish";
    private static final String MSG_PUSH_SUCCESS = "Changes pushed successfully";
    private static final String MSG_RESTORE_STASH_AFTER_SYNC = "Restoring local changes after sync.";

    private static final DateTimeFormatter BRANCH_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final Pattern PR_URL_PATTERN = Pattern.compile(
            "https?://\\S+?(?:pull/new/|pull-requests/new|merge_requests/new)\\S*");

    @FunctionalInterface
    public interface PullExecutor {
        GitSyncResult performPull(Git git, Repository repository);
    }

    @FunctionalInterface
    public interface DirectPublisher {
        GitSyncResult publish(Git git, Repository repository, String commitMessage, List<String> filePaths) throws Exception;
    }

    private final RoqEditorConfig editorConfig;
    private final GitOperationHelper operationHelper;
    private final GitCredentialHelper credentialHelper;
    private final String configuredPassphrase;
    private final PullExecutor pullExecutor;
    private final DirectPublisher directPublisher;

    public GitPrFlowPublisher(RoqEditorConfig editorConfig, GitOperationHelper operationHelper,
            GitCredentialHelper credentialHelper, String configuredPassphrase,
            PullExecutor pullExecutor, DirectPublisher directPublisher) {
        this.editorConfig = editorConfig;
        this.operationHelper = operationHelper;
        this.credentialHelper = credentialHelper;
        this.configuredPassphrase = configuredPassphrase;
        this.pullExecutor = pullExecutor;
        this.directPublisher = directPublisher;
    }

    /**
     * Publishes the staged edits by routing on the current branch:
     * <ul>
     * <li>Main: create content branch, commit, push with upstream tracking, surface PR link.</li>
     * <li>Content branch with remote ref still present: commit (or amend), push.</li>
     * <li>Content branch whose remote ref was pruned: stash, return to main, pull, pop, start a new cycle.</li>
     * <li>Any other branch: fall back to direct publish.</li>
     * </ul>
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @param commitMessage the commit message to use
     * @param filePaths the file paths to stage
     * @param branchNameOverride optional name for the content branch (used when starting a new cycle)
     * @return the result of the publish operation
     * @throws Exception if a Git operation fails
     */
    public GitSyncResult publish(Git git, Repository repository, String commitMessage, List<String> filePaths,
            String branchNameOverride) throws Exception {
        String mainBranch = resolveMainBranch(repository);
        String currentBranch = repository.getBranch();
        String prefix = editorConfig.sync().prFlow().contentBranchPrefix();

        if (!currentBranch.equals(mainBranch) && currentBranch.startsWith(prefix)) {
            fetchAndPrune(git, repository);
            if (remoteBranchExists(repository, currentBranch)) {
                return publishOnContentBranch(git, repository, commitMessage, filePaths, currentBranch);
            }
            GitSyncResult switched = switchBackToMain(git, repository, mainBranch);
            if (!switched.success()) {
                return switched;
            }
            currentBranch = mainBranch;
        }

        if (currentBranch.equals(mainBranch)) {
            return startContentBranchCycle(git, repository, commitMessage, filePaths, branchNameOverride);
        }

        LOG.debug("PR mode: current branch '" + currentBranch
                + "' is neither the main branch nor a content branch; falling back to direct publish");
        return directPublisher.publish(git, repository, commitMessage, filePaths);
    }

    /**
     * Stages pending edits, branches off main, commits, and pushes with upstream tracking.
     * Returns a "nothing to publish" result when the working tree has no significant changes.
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @param commitMessage the commit message to use
     * @param filePaths the file paths to stage
     * @param branchNameOverride optional explicit branch name; auto-generated when blank
     * @return the result of the push, including the PR creation URL when surfaced by the remote
     */
    private GitSyncResult startContentBranchCycle(Git git, Repository repository, String commitMessage,
            List<String> filePaths, String branchNameOverride) throws Exception {
        operationHelper.stageChanges(git, repository, filePaths);
        if (!operationHelper.hasStagedChanges(git) && repository.getRepositoryState() == RepositoryState.SAFE) {
            return new GitSyncResult(true, MSG_NOTHING_TO_PUBLISH, false, Collections.emptyList(), false);
        }

        String newBranch = resolveNewBranchName(repository, branchNameOverride);
        git.checkout().setCreateBranch(true).setName(newBranch).call();

        GitSyncResult stateResult = operationHelper.finalizeState(git, repository, commitMessage);
        if (stateResult != null) {
            return stateResult;
        }

        return pushContentBranch(git, repository, newBranch, false);
    }

    /**
     * Updates the existing content branch with a new commit, or amends the previous commit
     * when {@link CommitStrategy#AMEND} is configured and the branch already has commits past main.
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @param commitMessage the commit message to use
     * @param filePaths the file paths to stage
     * @param branchName the current content branch name
     * @return the result of the push
     */
    private GitSyncResult publishOnContentBranch(Git git, Repository repository, String commitMessage,
            List<String> filePaths, String branchName) throws Exception {
        operationHelper.stageChanges(git, repository, filePaths);

        CommitStrategy strategy = editorConfig.sync().prFlow().commitStrategy();
        boolean amend = strategy == CommitStrategy.AMEND
                && hasCommitsBeyond(repository, branchName, resolveMainBranch(repository));

        if (operationHelper.hasStagedChanges(git) || repository.getRepositoryState() == RepositoryState.MERGING) {
            String msg = (commitMessage == null || commitMessage.isBlank())
                    ? editorConfig.sync().commitMessage().template()
                    : commitMessage;
            git.commit().setMessage(msg).setAmend(amend).call();
        } else if (repository.getRepositoryState().isRebasing()) {
            GitSyncResult stateResult = operationHelper.finalizeState(git, repository, commitMessage);
            if (stateResult != null) {
                return stateResult;
            }
        } else {
            return new GitSyncResult(true, MSG_NOTHING_TO_PUBLISH, false, Collections.emptyList(), false);
        }

        return pushContentBranch(git, repository, branchName, amend);
    }

    /**
     * Pushes the named content branch to {@code origin}, optionally with force, and extracts the
     * PR creation URL from the remote messages when available.
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @param branchName the branch to push
     * @param force whether to force the push (required when amending)
     * @return the result including the PR creation URL when surfaced by the remote
     */
    private GitSyncResult pushContentBranch(Git git, Repository repository, String branchName, boolean force)
            throws Exception {
        Iterable<PushResult> results = configureTransport(
                git.push().setRemote("origin").setForce(force).add("refs/heads/" + branchName),
                repository).call();

        String prUrl = null;
        for (PushResult pushResult : results) {
            for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
                RemoteRefUpdate.Status updateStatus = update.getStatus();
                if (updateStatus != RemoteRefUpdate.Status.OK
                        && updateStatus != RemoteRefUpdate.Status.UP_TO_DATE) {
                    return new GitSyncResult(false,
                            "Push failed: " + updateStatus + " (" + update.getMessage() + ")",
                            false, Collections.emptyList(), false);
                }
            }
            String messages = pushResult.getMessages();
            if (prUrl == null && messages != null) {
                prUrl = extractPrCreationUrl(messages);
            }
        }

        configureBranchTracking(repository, branchName);
        return new GitSyncResult(true, MSG_PUSH_SUCCESS, false, Collections.emptyList(), false, prUrl, branchName);
    }

    /**
     * Returns the editor to the main branch after the content branch's remote ref disappears
     * (typically because the PR was merged or closed). Local edits are preserved via stash.
     *
     * @param git the Git instance
     * @param repository the JGit repository
     * @param mainBranch the resolved main branch name
     * @return the result of the pull after switching back
     */
    private GitSyncResult switchBackToMain(Git git, Repository repository, String mainBranch) throws Exception {
        boolean wasDirty = !git.status().call().isClean();
        if (wasDirty) {
            git.stashCreate().setIncludeUntracked(true).call();
        }

        git.checkout().setName(mainBranch).call();

        GitSyncResult pullResult = pullExecutor.performPull(git, repository);
        if (!pullResult.success()) {
            if (wasDirty) {
                operationHelper.restoreStash(git, pullResult, MSG_RESTORE_STASH_AFTER_SYNC);
            }
            return pullResult;
        }

        if (wasDirty) {
            return operationHelper.restoreStash(git, pullResult, MSG_RESTORE_STASH_AFTER_SYNC);
        }
        return pullResult;
    }

    private void fetchAndPrune(Git git, Repository repository) {
        try {
            configureTransport(git.fetch().setRemote("origin").setRemoveDeletedRefs(true), repository).call();
        } catch (Exception e) {
            LOG.debug("Fetch+prune failed (will continue with cached refs): " + e.getMessage());
        }
    }

    private boolean remoteBranchExists(Repository repository, String branchName) throws IOException {
        return repository.findRef("refs/remotes/origin/" + branchName) != null;
    }

    /**
     * Checks whether {@code branchName} contains at least one commit not reachable from
     * {@code baseBranch}. Used to decide whether the previous commit can be amended.
     *
     * @param repository the JGit repository
     * @param branchName the branch to inspect
     * @param baseBranch the base branch to compare against
     * @return true when the branch has commits past the base
     */
    private boolean hasCommitsBeyond(Repository repository, String branchName, String baseBranch) throws IOException {
        ObjectId branchHead = repository.resolve(branchName);
        ObjectId baseHead = repository.resolve(baseBranch);
        if (branchHead == null || baseHead == null) {
            return false;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(branchHead));
            walk.markUninteresting(walk.parseCommit(baseHead));
            return walk.iterator().hasNext();
        }
    }

    private String resolveMainBranch(Repository repository) throws IOException {
        return editorConfig.sync().prFlow().mainBranch()
                .filter(b -> !b.isBlank())
                .orElseGet(() -> detectDefaultBranch(repository));
    }

    private String detectDefaultBranch(Repository repository) {
        try {
            Ref head = repository.findRef("refs/remotes/origin/HEAD");
            if (head != null && head.isSymbolic()) {
                String target = head.getTarget().getName();
                String prefix = "refs/remotes/origin/";
                if (target.startsWith(prefix)) {
                    return target.substring(prefix.length());
                }
            }
            if (repository.findRef("refs/heads/main") != null) {
                return "main";
            }
            if (repository.findRef("refs/heads/master") != null) {
                return "master";
            }
        } catch (IOException ignored) {
        }
        return "main";
    }

    private String resolveNewBranchName(Repository repository, String override) throws IOException {
        String prefix = editorConfig.sync().prFlow().contentBranchPrefix();
        String base;
        if (override != null && !override.isBlank()) {
            String sanitized = override.replaceAll("[^A-Za-z0-9._/-]", "-");
            base = sanitized.startsWith(prefix) ? sanitized : prefix + sanitized;
        } else {
            base = prefix + "update-" + LocalDateTime.now().format(BRANCH_TIMESTAMP);
        }

        String candidate = base;
        int suffix = 2;
        while (branchNameTaken(repository, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean branchNameTaken(Repository repository, String name) throws IOException {
        return repository.findRef("refs/heads/" + name) != null
                || repository.findRef("refs/remotes/origin/" + name) != null;
    }

    private void configureBranchTracking(Repository repository, String branchName) throws IOException {
        StoredConfig config = repository.getConfig();
        if (config.getString("branch", branchName, "remote") == null) {
            config.setString("branch", branchName, "remote", "origin");
            config.setString("branch", branchName, "merge", "refs/heads/" + branchName);
            config.save();
        }
    }

    static String extractPrCreationUrl(String pushMessages) {
        if (pushMessages == null || pushMessages.isEmpty()) {
            return null;
        }
        Matcher matcher = PR_URL_PATTERN.matcher(pushMessages);
        return matcher.find() ? matcher.group() : null;
    }

    private <C extends TransportCommand<C, ?>> C configureTransport(C cmd, Repository repository) {
        cmd.setTransportConfigCallback(GitTransportHelper.createTransportCallback(configuredPassphrase));
        if (!GitTransportHelper.isSsh(repository)) {
            CredentialsProvider cp = credentialHelper.getCredentials(repository);
            if (cp != null) {
                cmd.setCredentialsProvider(cp);
            }
        }
        return cmd;
    }
}
