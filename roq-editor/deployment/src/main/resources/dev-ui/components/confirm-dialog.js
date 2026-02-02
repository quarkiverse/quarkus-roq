
import { LitElement, html } from 'lit';
import {
    dialogRenderer,
    dialogHeaderRenderer,
    dialogFooterRenderer
} from '@vaadin/dialog/lit.js';

import '@vaadin/dialog';
import '@vaadin/button';
import '@vaadin/vertical-layout';

/**
 * @typedef {Object} ConfirmOptions
 * @property {string} [confirmText]  Text for the confirm button (default: "OK")
 * @property {string} [cancelText]   Text for the cancel button (default: "Cancel")
 * @property {'primary'|'error'|'success'|'contrast'} [theme]
 *   Theme variant applied to the confirm button. Typically use "error" for destructive actions.
 * @property {string} [title]
 *   Optional dialog title. If omitted, message is shown as the header (like your PromptDialog).
 * @property {boolean} [focusCancelOnOpen]
 *   If true, focuses Cancel when opened (defaults to true when theme === 'error').
 */

export class ConfirmDialog extends LitElement {
    static properties = {
        _open: { state: true },
        _message: { state: true },
        _options: { state: true },
        _resolve: { state: true }
    };

    constructor() {
        super();
        this._open = false;
        this._message = '';
        this._options = {};
        this._resolve = null;
    }

    /**
     * Shows a confirm dialog and resolves to true (confirm) or false (cancel/close).
     * @param {string} message
     * @param {ConfirmOptions} [options]
     * @returns {Promise<boolean>}
     */
    confirm(message, options = {}) {
        return new Promise((resolve) => {
            this._message = message;
            this._options = {
                confirmText: 'OK',
                cancelText: 'Cancel',
                theme: 'primary',
                ...options
            };
            this._resolve = resolve;
            this._open = true;
            this.requestUpdate();

            // Focus management after render: for destructive actions, default to Cancel.
            queueMicrotask(() => this._focusInitialButton());
        });
    }

    _focusInitialButton() {
        if (!this._open) return;

        const { theme, focusCancelOnOpen } = this._options || {};
        const focusCancel = focusCancelOnOpen ?? theme === 'error';

        const root = /** @type {ShadowRoot} */ (this.renderRoot);
        const target = root?.querySelector(focusCancel ? '#cancel-btn' : '#confirm-btn');
        target?.focus?.();
    }

    _handleConfirm() {
        if (this._resolve) {
            this._resolve(true);
            this._resolve = null;
        }
        this._open = false;
    }

    _handleCancel() {
        if (this._resolve) {
            this._resolve(false);
            this._resolve = null;
        }
        this._open = false;
    }

    _confirmButtonTheme() {
        const theme = this._options?.theme || 'primary';

        // Vaadin Button supports combining variants: "primary error"
        // Keep "primary" as the baseline for emphasis, and add e.g. "error".
        if (theme === 'primary') return 'primary';
        return `primary ${theme}`;
    }

    render() {
        const title = this._options?.title;
        const confirmText = this._options?.confirmText ?? 'OK';
        const cancelText = this._options?.cancelText ?? 'Cancel';

        // If no title provided, mimic your PromptDialog: show message as header.
        const headerText = title ?? 'Your choice...';
        const showBodyMessage = true;

        return html`
      <vaadin-dialog
        .opened=${this._open}
        @opened-changed=${(e) => {
            // When opened transitions to true, (re)apply focus.
            if (e.detail.value) queueMicrotask(() => this._focusInitialButton());
        }}
        @closed=${() => {
            // ESC / outside click => cancel
            if (this._resolve) this._handleCancel();
        }}
        ${dialogHeaderRenderer(
            () => html`
            <h2 style="margin:0; font-size: 1.25rem; font-weight: 600;">
              ${headerText}
            </h2>
          `,
            [headerText]
        )}
        ${dialogRenderer(
            () => html`
            <vaadin-vertical-layout
              theme="spacing"
              style="align-items: stretch; width: 24rem; max-width: 100%;"
            >
              ${showBodyMessage
                ? html`<div style="line-height: 1.4;">${this._message}</div>`
                : html``}
            </vaadin-vertical-layout>
          `,
            [this._message, showBodyMessage]
        )}
        ${dialogFooterRenderer(
            () => html`
            <vaadin-button
              id="cancel-btn"
              theme="tertiary"
              @click=${() => this._handleCancel()}
            >
              ${cancelText}
            </vaadin-button>

            <vaadin-button
              id="confirm-btn"
              theme=${this._confirmButtonTheme()}
              @click=${() => this._handleConfirm()}
            >
              ${confirmText}
            </vaadin-button>
          `,
            [cancelText, confirmText, this._options?.theme]
        )}
      ></vaadin-dialog>
    `;
    }
}

customElements.define('qwc-confirm-dialog', ConfirmDialog);

/**
 * Singleton helper (same pattern as your showPrompt)
 */
let dialogInstance = null;

/**
 * @param {string} message
 * @param {ConfirmOptions} [options]
 * @returns {Promise<boolean>}
 */
export function showConfirm(message, options = {}) {
    if (!dialogInstance) {
        dialogInstance = document.createElement('qwc-confirm-dialog');
        document.body.appendChild(dialogInstance);
    }
    return dialogInstance.confirm(message, options);
}
