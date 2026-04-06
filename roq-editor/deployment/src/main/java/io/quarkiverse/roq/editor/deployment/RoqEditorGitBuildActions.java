package io.quarkiverse.roq.editor.deployment;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.quarkiverse.roq.editor.deployment.git.GitSyncService;
import io.quarkus.devui.spi.buildtime.BuildTimeActionBuildItem;

/**
 * Helper to register Git actions for the Dev UI JSON-RPC.
 */
public class RoqEditorGitBuildActions {

    /**
     * Registers all Git synchronization actions.
     *
     * @param gitService the GitSyncService to use
     * @return the BuildTimeActionBuildItem with registered actions
     */
    public static BuildTimeActionBuildItem register(GitSyncService gitService) {
        BuildTimeActionBuildItem actions = new BuildTimeActionBuildItem();

        actions.actionBuilder().methodName("getSyncStatus").function(parameters -> {
            String passphrase = parseString(parameters.get("passphrase"));
            boolean skipFetch = parseBoolean(parameters.get("skipFetch"));
            return CompletableFuture.supplyAsync(() -> gitService.getStatus(passphrase, skipFetch));
        }).build();

        actions.actionBuilder().methodName("syncContent").function(parameters -> {
            String passphrase = parseString(parameters.get("passphrase"));
            return CompletableFuture.supplyAsync(() -> gitService.sync(passphrase));
        }).build();

        actions.actionBuilder().methodName("publishContent").function(parameters -> {
            String commitMessage = parseString(parameters.get("message"));
            String passphrase = parseString(parameters.get("passphrase"));
            List<String> filePaths = extractList(parameters.get("filePaths"));
            return CompletableFuture.supplyAsync(() -> gitService.publish(commitMessage, passphrase, filePaths));
        }).build();

        actions.actionBuilder().methodName("publishAndSync").function(parameters -> {
            String commitMessage = parseString(parameters.get("message"));
            String passphrase = parseString(parameters.get("passphrase"));
            List<String> filePaths = extractList(parameters.get("filePaths"));
            return CompletableFuture.supplyAsync(() -> gitService.publishAndSync(commitMessage, passphrase, filePaths));
        }).build();

        return actions;
    }

    private static String parseString(Object value) {
        if (value == null)
            return null;
        String str = String.valueOf(value);
        return (str.isEmpty() || "null".equals(str)) ? null : str;
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean)
            return (boolean) value;
        if (value instanceof String)
            return Boolean.parseBoolean((String) value);
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractList(Object value) {
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }
}
