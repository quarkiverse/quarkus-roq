/**
 * Orchestrator for Git synchronization background tasks.
 * Handles polling for repository status and automatic sync/publish.
 *
 * SSH authentication is fully delegated to the server: it relies on the system SSH agent
 * (macOS Keychain, ssh-agent, Pageant) and, as a fallback, the EDITOR_SYNC_SSH_PASSPHRASE
 * environment variable. No passphrase is ever sent from the browser.
 */
export class SyncManager {
    /**
     * @param {Object} jsonRpc - The JSON-RPC service for Git operations
     * @param {Function} onStatusChange - Callback fired when Git status is updated
     * @param {Object} syncConfig - Git sync configuration from RoqEditorConfig
     * @param {Object} callbacks - Collection of UI callbacks
     * @param {Function} callbacks.onNotification - Show a UI notification (message, type)
     * @param {Function} callbacks.onConflict - Show the conflict resolution dialog (files)
     */
    constructor(jsonRpc, onStatusChange, syncConfig, callbacks) {
        this.jsonRpc = jsonRpc;
        this.onStatusChange = onStatusChange;
        this.syncConfig = syncConfig;
        this.onNotification = callbacks.onNotification;
        this.onConflict = callbacks.onConflict;
        this.onBusy = callbacks.onBusy || (() => {});

        this.status = null;
        this.authError = false;
        this.intervalId = null;
        this.pollingCount = 0;
        this._refreshingPromise = null;
        this._lastRefreshSkipFetch = true;
    }

    /**
     * Updates the JSON-RPC service reference.
     * Useful when the component re-connects and a new bus is provided.
     * @param {Object} jsonRpc - The new JSON-RPC service
     */
    updateJsonRpc(jsonRpc) {
        this.jsonRpc = jsonRpc;
        this._refreshingPromise = null;
    }

    /**
     * Starts the background polling and automation timers.
     * Status updates occur every 10 seconds. Full network fetches are performed periodically.
     */
    start() {
        if (this.intervalId) return;

        const pollingIntervalMs = 10 * 1000;

        this.lastAutoSyncTime = Date.now();
        this.lastAutoPublishTime = Date.now();

        this.refreshStatus(false);

        this.intervalId = setInterval(async () => {
            const skipFetch = this.status?.authFailed || (this.pollingCount % 6 !== 0);

            this.pollingCount++;
            const status = await this.refreshStatus(skipFetch);

            if (!status || status.hasConflicts || status.authFailed) return;

            const now = Date.now();

            const autoSyncConfig = this.syncConfig?.autoSync;
            if (autoSyncConfig?.enabled === true && status.behind > 0) {
                const syncIntervalMs = (autoSyncConfig.intervalSeconds || 60) * 1000;
                const timeSinceLastSync = now - this.lastAutoSyncTime;
                if (timeSinceLastSync >= syncIntervalMs) {
                    try {
                        await this.manualSync();
                        this.lastAutoSyncTime = now;
                    } catch (e) {
                        console.warn("[SyncManager] Auto-sync failed:", e.message);
                    }
                }
            }

            const autoPublishConfig = this.syncConfig?.autoPublish;
            if (autoPublishConfig?.enabled === true && status.hasUnpublished) {
                const publishIntervalMs = (autoPublishConfig.intervalSeconds || 300) * 1000;
                const timeSinceLastPublish = now - this.lastAutoPublishTime;

                if (timeSinceLastPublish >= publishIntervalMs) {
                    try {
                        const message = this.syncConfig?.commitMessage?.template || "Auto-update via Roq Editor";
                        await this.manualPublish(message, status.pendingFiles || []);
                        this.lastAutoPublishTime = now;
                    } catch (e) {
                        console.warn("[SyncManager] Auto-publish failed:", e.message);
                    }
                }
            }
        }, pollingIntervalMs);
    }

    /**
     * Stops background polling and clears all timers.
     */
    stop() {
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
        this._refreshingPromise = null;
    }

    /**
     * Optimistically marks the current status as dirty (content not published).
     * Used after local file operations like create, save, or delete.
     */
    markAsDirty() {
        if (this.status && !this.status.hasUnpublished) {
            this.status = {
                ...this.status,
                hasUnpublished: true,
                upToDate: false
            };
            this.onStatusChange(this.status);
        }
    }

