import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import { showPrompt } from './prompt-dialog.js';

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

    render() {
        // While status is unknown (null), keep both buttons available.
        // Once status is known: Sync only when no unpublished changes; Publish only when there are.
        const statusKnown = this.status !== null;
        const canSync = (!statusKnown || !this.status?.hasUnpublished) && !this.syncing && !this.publishing;
        const canPublish = (!statusKnown || this.status?.hasUnpublished) && !this.publishing && !this.syncing;

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
                ${this.publishing ? 'Publishing...' : 'Publish'}
            </vaadin-button>
        `;
    }
}

customElements.define('qwc-sync-controls', SyncControls);
