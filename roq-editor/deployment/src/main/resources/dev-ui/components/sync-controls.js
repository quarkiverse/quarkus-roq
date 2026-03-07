import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';

export class SyncControls extends LitElement {

    static properties = {
        status: { type: Object },
        syncing: { type: Boolean },
        publishing: { type: Boolean }
    };

    static styles = css`
        :host {
            display: flex;
            gap: var(--lumo-space-s);
            align-items: center;
        }
    `;

    constructor() {
        super();
        this.status = null;
        this.syncing = false;
        this.publishing = false;
    }

    _onSyncClick() {
        this.dispatchEvent(new CustomEvent('sync-requested', {
            bubbles: true,
            composed: true
        }));
    }

    async _onPublishClick() {
        this.dispatchEvent(new CustomEvent('publish-requested', {
            bubbles: true,
            composed: true
        }));
    }

    /**
     * Renders the sync and publish buttons.
     * 
     * Button enablement logic:
     * - While status is unknown (null), keep both buttons available.
     * - Publish: enabled if there are local changes OR commits ahead of remote OR conflicts to resolve.
     * - Sync: enabled if there are remote changes OR if local state is clean (safe to pull).
     */
    render() {
        const statusKnown = this.status !== null;
        const hasUnpublished = this.status?.hasUnpublished;
        const ahead = this.status?.ahead > 0;
        const behind = this.status?.behind > 0;
        const hasConflicts = this.status?.hasConflicts;

        const canPublish = (!statusKnown || hasUnpublished || ahead || hasConflicts) && !this.publishing && !this.syncing;
        const canSync = (!statusKnown || behind || !hasUnpublished) && !this.syncing && !this.publishing;

        return html`
            <vaadin-button
                theme="tertiary"
                ?disabled="${!canSync}"
                @click="${this._onSyncClick}">
                <vaadin-icon icon="font-awesome-solid:arrow-rotate-left" slot="prefix"></vaadin-icon>
                ${this.syncing ? 'Syncing...' : 'Sync'}
            </vaadin-button>

            <vaadin-button
                theme="primary small"
                ?disabled="${!canPublish}"
                @click="${this._onPublishClick}">
                <vaadin-icon icon="font-awesome-solid:cloud-arrow-up" slot="prefix"></vaadin-icon>
                ${this.publishing ? 'Publishing...' : (hasConflicts ? 'Resolve & Publish' : 'Publish')}
            </vaadin-button>
        `;
    }
}

customElements.define('qwc-sync-controls', SyncControls);
