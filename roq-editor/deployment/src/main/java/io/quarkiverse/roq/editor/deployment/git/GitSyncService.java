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
     * @param passphrase the SSH passphrase if required for remote operations
     * @param skipFetch if true, skip network fetch and return status based on local tracking branches
     * @return an object containing detailed Git status information
     */
    GitStatusInfo getStatus(String passphrase, boolean skipFetch);

    /**
     * Synchronizes the local repository with the remote (git pull).
     * This may involve stashing local changes to ensure a clean sync.
     *
     * @param passphrase the SSH passphrase if required
     * @return the result of the synchronization operation
     */
    GitSyncResult sync(String passphrase);

    /**
     * Pushes local commits to the remote repository.
     *
     * @param passphrase the SSH passphrase if required
     * @return the result of the push operation
     */
    GitSyncResult push(String passphrase);

    /**
     * Publishes local changes by staging, committing, and pushing them.
     * If the local branch is behind, it will attempt a smart merge before pushing.
     *
     * @param message the commit message
     * @param passphrase the SSH passphrase if required
     * @param filePaths (optional) list of files to publish
     * @return the result of the publish operation
     */
    GitSyncResult publish(String message, String passphrase, List<String> filePaths);

    /**
     * Publishes local changes and then performs a full synchronization.
     *
     * @param message the commit message
     * @param passphrase the SSH passphrase if required
     * @param filePaths (optional) list of files to publish
     * @return the combined result of publish and sync operations
     */
    GitSyncResult publishAndSync(String message, String passphrase, List<String> filePaths);
}
