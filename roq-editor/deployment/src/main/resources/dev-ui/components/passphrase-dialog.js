import { LitElement, html, css } from 'lit';

/**
 * Dialog prompting the user for their SSH key passphrase.
 * Fires a 'passphrase-confirmed' event with { passphrase } on confirm,
 * or 'passphrase-cancelled' on cancel.
 */
export class PassphraseDialog extends LitElement {
    static properties = {
        open: { type: Boolean },
        _passphrase: { type: String, state: true },
        _error: { type: String, state: true },
    };

    static styles = css`
        .overlay {
            position: fixed; inset: 0;
            background: rgba(0,0,0,0.5);
            display: flex; align-items: center; justify-content: center;
            z-index: 1000;
        }
        .dialog {
            background: var(--lumo-base-color, #fff);
            border-radius: 8px;
            padding: 24px;
            width: 380px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.2);
        }
        h3 { margin: 0 0 8px; font-size: 1.1rem; }
        p  { margin: 0 0 16px; font-size: 0.9rem; color: var(--lumo-secondary-text-color, #666); }
        .error { color: var(--lumo-error-color, #e53e3e); font-size: 0.85rem; margin: 4px 0 8px; }
        input {
            width: 100%; box-sizing: border-box;
            padding: 8px 10px; border-radius: 4px;
            border: 1px solid var(--lumo-contrast-30pct, #ccc);
            font-size: 1rem; margin-bottom: 16px;
        }
        .actions { display: flex; justify-content: flex-end; gap: 8px; }
        button {
            padding: 8px 18px; border-radius: 4px; border: none;
            cursor: pointer; font-size: 0.9rem;
        }
        .cancel { background: var(--lumo-contrast-10pct, #eee); }
        .confirm { background: var(--lumo-primary-color, #1976d2); color: #fff; }
    `;

    constructor() {
        super();
        this.open = false;
        this._passphrase = '';
        this._error = '';
    }

    show(errorMessage = '') {
        this._passphrase = '';
        this._error = errorMessage;
        this.open = true;
        // focus input after render
        this.updateComplete.then(() => {
            this.shadowRoot?.querySelector('input')?.focus();
        });
    }

    hide() {
        this.open = false;
    }

    _onKeyDown(e) {
        if (e.key === 'Enter') this._confirm();
        if (e.key === 'Escape') this._cancel();
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
        if (!this.open) return html``;
        return html`
            <div class="overlay" @click=${(e) => e.target === e.currentTarget && this._cancel()}>
                <div class="dialog">
                    <h3>🔑 SSH Key Passphrase</h3>
                    <p>Your SSH key requires a passphrase. It will be kept in memory for this session only.</p>
                    ${this._error ? html`<div class="error">${this._error}</div>` : ''}
                    <input
                        type="password"
                        placeholder="Enter passphrase…"
                        .value=${this._passphrase}
                        @input=${(e) => { this._passphrase = e.target.value; this._error = ''; }}
                        @keydown=${this._onKeyDown}
                    />
                    <div class="actions">
                        <button class="cancel" @click=${this._cancel}>Cancel</button>
                        <button class="confirm" @click=${this._confirm}>Confirm</button>
                    </div>
                </div>
            </div>
        `;
    }
}

customElements.define('passphrase-dialog', PassphraseDialog);
