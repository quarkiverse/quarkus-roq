package io.quarkiverse.roq.editor.runtime.devui.git;

import java.util.List;

/**
 * Result of a Git synchronization operation.
 */
public record GitSyncResult(
        boolean success,
        String message,
        boolean hasConflicts,
        List<String> conflictFiles,
        boolean authFailed) {
}
