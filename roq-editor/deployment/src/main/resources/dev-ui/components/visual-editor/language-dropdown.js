/**
 * Language dropdown component for TipTap code blocks
 * Allows selecting syntax highlighting language for the active code block
 */

import '@vaadin/combo-box';
import { LitElement, css, html } from 'lit';
import { editorContext } from './editor-context.js';
import { ContextConsumer, lowlight } from '../../bundle.js';

function formatLanguageLabel(language) {
    if (!language) {
        return 'Plain text';
    }
    return language.charAt(0).toUpperCase() + language.slice(1);
}

function buildLanguageItems() {
    const languages = lowlight.listLanguages()
        .slice()
        .sort((a, b) => a.localeCompare(b))
        .map(language => ({
            label: formatLanguageLabel(language),
            value: language,
        }));

    return [{ label: 'Plain text', value: '' }, ...languages];
}

export class LanguageDropdown extends LitElement {
    static properties = {
        codeBlockPos: { type: Number },
        _selectedValue: { state: true },
        _dropdownOpen: { state: true },
    };

    constructor() {
        super();
        this.codeBlockPos = null;
        this._selectedValue = '';
        this._dropdownOpen = false;
        this._isUpdatingFromEditor = false;
        this._items = buildLanguageItems();

        this._editorConsumer = new ContextConsumer(this, {
            context: editorContext,
            subscribe: true,
            callback: () => {
                this._updateSelectedValue();
            }
        });
    }

    static styles = css`
        :host {
            display: inline-flex;
            align-items: center;
            gap: 0.35rem;
        }

        .language-label {
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-secondary-text-color);
            white-space: nowrap;
        }

        vaadin-combo-box {
            --vaadin-combo-box-overlay-width: 180px;
            width: 130px;
        }

        vaadin-combo-box::part(input-field) {
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-s);
            min-height: 28px;
            padding: 0;
        }

        vaadin-combo-box::part(toggle-button) {
            color: var(--lumo-body-text-color);
        }

        vaadin-combo-box:hover::part(input-field) {
            border-color: var(--lumo-primary-color-50pct);
        }
    `;

    get editor() {
        return this._editorConsumer.value?.editor || null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._updateInterval = setInterval(() => {
            if (this.editor && this.isConnected) {
                this._updateSelectedValue();
            }
        }, 100);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._updateInterval) {
            clearInterval(this._updateInterval);
        }
    }

    _getCodeBlockNode() {
        if (!this.editor || this.codeBlockPos == null) {
            return null;
        }

        const node = this.editor.state.doc.nodeAt(this.codeBlockPos);
        if (!node || node.type.name !== 'codeBlock') {
            return null;
        }

        return node;
    }

    _updateSelectedValue() {
        const node = this._getCodeBlockNode();
        if (!node) {
            return;
        }

        const language = node.attrs.language ?? '';
        if (this._selectedValue !== language) {
            this._isUpdatingFromEditor = true;
            this._selectedValue = language;
            requestAnimationFrame(() => {
                this._isUpdatingFromEditor = false;
            });
        }
    }

    _applyLanguage(language) {
        const editor = this.editor;
        const node = this._getCodeBlockNode();
        if (!editor || !node) {
            return;
        }

        editor.chain().focus().command(({ tr, dispatch }) => {
            tr.setNodeMarkup(this.codeBlockPos, undefined, {
                ...node.attrs,
                language: language || null,
            });
            dispatch(tr);
            return true;
        }).run();
    }

    _handleChange(e) {
        if (this._isUpdatingFromEditor) {
            return;
        }

        const comboBox = e.target;
        const selectedItem = comboBox.selectedItem;

        if (!selectedItem) {
            return;
        }

        this._applyLanguage(selectedItem.value || null);
    }

    _handleOpenedChanged(e) {
        this._dropdownOpen = e.detail.value;
        this.dispatchEvent(new CustomEvent('dropdown-opened-changed', {
            bubbles: true,
            composed: true,
            detail: { opened: this._dropdownOpen },
        }));
    }

    render() {
        return html`
            <span class="language-label">Language</span>
            <vaadin-combo-box
                theme="small"
                .items="${this._items}"
                item-label-path="label"
                item-value-path="value"
                .value="${this._selectedValue}"
                @change="${this._handleChange}"
                @opened-changed="${this._handleOpenedChanged}"
            ></vaadin-combo-box>
        `;
    }
}

customElements.define('qwc-language-dropdown', LanguageDropdown);
