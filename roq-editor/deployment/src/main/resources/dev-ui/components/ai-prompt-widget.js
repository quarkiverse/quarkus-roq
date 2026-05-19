import { LitElement, html, css } from 'lit';
import '@vaadin/icon';
import '@vaadin/progress-bar';

export class AiPromptWidget extends LitElement {

    static properties = {
        _loading: { state: true },
        _error: { state: true },
    };

    static styles = css`
        :host {
            position: absolute;
            z-index: 1000;
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-primary-color-50pct);
            border-radius: var(--lumo-border-radius-m);
            box-shadow: var(--lumo-box-shadow-s);
            padding: var(--lumo-space-xs) var(--lumo-space-s);
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
            min-width: 500px;
        }
        :host([error]) {
            border-color: var(--lumo-error-color-50pct);
        }
        .row {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
        }
        .icon {
            color: var(--lumo-secondary-text-color);
            flex-shrink: 0;
        }
        .icon[error] {
            color: var(--lumo-error-color);
        }
        input {
            flex: 1;
            border: none;
            outline: none;
            font: inherit;
            font-size: var(--lumo-font-size-s);
            background: transparent;
            color: var(--lumo-body-text-color);
            padding: var(--lumo-space-xs) 0;
        }
        input:disabled {
            opacity: 0.6;
        }
        .hint {
            font-size: var(--lumo-font-size-xxs);
            color: var(--lumo-tertiary-text-color);
            white-space: nowrap;
        }
        .close {
            cursor: pointer;
            color: var(--lumo-tertiary-text-color);
            flex-shrink: 0;
            font-size: var(--lumo-font-size-xs);
        }
        vaadin-progress-bar {
            width: 100%;
            margin: 0;
        }
    `;

    constructor() {
        super();
        this._loading = false;
        this._error = false;
    }

    firstUpdated() {
        this.shadowRoot.querySelector('input')?.focus();
    }

    render() {
        return html`
            <div class="row">
                <vaadin-icon class="icon" ?error="${this._error}"
                    icon="${this._error ? 'font-awesome-solid:triangle-exclamation' : 'font-awesome-solid:robot'}">
                </vaadin-icon>
                <input
                    type="text"
                    placeholder="Describe what to generate..."
                    ?disabled="${this._loading}"
                    @keydown="${this._onKeyDown}"
                />
                <span class="hint">${this._error ? 'Retry ↵' : 'Enter ↵'}</span>
                <vaadin-icon class="close" icon="lumo:cross" @click="${this._close}"></vaadin-icon>
            </div>
            ${this._loading ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : ''}
        `;
    }

    _onKeyDown(e) {
        if (e.key === 'Enter' && e.target.value.trim()) {
            this._generate(e.target.value.trim());
        } else if (e.key === 'Escape') {
            this._close();
        }
    }

    async _generate(prompt) {
        this._loading = true;
        this._error = false;
        this.dispatchEvent(new CustomEvent('generate', {
            detail: { prompt },
            bubbles: true, composed: true
        }));
    }

    showError() {
        this._loading = false;
        this._error = true;
        this.toggleAttribute('error', true);
        this.updateComplete.then(() => {
            this.shadowRoot.querySelector('input')?.focus();
        });
    }

    close() {
        this.remove();
    }

    _close() {
        this.dispatchEvent(new CustomEvent('close', { bubbles: true, composed: true }));
        this.remove();
    }
}

customElements.define('qwc-ai-prompt', AiPromptWidget);
