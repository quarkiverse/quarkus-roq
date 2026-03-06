package io.quarkiverse.roq.editor.runtime.devui.git;

import java.util.List;

/**
 * Information about the Git repository status.
 */
public record GitStatusInfo(
        boolean upToDate,
        boolean hasUnpublished,
        boolean hasRemoteChanges,
        String branch,
        int ahead,
        int behind,
        List<String> pendingFiles,
        boolean authFailed) {
}
