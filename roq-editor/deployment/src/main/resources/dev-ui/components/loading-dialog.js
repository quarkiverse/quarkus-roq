
import { LitElement, html, css } from 'lit';
import {
    dialogRenderer,
    dialogHeaderRenderer,
    dialogFooterRenderer
} from '@vaadin/dialog/lit.js';

import '@vaadin/dialog';
import '@vaadin/progress-bar';
import '@vaadin/vertical-layout';

export class LoadingDialog extends LitElement {
    static properties = {
        open: { type: Boolean, reflect: true },     // ✅ controlled by parent state
        title: { type: String },
        message: { type: String }
    };

    constructor() {
        super();
        this.open = false;
        this.title = 'Loading';
        this.message = 'Please wait…';
    }

    static styles = css`
        :host {
            display: contents;
        }
    `;

    render() {
        return html`
      <vaadin-dialog
        .opened=${this.open}
        no-close-on-esc
        no-close-on-outside-click
        @opened-changed=${this._onOpenedChanged}
        ${dialogHeaderRenderer(
            () => html`
            <h2 style="margin:0; font-size: 1.1rem; font-weight: 600;">
              ${this.title}
            </h2>
          `,
            [this.title]
        )}
        ${dialogRenderer(
            () => html`
            <vaadin-vertical-layout
              theme="spacing"
              style="align-items: center; text-align: center; width: 22rem; max-width: 100%;"
            >
              <vaadin-progress-bar indeterminate></vaadin-progress-bar>

              <div style="line-height: 1.4; color: var(--lumo-secondary-text-color);">
                ${this.message}
              </div>
            </vaadin-vertical-layout>
          `,
            [this.message]
        )}
        ${dialogFooterRenderer(() => html``, [])}
      ></vaadin-dialog>
    `;
    }

    _onOpenedChanged = (e) => {
        // Keep it purely controlled:
        // If something tries to close it, re-open unless parent state says otherwise.
        if (e.detail.value !== this.open) {
            // Re-sync Vaadin's internal state to our controlled prop.
            e.target.opened = this.open;
        }
    };
}

customElements.define('qwc-loading-dialog', LoadingDialog);
