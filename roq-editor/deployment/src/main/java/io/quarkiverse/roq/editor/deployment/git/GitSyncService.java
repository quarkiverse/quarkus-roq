package io.quarkiverse.roq.editor.deployment.git;

import java.util.List;

import io.quarkiverse.roq.editor.runtime.devui.git.GitStatusInfo;
import io.quarkiverse.roq.editor.runtime.devui.git.GitSyncResult;

/**
 * Interface for Git synchronization operations in the Roq Editor.
 * This service provides methods to retrieve the repository status,
 * sync with remote, and publish local changes.
 */
public interface GitSyncService {

    /**
     * Retrieves the current status of the Git repository.
     *
     * @param skipFetch if true, skip network fetch and return status based on local tracking branches
     * @return an object containing detailed Git status information
     */
    GitStatusInfo getStatus(boolean skipFetch);

    /**
     * Synchronizes the local repository with the remote (git pull).
     * This may involve stashing local changes to ensure a clean sync.
     *
     * @return the result of the synchronization operation
     */
    GitSyncResult sync();

    /**
     * Pushes local commits to the remote repository.
     *
     * @return the result of the push operation
     */
    GitSyncResult push();

    /**
     * Publishes local changes by staging, committing, and pushing them.
     * If the local branch is behind, it will attempt a smart merge before pushing.
     *
     * @param message the commit message
     * @param filePaths (optional) list of files to publish
     * @return the result of the publish operation
     */
    default GitSyncResult publish(String message, List<String> filePaths) {
        return publish(message, filePaths, null);
    }

    /**
     * Publishes local changes by staging, committing, and pushing them.
     * <p>
     * When PR mode is active and the current branch is the main branch, a new content branch
     * is created. The caller may supply {@code branchNameOverride} to use a specific name
     * (sanitised against the configured prefix); when {@code null}, a name is auto-generated.
     *
     * @param message the commit message
     * @param filePaths (optional) list of files to publish
     * @param branchNameOverride (optional) name for the new content branch when starting a PR cycle
     * @return the result of the publish operation
     */
    GitSyncResult publish(String message, List<String> filePaths, String branchNameOverride);

    /**
     * Publishes local changes and then performs a full synchronization.
     *
     * @param message the commit message
     * @param filePaths (optional) list of files to publish
     * @return the combined result of publish and sync operations
     */
    default GitSyncResult publishAndSync(String message, List<String> filePaths) {
        return publishAndSync(message, filePaths, null);
    }

    /**
     * Publishes local changes (optionally creating a content branch in PR mode) and then
     * performs a full synchronization.
     *
     * @param message the commit message
     * @param filePaths (optional) list of files to publish
     * @param branchNameOverride (optional) name for the new content branch when starting a PR cycle
     * @return the combined result of publish and sync operations
     */
    GitSyncResult publishAndSync(String message, List<String> filePaths, String branchNameOverride);
}
