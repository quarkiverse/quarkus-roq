export class SyncManager {
    constructor(jsonRpc, onStatusChange, syncConfig, onPassphraseRequired) {
        this.jsonRpc = jsonRpc;
        this.onStatusChange = onStatusChange;
        this.syncConfig = syncConfig;
        this.onPassphraseRequired = onPassphraseRequired;
        this.status = null;
        this.passphrase = null;
        this.intervalId = null;
        this.pollingCount = 0;
    }

    start() {
        if (this.intervalId) return;
        
        // Use configured interval or default to 10s for status polling
        // Note: SmallRye Config maps intervalSeconds to interval-seconds in JSON
        const autoSyncConfig = this.syncConfig?.['auto-sync'];
        const intervalMs = (autoSyncConfig?.['interval-seconds'] || 10) * 1000;
        
        // First refresh should NOT skip fetch to detect auth needs immediately
        this.refreshStatus(false);
        
        this.intervalId = setInterval(() => {
            this.pollingCount++;
            // Perform a full fetch every 6 cycles (approx 60s if interval is 10s)
            const skipFetch = (this.pollingCount % 6 !== 0);
            this.refreshStatus(skipFetch);
        }, 10000); 
    }

    stop() {
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
    }

    async refreshStatus(skipFetch = true) {
        try {
            const hasPassphrase = !!this.passphrase;
            const response = await this.jsonRpc.getSyncStatus({ 
                passphrase: this.passphrase, 
                skipFetch 
            });
            const statusInfo = response.result;
            if (!statusInfo) return;

            this.status = statusInfo;
            
            if (statusInfo.authFailed && !hasPassphrase) {
                this.onPassphraseRequired();
            }
            
            this.onStatusChange(statusInfo);
            return statusInfo;
        } catch (error) {
            console.error("Failed to refresh Git status", error);
        }
    }

    setPassphrase(passphrase) {
        this.passphrase = passphrase;
        this.refreshStatus(false);
    }

    async _withPassphrase(operation) {
        if (!this.passphrase && this.status?.authFailed) {
            this.onPassphraseRequired();
            throw new Error("Authentication required");
        }
        try {
            const result = await operation(this.passphrase);
            if (result?.authFailed) {
                this.passphrase = null;
                this.onPassphraseRequired();
                throw new Error("Authentication failed");
            }
            return result;
        } catch (error) {
            const msg = error.message?.toLowerCase() || "";
            if (msg.includes("auth") || msg.includes("passphrase")) {
                this.passphrase = null;
                this.onPassphraseRequired();
            }
            throw error;
        }
    }

    async manualSync() {
        const result = await this._withPassphrase(
            (pass) => this.jsonRpc.syncContent({ passphrase: pass }).then(r => r.result)
        );
        await this.refreshStatus();
        return result;
    }

    async manualPublish(message, filePaths) {
        const result = await this._withPassphrase(
            (pass) => this.jsonRpc.publishContent({ message, passphrase: pass, filePaths: filePaths ?? [] }).then(r => r.result)
        );
        await this.refreshStatus();
        return result;
    }

    async publishAndSync(message, filePaths) {
        const result = await this._withPassphrase(
            (pass) => this.jsonRpc.publishAndSync({ message, passphrase: pass, filePaths: filePaths ?? [] }).then(r => r.result)
        );
        await this.refreshStatus();
        return result;
    }
}
