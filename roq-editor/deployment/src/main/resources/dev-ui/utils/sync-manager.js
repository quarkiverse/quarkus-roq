/**
 * Orchestrator for Git synchronization background tasks and authentication management.
 * Handles polling for repository status, automatic sync/publish, and SSH passphrase flows.
 */
export class SyncManager {
    /**
     * @param {Object} jsonRpc - The JSON-RPC service for Git operations
     * @param {Function} onStatusChange - Callback fired when Git status is updated
     * @param {Object} syncConfig - Git sync configuration from RoqEditorConfig
     * @param {Object} callbacks - Collection of UI callbacks
     * @param {Function} callbacks.onPassphraseRequired - Trigger the SSH passphrase dialog
     * @param {Function} callbacks.onNotification - Show a UI notification (message, type)
     * @param {Function} callbacks.onConflict - Show the conflict resolution dialog (files)
     */
    constructor(jsonRpc, onStatusChange, syncConfig, callbacks) {
        this.jsonRpc = jsonRpc;
        this.onStatusChange = onStatusChange;
        this.syncConfig = syncConfig;
        this.onPassphraseRequired = callbacks.onPassphraseRequired;
        this.onNotification = callbacks.onNotification;
        this.onConflict = callbacks.onConflict;
        this.onBusy = callbacks.onBusy || (() => {});
        
        this.status = null;
        this.passphrase = null;
        this.authError = false; 
        this.intervalId = null;
        this.pollingCount = 0;
        this._refreshingPromise = null;
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
            const isAuthBlocked = this.status?.authFailed && !this.passphrase;
            const skipFetch = isAuthBlocked || (this.pollingCount % 6 !== 0);
            
            this.pollingCount++;
            const status = await this.refreshStatus(skipFetch);
            
            if (!status || status.hasConflicts) return;

            const now = Date.now();

            const autoSyncConfig = this.syncConfig?.autoSync;
            if (autoSyncConfig?.enabled === true && status.behind > 0) {
                const syncIntervalMs = (autoSyncConfig.intervalSeconds || 60) * 1000;
                const timeSinceLastSync = now - this.lastAutoSyncTime;
                if (timeSinceLastSync >= syncIntervalMs) {
                    try {
                        if (!status.isSsh || this.passphrase) {
                            await this.manualSync();
                            this.lastAutoSyncTime = now;
                        }
                    } catch (e) {
                        console.warn("[SyncManager] Auto-sync failed:", e.message);
                    }
                }
            }

            const autoPublishConfig = this.syncConfig?.autoPublish;
            if (autoPublishConfig?.enabled === true) {
                if (status.hasUnpublished) {
                    const publishIntervalMs = (autoPublishConfig.intervalSeconds || 300) * 1000;
                    const timeSinceLastPublish = now - this.lastAutoPublishTime;
                    
                    if (timeSinceLastPublish >= publishIntervalMs) {
                        try {
                            if (!status.isSsh || this.passphrase) {
                                const message = this.syncConfig?.commitMessage?.template || "Auto-update via Roq Editor";
                                await this.manualPublish(message, status.pendingFiles || []);
                                this.lastAutoPublishTime = now;
                            }
                        } catch (e) {
                            console.warn("[SyncManager] Auto-publish failed:", e.message);
                        }
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
        if (this._refreshingPromise) {
            return await this._refreshingPromise;
        }

        this._refreshingPromise = (async () => {
            if (!skipFetch) this.onBusy(true);
            try {
                const response = await this.jsonRpc.getSyncStatus({ 
                    passphrase: this.passphrase, 
                    skipFetch 
                });
                const statusInfo = response.result;
                if (!statusInfo) return;

                this.status = statusInfo;
                
                if (statusInfo.authFailed && statusInfo.isSsh) {
                    const isNewFailure = !!this.passphrase;
                    if (isNewFailure) {
                        console.warn("[SyncManager] SSH authentication failed with the provided passphrase.");
                        this.authError = true;
                        this.passphrase = null;
                        this.onPassphraseRequired("SSH authentication failed. Please check your passphrase.");
                    } else if (!this.authError) {
                        this.onPassphraseRequired(null);
                    }
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
     * Sets the SSH passphrase and triggers an immediate refresh to validate it.
     * @param {string} passphrase - The SSH passphrase
     */
    async setPassphrase(passphrase) {
        this.passphrase = passphrase;
        this.authError = false;
        await this.refreshStatus(false);
    }

    /**
     * Internal check to identify authentication-related errors.
     * @param {any} errorOrMsg - Error object or message string
     * @returns {boolean}
     * @private
     */
    _isAuthError(errorOrMsg) {
        const msg = typeof errorOrMsg === 'string' ? errorOrMsg : (errorOrMsg?.message || "");
        return msg.includes("AUTH_FAILED:") || msg.includes("AUTH_REQUIRED:");
    }

    /**
     * Wrapper for Git operations that require SSH authentication.
     * Handles automatic passphrase prompting and error reporting.
     * @param {Function} operation - The function to execute (receives passphrase)
     * @returns {Promise<any>} Result of the operation
     * @private
     */
    async _withPassphrase(operation) {
        if (!this.passphrase && this.status?.authFailed && this.status?.isSsh) {
            this.onPassphraseRequired(null);
            throw new Error("AUTH_REQUIRED: SSH passphrase required");
        }
        try {
            const result = await operation(this.passphrase);
            
            if (result?.authFailed && this.status?.isSsh) {
                const hadPassphrase = !!this.passphrase;
                this.passphrase = null;
                const errorMsg = result?.message || "Authentication failed";
                this.onPassphraseRequired(hadPassphrase ? errorMsg.replace("AUTH_FAILED:", "").replace("AUTH_REQUIRED:", "") : null);
                throw new Error(errorMsg.startsWith("AUTH_") ? errorMsg : `AUTH_FAILED:${errorMsg}`);
            }
            return result;
        } catch (error) {
            if (this._isAuthError(error)) {
                const hadPassphrase = !!this.passphrase;
                this.passphrase = null;
                const msg = typeof error === 'string' ? error : error.message;
                this.onPassphraseRequired(hadPassphrase ? msg.replace("AUTH_FAILED:", "").replace("AUTH_REQUIRED:", "") : null);
            }
            throw error;
        }
    }

    /**
     * Centralized handler for Git operation results.
     * Triggers notifications, dialogs, and status refreshes based on the result.
     * 
     * @param {Object} result - The result from the Git operation
     * @param {string} successMessage - Message to show on success
     * @returns {Object} The original result
     * @private
     */
    async _handleOperationResult(result, successMessage) {
        if (result?.success) {
            this.onNotification(successMessage, 'success');
            // Optimistically update local status to reflect success immediately
            if (this.status) {
                this.status = {
                    ...this.status,
                    upToDate: true,
                    hasUnpublished: false,
                    ahead: 0,
                    behind: 0
                };
                this.onStatusChange(this.status);
            }
            await this.refreshStatus(true);
        } else if (result?.hasConflicts) {
            await this.onConflict(result.conflictFiles);
        } else if (result && !result.passphraseRequired) {
            this.onNotification(result.message || "Operation failed", 'error');
        }
        return result;
    }

    /**
     * Manually triggers a Git Sync (pull).
     * @returns {Promise<Object>} Result of the operation
     */
    async manualSync() {
        const result = await this._withPassphrase(
            (pass) => this.jsonRpc.syncContent({ passphrase: pass }).then(r => r.result)
        );
        return this._handleOperationResult(result, 'Content synchronized successfully');
    }

    /**
     * Manually triggers a Git Publish (commit + push).
     * @param {string} message - The commit message
     * @param {string[]} filePaths - List of files to stage and commit
     * @returns {Promise<Object>} Result of the operation
     */
    async manualPublish(message, filePaths) {
        const result = await this._withPassphrase(
            (pass) => this.jsonRpc.publishContent({ message, passphrase: pass, filePaths: filePaths ?? [] }).then(r => r.result)
        );
        return this._handleOperationResult(result, 'Content published successfully');
    }

    /**
     * Triggers a publish followed by a sync.
     * @param {string} message - The commit message
     * @param {string[]} filePaths - List of files to publish
     * @returns {Promise<Object>} Combined result
     */
    async publishAndSync(message, filePaths) {
        const result = await this._withPassphrase(
            (pass) => this.jsonRpc.publishAndSync({ message, passphrase: pass, filePaths: filePaths ?? [] }).then(r => r.result)
        );
        return this._handleOperationResult(result, 'Content published and synchronized successfully');
    }
}
