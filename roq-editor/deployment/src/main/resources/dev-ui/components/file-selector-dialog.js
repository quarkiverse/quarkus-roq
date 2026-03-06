import { LitElement, html, css } from 'lit';
import { dialogRenderer, dialogHeaderRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import '@vaadin/dialog';
import '@vaadin/button';
import '@vaadin/vertical-layout';

export class FileSelectorDialog extends LitElement {

    static properties = {
        _open: { state: true },
        _files: { state: true },
        _selected: { state: true },
        _resolve: { state: true }
    };

    static styles = css`
        .file-item {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
            padding: var(--lumo-space-xs) 0;
            cursor: pointer;
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-body-text-color);
        }
        .file-item input[type="checkbox"] {
            width: 18px;
            height: 18px;
            cursor: pointer;
            accent-color: var(--lumo-primary-color);
            flex-shrink: 0;
        }
        .file-name {
            font-family: var(--lumo-font-family-mono, monospace);
            font-size: var(--lumo-font-size-xs);
            word-break: break-all;
        }
    `;

    constructor() {
        super();
        this._open = false;
        this._files = [];
        this._selected = new Set();
        this._resolve = null;
    }

    select(files) {
        return new Promise((resolve) => {
            this._files = files;
            this._selected = new Set(files);
            this._resolve = resolve;
            this._open = true;
        });
    }

    _toggleFile(file) {
        const next = new Set(this._selected);
        if (next.has(file)) {
            next.delete(file);
        } else {
            next.add(file);
        }
        this._selected = next;
    }

    _handleConfirm() {
        if (this._resolve) {
            this._resolve([...this._selected]);
            this._resolve = null;
        }
        this._open = false;
    }

    _handleCancel() {
        if (this._resolve) {
            this._resolve(null);
            this._resolve = null;
        }
        this._open = false;
    }

    render() {
        const count = this._selected.size;
        return html`
            <vaadin-dialog
                .opened=${this._open}
                @closed=${() => { if (this._resolve) this._handleCancel(); }}
                ${dialogHeaderRenderer(() => html`
                    <h2 style="margin:0; font-size: 1.25rem; font-weight: 600;">
                        Select files to publish
                    </h2>
                `, [])}
                ${dialogRenderer(() => html`
                    <vaadin-vertical-layout theme="spacing" style="min-width: 22rem; max-width: 36rem;">
                        ${this._files.map(f => html`
                            <label class="file-item">
                                <input
                                    type="checkbox"
                                    .checked=${this._selected.has(f)}
                                    @change=${() => this._toggleFile(f)}>
                                <span class="file-name">${f}</span>
                            </label>
                        `)}
                    </vaadin-vertical-layout>
                `, [this._files, this._selected])}
                ${dialogFooterRenderer(() => html`
                    <vaadin-button theme="tertiary" @click=${() => this._handleCancel()}>
                        Cancel
                    </vaadin-button>
                    <vaadin-button
                        theme="primary"
                        ?disabled=${count === 0}
                        @click=${() => this._handleConfirm()}>
                        Publish ${count} file${count === 1 ? '' : 's'}
                    </vaadin-button>
                `, [count])}
            ></vaadin-dialog>
        `;
    }
}

customElements.define('qwc-file-selector-dialog', FileSelectorDialog);

let _instance = null;

export function showFileSelector(files) {
    if (!_instance) {
        _instance = document.createElement('qwc-file-selector-dialog');
        document.body.appendChild(_instance);
    }
    return _instance.select(files);
}
