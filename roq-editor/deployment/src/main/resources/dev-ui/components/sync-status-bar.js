import { LitElement, html, css } from 'lit';
import '@vaadin/icon';

export class SyncStatusBar extends LitElement {

    static properties = {
        status: { type: Object },
        syncing: { type: Boolean }
    };

    static styles = css`
        :host {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
            padding: var(--lumo-space-xs) var(--lumo-space-s);
            background: var(--lumo-contrast-5pct);
            border-radius: var(--lumo-border-radius-m);
            font-size: var(--lumo-font-size-xs);
        }

        .status-indicator {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            flex-shrink: 0;
        }

        .status-indicator.up-to-date {
            background: var(--lumo-success-color);
        }

        .status-indicator.unpublished {
            background: var(--lumo-warning-color);
        }

        .status-indicator.remote-changes {
            background: var(--lumo-primary-color);
        }

        .status-indicator.syncing {
            background: var(--lumo-contrast-40pct);
            animation: pulse 1.5s ease-in-out infinite;
        }

        .status-indicator.error {
            background: var(--lumo-error-color);
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }

        .status-text {
            color: var(--lumo-secondary-text-color);
            white-space: nowrap;
        }

        .branch-info {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-xs);
            margin-left: var(--lumo-space-s);
            padding-left: var(--lumo-space-s);
            border-left: 1px solid var(--lumo-contrast-10pct);
        }

        .branch-name {
            color: var(--lumo-contrast-70pct);
            font-family: var(--lumo-font-family-mono);
            font-size: var(--lumo-font-size-xxs);
            padding: 2px 6px;
            background: var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-s);
        }

        .ahead-behind {
            display: flex;
            gap: var(--lumo-space-xs);
            font-size: var(--lumo-font-size-xxs);
            color: var(--lumo-contrast-60pct);
        }

        .ahead {
            color: var(--lumo-success-text-color);
        }

        .behind {
            color: var(--lumo-error-text-color);
        }
    `;

    constructor() {
        super();
        this.status = null;
        this.syncing = false;
    }

    _getStatusClass() {
        if (this.syncing) return 'syncing';
        if (!this.status) return 'syncing'; // unknown → neutral pulsing instead of red error
        if (this.status.hasUnpublished && this.status.hasRemoteChanges) return 'remote-changes';
        if (this.status.upToDate) return 'up-to-date';
        if (this.status.hasUnpublished) return 'unpublished';
        if (this.status.hasRemoteChanges) return 'remote-changes';
        return 'up-to-date';
    }

    _getStatusText() {
        if (this.syncing) return 'Syncing...';
        if (!this.status) return 'Checking status...';
        if (this.status.hasUnpublished && this.status.hasRemoteChanges) return 'Unpublished changes & remote changes detected';
        if (this.status.upToDate) return 'Content up to date';
        if (this.status.hasUnpublished) return 'Content not published';
        if (this.status.hasRemoteChanges) return 'Remote changes detected';
        return 'Content up to date';
    }

    render() {
        return html`
            <div class="status-indicator ${this._getStatusClass()}"></div>
            <span class="status-text">${this._getStatusText()}</span>
            ${this.status?.branch && this.status.branch !== 'sync-disabled' ? html`
                <div class="branch-info">
                    <vaadin-icon icon="font-awesome-solid:code-branch"></vaadin-icon>
                    <span class="branch-name">${this.status.branch}</span>
                    ${this.status.ahead > 0 || this.status.behind > 0 ? html`
                        <div class="ahead-behind">
                            ${this.status.ahead > 0 ? html`
                                <span class="ahead" title="Commits ahead">↑${this.status.ahead}</span>
                            ` : ''}
                            ${this.status.behind > 0 ? html`
                                <span class="behind" title="Commits behind">↓${this.status.behind}</span>
                            ` : ''}
                        </div>
                    ` : ''}
                </div>
            ` : ''}
        `;
    }
}

customElements.define('qwc-sync-status-bar', SyncStatusBar);