    /**
     * Refreshes the Git repository status from the backend.
     * @param {boolean} skipFetch - If true, only check local branch tracking without network fetch
     * @returns {Promise<Object>} The updated status information
     */
    async refreshStatus(skipFetch = true) {
        if (this._refreshingPromise && (skipFetch || !this._lastRefreshSkipFetch)) {
            return await this._refreshingPromise;
        }

        if (this._refreshingPromise) {
            await this._refreshingPromise;
        }

        this._lastRefreshSkipFetch = skipFetch;
        this._refreshingPromise = (async () => {
            if (!skipFetch) this.onBusy(true);
            try {
                const response = await this.jsonRpc.getSyncStatus({ skipFetch });
                const statusInfo = response.result;
                if (!statusInfo) return;

                this.status = statusInfo;

                if (statusInfo.authFailed && statusInfo.isSsh) {
                    this._notifyAuthError();
                } else if (!statusInfo.authFailed) {
                    this.authError = false;
                }

                this.onStatusChange(statusInfo);
                return statusInfo;
            } catch (error) {
                console.error("[SyncManager] Failed to refresh Git status", error);
            } finally {
                if (!skipFetch) this.onBusy(false);
                this._refreshingPromise = null;
            }
        })();

        return await this._refreshingPromise;
    }

    /**
     * Notifies the user once about an SSH authentication failure, pointing to the
     * server-side configuration options. Avoids spamming on every poll.
     * @private
     */
    _notifyAuthError() {
        if (this.authError) return;
        this.authError = true;
        this.onNotification(
            "SSH authentication failed. Make sure your key is loaded in your ssh-agent, " +
            "or set the EDITOR_SYNC_SSH_PASSPHRASE environment variable for a passphrase-protected key.",
            'error');
    }

    /**
     * Handles the result of a Git operation, showing notifications and refreshing status.
     * @param {Object} result The operation result
     * @param {string} successMessage Message to show on success
     * @param {string} opType The operation type ('sync', 'publish', 'publishAndSync')
     * @param {boolean} fullRefresh Whether to perform a full status refresh (with fetch)
     */
    async _handleOperationResult(result, successMessage, opType, fullRefresh = false) {
        if (result?.success) {
            this.onNotification(successMessage, 'success');
            if (this.status) {
                const updated = { ...this.status };
                if (opType === 'publish' || opType === 'publishAndSync') {
                    updated.hasUnpublished = false;
                    updated.ahead = 0;
                }
                if (opType === 'sync' || opType === 'publishAndSync') {
                    updated.behind = 0;
                }
                updated.upToDate = !updated.hasUnpublished && updated.ahead === 0 && updated.behind === 0
                        && !updated.hasConflicts;
                this.status = updated;
                this.onStatusChange(this.status);
            }
            await this.refreshStatus(!fullRefresh);
        } else if (result?.hasConflicts) {
            await this.onConflict(result.conflictFiles);
            await this.refreshStatus(true);
        } else if (result?.authFailed) {
            this._notifyAuthError();
            await this.refreshStatus(true);
        } else if (result) {
            this.onNotification(result.message || "Operation failed", 'error');
            await this.refreshStatus(true);
        }
        return result;
    }

    /**
     * Manually triggers a Git Sync (pull).
     * @returns {Promise<Object>} Result of the operation
     */
    async manualSync() {
        const result = await this.jsonRpc.syncContent().then(r => r.result);
        return this._handleOperationResult(result, 'Content synchronized successfully', 'sync', true);
    }

    /**
     * Manually triggers a Git Publish (commit + push).
     * @param {string} message - The commit message
     * @param {string[]} filePaths - List of files to stage and commit
     * @returns {Promise<Object>} Result of the operation
     */
    async manualPublish(message, filePaths) {
        const result = await this.jsonRpc.publishContent({ message, filePaths: filePaths ?? [] }).then(r => r.result);
        return this._handleOperationResult(result, 'Content published successfully', 'publish', true);
    }

    /**
     * Triggers a publish followed by a sync.
     * @param {string} message - The commit message
     * @param {string[]} filePaths - List of files to publish
     * @returns {Promise<Object>} Combined result
     */
    async publishAndSync(message, filePaths) {
        const result = await this.jsonRpc.publishAndSync({ message, filePaths: filePaths ?? [] }).then(r => r.result);
        return this._handleOperationResult(result, 'Content published and synchronized successfully', 'publishAndSync', true);
    }
}
