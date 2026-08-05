package io.quarkiverse.roq.editor.runtime.devui.git;

import java.util.List;

/**
 * Result of a Git synchronization operation.
 * <p>
 * {@code prCreationUrl} and {@code targetBranch} are populated by the PR-based publish flow
 * to let the UI surface a clickable "Open PR" link and confirm which branch received the push.
 * Both remain {@code null} for sync, direct-mode publish, and failure results.
 */
public record GitSyncResult(
        boolean success,
        String message,
        boolean hasConflicts,
        List<String> conflictFiles,
        boolean authFailed,
        String prCreationUrl,
        String targetBranch) {

    public GitSyncResult(boolean success, String message, boolean hasConflicts, List<String> conflictFiles,
            boolean authFailed) {
        this(success, message, hasConflicts, conflictFiles, authFailed, null, null);
    }
}
