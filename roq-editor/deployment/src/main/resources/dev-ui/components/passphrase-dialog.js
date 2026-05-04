import { LitElement, html, css } from 'lit';
import { dialogRenderer, dialogHeaderRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import '@vaadin/dialog';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/password-field';
import '@vaadin/icon';
import '@vaadin/vertical-layout';

/**
 * Dialog prompting the user for their SSH key passphrase.
 * Fires a 'passphrase-confirmed' event with { passphrase } on confirm,
 * or 'passphrase-cancelled' on cancel.
 */
export class PassphraseDialog extends LitElement {
    static properties = {
        _open: { state: true },
        _passphrase: { state: true },
        _error: { state: true },
    };

    static styles = css`
        :host {
            display: contents;
        }
        .passphrase-icon {
            color: var(--lumo-primary-color);
            font-size: 48px;
            margin-bottom: var(--lumo-space-m);
        }
        .passphrase-message {
            text-align: center;
            line-height: 1.6;
            color: var(--lumo-secondary-text-color);
            margin-bottom: var(--lumo-space-m);
        }
        .error-message {
            color: var(--lumo-error-text-color);
            font-size: var(--lumo-font-size-s);
            margin-top: var(--lumo-space-xs);
            margin-bottom: var(--lumo-space-s);
        }
        vaadin-password-field {
            width: 100%;
        }
    `;

    constructor() {
        super();
        this._open = false;
        this._passphrase = '';
        this._error = '';
    }

    show(errorMessage = '') {
        this._passphrase = '';
        this._error = errorMessage;
        this._open = true;
    }

    hide() {
        this._open = false;
    }

    _confirm() {
        if (!this._passphrase) {
            this._error = 'Passphrase cannot be empty.';
            return;
        }
        this.dispatchEvent(new CustomEvent('passphrase-confirmed', {
            detail: { passphrase: this._passphrase },
            bubbles: true, composed: true,
        }));
        this.hide();
    }

    _cancel() {
        this.dispatchEvent(new CustomEvent('passphrase-cancelled', {
            bubbles: true, composed: true,
        }));
        this.hide();
    }

    render() {
        return html`
            <vaadin-dialog
                .opened="${this._open}"
                @opened-changed="${(e) => (this._open = e.detail.value)}"
                ${dialogHeaderRenderer(() => html`
                    <vaadin-horizontal-layout style="align-items: center; gap: var(--lumo-space-s);">
                        <vaadin-icon icon="font-awesome-solid:key"></vaadin-icon>
                        <span style="font-weight: bold;">SSH Key Passphrase</span>
                    </vaadin-horizontal-layout>
                `, [])}
                ${dialogRenderer(() => html`
                    <vaadin-vertical-layout style="align-items: center; width: 320px;">
                        <vaadin-icon class="passphrase-icon" icon="font-awesome-solid:lock"></vaadin-icon>
                        <div class="passphrase-message">
                            Your SSH key requires a passphrase. It will be kept in memory for this session only.
                        </div>

                        <vaadin-password-field
                            label="Passphrase"
                            placeholder="Enter passphrase…"
                            .value="${this._passphrase}"
                            @input="${(e) => { this._passphrase = e.target.value; this._error = ''; }}"
                            @keydown="${(e) => e.key === 'Enter' && this._confirm()}"
                            autofocus
                        ></vaadin-password-field>

                        ${this._error ? html`<div class="error-message">${this._error}</div>` : ''}
                    </vaadin-vertical-layout>
                `, [this._passphrase, this._error])}
                ${dialogFooterRenderer(() => html`
                    <vaadin-button theme="tertiary" @click="${this._cancel}">Cancel</vaadin-button>
                    <vaadin-button theme="primary" @click="${this._confirm}">Confirm</vaadin-button>
                `, [this._passphrase])}
            ></vaadin-dialog>
        `;
    }
}

customElements.define('passphrase-dialog', PassphraseDialog);
