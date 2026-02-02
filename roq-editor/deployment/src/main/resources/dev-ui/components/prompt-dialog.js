
import { LitElement, html } from 'lit';
import { dialogRenderer, dialogHeaderRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import '@vaadin/dialog';
import '@vaadin/text-field';
import '@vaadin/select';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/vertical-layout';

export class PromptDialog extends LitElement {
    static properties = {
        _open: { state: true },
        _message: { state: true },
        _defaultValue: { state: true },
        _resolve: { state: true },
        _inputValue: { state: true },
        _tempInputValue: { state: true },
        _customRenderer: { state: true },
        _formValues: { state: true },
        _defaultFormValues: { state: true }
    };

    constructor() {
        super();
        this._open = false;
        this._message = '';
        this._defaultValue = '';
        this._inputValue = '';
        this._tempInputValue = '';
        this._resolve = null;

        this._customRenderer = null;
        this._formValues = {};
        this._defaultFormValues = {};
    }

    /**
     * Shows a prompt dialog and returns a Promise that resolves with the user's input.
     * - Without customRenderer: resolves to string or null
     * - With customRenderer: resolves to object or null
     */
    prompt(message, defaultValue, customRenderer = null) {
        return new Promise((resolve) => {
            this._message = message;
            this._defaultValue = defaultValue;
            this._inputValue = typeof defaultValue === 'string' ? defaultValue : '';
            this._tempInputValue = typeof defaultValue === 'string' ? defaultValue : '';

            this._customRenderer = customRenderer;

            if (customRenderer) {
                const initial =
                    defaultValue && typeof defaultValue === 'object' && !Array.isArray(defaultValue)
                        ? JSON.parse(JSON.stringify(defaultValue))
                        : {};
                this._defaultFormValues = initial;
                this._formValues = JSON.parse(JSON.stringify(initial));
            } else {
                this._defaultFormValues = {};
                this._formValues = {};
            }

            this._resolve = resolve;
            this._open = true;
            this.requestUpdate();
        });
    }

    _handleConfirm() {
        if (this._resolve) {
            if (this._customRenderer) {
                this._resolve(JSON.parse(JSON.stringify(this._formValues)));
            } else {
                this._resolve(this._tempInputValue || null);
                this._inputValue = this._tempInputValue;
            }
            this._resolve = null;
        }
        this._customRenderer = null;
        this._open = false;
    }

    _handleCancel() {
        if (this._resolve) {
            if(this._defaultValue && typeof this._defaultValue === 'object') {
                this._resolve({});
            } else {
                this._resolve(null);
            }

            if (this._customRenderer) {
                this._formValues = JSON.parse(JSON.stringify(this._defaultFormValues));
            } else {
                this._tempInputValue = this._defaultValue;
                this._inputValue = this._defaultValue;
            }
            this._resolve = null;
        }
        this._customRenderer = null;
        this._open = false;
    }

    // --- Lit template using Vaadin renderer directives (per docs) ---
    render() {
        return html`
          <vaadin-dialog
            .opened=${this._open}
            @closed=${() => {
              if (this._resolve) this._handleCancel();
            }}
            ${dialogHeaderRenderer(
              () => html`
            <h2 style="margin:0; font-size: 1.25rem; font-weight: 600;">
              ${this._message}
            </h2>
          `,
              // Re-render header only when message changes
              [this._message]
            )}
            ${dialogRenderer(
              () => html`
            <vaadin-vertical-layout
              theme="spacing"
              style="align-items: stretch; width: 20rem; max-width: 100%;"
            >
              ${this._customRenderer
                ? this._customRenderer({
                  values: this._formValues,
                  update: (name, val) => {
                    this._formValues = { ...this._formValues, [name]: val };
                  }
                })
                : html`
                    <vaadin-text-field
                      id="prompt-input"
                      .value=${this._tempInputValue}
                      placeholder="Enter value..."
                      autofocus
                      @value-changed=${(e) =>
                  (this._tempInputValue = (e.detail).value || '')}
                    ></vaadin-text-field>
                  `}
            </vaadin-vertical-layout>
          `,
              // Dependencies: update body when either the basic value or form values change
              [this._tempInputValue, JSON.stringify(this._formValues), this._customRenderer]
            )}
            ${dialogFooterRenderer(
              () => html`
            <vaadin-button theme="tertiary" @click=${() => this._handleCancel()}>
              Cancel
            </vaadin-button>
            <vaadin-button theme="primary" @click=${() => this._handleConfirm()}>
              OK
            </vaadin-button>
          `,
              // Footer doesn't depend on state
              []
            )}
          ></vaadin-dialog>
        `;
    }
}

customElements.define('qwc-prompt-dialog', PromptDialog);

/**
 * Helper function to show a prompt dialog
 * Creates a singleton dialog instance and reuses it
 */
let dialogInstance = null;

export function showPrompt(message, defaultValue , customRenderer = null) {
    if (!dialogInstance) {
        dialogInstance = document.createElement('qwc-prompt-dialog');
        document.body.appendChild(dialogInstance);
    }
    return dialogInstance.prompt(message, defaultValue, customRenderer);
}
