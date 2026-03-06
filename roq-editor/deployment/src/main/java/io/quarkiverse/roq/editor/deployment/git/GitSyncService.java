package io.quarkiverse.roq.editor.deployment.git;

import java.util.List;

import io.quarkiverse.roq.editor.runtime.devui.git.GitStatusInfo;
import io.quarkiverse.roq.editor.runtime.devui.git.GitSyncResult;

/**
 * Interface for Git synchronization operations in the Roq Editor.
 */
public interface GitSyncService {

    GitStatusInfo getStatus(String passphrase, boolean skipFetch);

    GitSyncResult sync(String passphrase);

    GitSyncResult push(String passphrase);

    GitSyncResult publish(String message, String passphrase, List<String> filePaths);

    GitSyncResult publishAndSync(String message, String passphrase, List<String> filePaths);
}
