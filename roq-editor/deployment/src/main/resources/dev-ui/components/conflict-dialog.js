import { LitElement, html, css } from 'lit';
import { dialogRenderer, dialogHeaderRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import '@vaadin/dialog';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/vertical-layout';

export class ConflictDialog extends LitElement {
    static properties = {
        _open: { state: true },
        _conflictFiles: { state: true }
    };

    static styles = css`
        :host {
            display: contents;
        }

        .conflict-icon {
            color: var(--lumo-error-color);
            font-size: 48px;
            margin-bottom: var(--lumo-space-m);
        }

        .conflict-message {
            text-align: center;
            line-height: 1.6;
            color: var(--lumo-secondary-text-color);
        }

        .conflict-files {
            margin-top: var(--lumo-space-m);
            padding: var(--lumo-space-s);
            background: var(--lumo-contrast-5pct);
            border-radius: var(--lumo-border-radius-m);
            max-height: 200px;
            overflow-y: auto;
            width: 100%;
        }

        .conflict-file {
            font-family: var(--lumo-font-family-mono);
            font-size: var(--lumo-font-size-xs);
            padding: var(--lumo-space-xs);
            color: var(--lumo-error-text-color);
        }
    `;

    constructor() {
        super();
        this._open = false;
        this._conflictFiles = [];
        this._resolve = null;
    }

    show(conflictFiles) {
        return new Promise((resolve) => {
            this._conflictFiles = conflictFiles || [];
            this._resolve = resolve;
            this._open = true;
            this.requestUpdate();
        });
    }

    _handleClose() {
        if (this._resolve) {
            this._resolve();
            this._resolve = null;
        }
        this._open = false;
    }

    render() {
        return html`
            <vaadin-dialog
                .opened=${this._open}
                @opened-changed=${(e) => {
                    if (!e.detail.value && this._resolve) {
                        this._handleClose();
                    }
                }}
                ${dialogHeaderRenderer(
                    () => html`
                        <h2 style="margin:0; font-size: 1.2rem; font-weight: 600; color: var(--lumo-error-text-color);">
                            <vaadin-icon icon="font-awesome-solid:triangle-exclamation"></vaadin-icon>
                            Merge Conflicts Detected
                        </h2>
                    `,
                    []
                )}
                ${dialogRenderer(
                    () => html`
                        <vaadin-vertical-layout
                            theme="spacing"
                            style="align-items: center; text-align: center; width: 30rem; max-width: 100%;">

                            <vaadin-icon
                                class="conflict-icon"
                                icon="font-awesome-solid:code-merge"></vaadin-icon>

                            <div class="conflict-message">
                                <p><strong>Git sync failed due to merge conflicts.</strong></p>
                                <p>The following files have conflicts that need to be resolved manually:</p>
                            </div>

                            <div class="conflict-files">
                                ${this._conflictFiles.map(file => html`
                                    <div class="conflict-file">
                                        <vaadin-icon icon="font-awesome-solid:file-code"></vaadin-icon>
                                        ${file}
                                    </div>
                                `)}
                            </div>

                            <div class="conflict-message">
                                <p>Please resolve conflicts using your Git client and try syncing again.</p>
                            </div>
                        </vaadin-vertical-layout>
                    `,
                    [this._conflictFiles]
                )}
                ${dialogFooterRenderer(
                    () => html`
                        <vaadin-button theme="primary" @click="${this._handleClose}">
                            OK, I'll resolve manually
                        </vaadin-button>
                    `,
                    []
                )}
            ></vaadin-dialog>
        `;
    }
}

customElements.define('qwc-conflict-dialog', ConflictDialog);

let conflictDialogInstance = null;

export function showConflictDialog(conflictFiles) {
    if (!conflictDialogInstance) {
        conflictDialogInstance = document.createElement('qwc-conflict-dialog');
        document.body.appendChild(conflictDialogInstance);
    }
    return conflictDialogInstance.show(conflictFiles);
}
