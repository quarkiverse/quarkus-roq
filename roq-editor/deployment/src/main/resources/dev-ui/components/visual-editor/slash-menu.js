/**
 * Slash menu component for TipTap editor
 * Lit component that appears when typing "/" at the start of a line
 * Provides a filterable list of block types to insert
 */

import '@vaadin/list-box';
import '@vaadin/item';
import { LitElement, css, html } from 'lit';

export class SlashMenu extends LitElement {
    static properties = {
        items: { type: Array },
        selectedIndex: { type: Number },
        query: { type: String },
    };

    constructor() {
        super();
        this.items = [];
        this.selectedIndex = 0;
        this.query = '';
    }

    static styles = css`
        :host {
            display: block;
        }
        .slash-menu {
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
            min-width: 200px;
            max-height: 300px;
            overflow-y: auto;
            background: var(--lumo-base-color);
        }
        .slash-menu-label {
            font-size: var(--lumo-font-size-xxs);
            color: var(--lumo-secondary-text-color);
            padding: var(--lumo-space-s) var(--lumo-space-m) var(--lumo-space-xs);
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }
        vaadin-list-box {
            --_lumo-list-box-item-selected-icon-display: none;
        }
        vaadin-item {
            cursor: pointer;
            padding: var(--lumo-space-xs) var(--lumo-space-m);
        }
        vaadin-item[selected] {
            background: var(--lumo-primary-color-10pct);
            color: var(--lumo-primary-text-color);
        }
        vaadin-item::part(content) {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
        }
        .item-icon {
            font-size: var(--lumo-font-size-s);
            font-weight: 600;
            color: var(--lumo-secondary-text-color);
        }
        .slash-menu-empty {
            padding: var(--lumo-space-m);
            text-align: center;
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
        }
    `;

    updated(changedProperties) {
        if (changedProperties.has('items')) {
            this.selectedIndex = 0;
        }
    }

    render() {
        if (!this.items || this.items.length === 0) {
            return html`
                <div class="slash-menu">
                    <div class="slash-menu-empty">No matching blocks</div>
                </div>
            `;
        }

        return html`
            <div class="slash-menu">
                <div class="slash-menu-label">Style</div>
                <vaadin-list-box
                    .selected="${this.selectedIndex}"
                    @selected-changed="${this._onSelectedChanged}"
                >
                    ${this.items.map((item, index) => html`
                        <vaadin-item 
                            @click="${() => this._selectItem(index)}"
                            @mouseenter="${() => this._hoverItem(index)}"
                        >
                            <span class="item-icon">${item.icon}</span>
                            <span>${item.label}</span>
                        </vaadin-item>
                    `)}
                </vaadin-list-box>
            </div>
        `;
    }

    _onSelectedChanged(e) {
        this.selectedIndex = e.detail.value;
    }

    _selectItem(index) {
        const item = this.items[index];
        if (item) {
            this.dispatchEvent(new CustomEvent('item-selected', {
                detail: { item },
                bubbles: true,
                composed: true
            }));
        }
    }

    _hoverItem(index) {
        this.selectedIndex = index;
    }

    /**
     * Handle keyboard navigation
     * @param {KeyboardEvent} event 
     * @returns {boolean} Whether the event was handled
     */
    onKeyDown(event) {
        if (event.key === 'ArrowUp') {
            this._setSelectedIndex((this.selectedIndex - 1 + this.items.length) % this.items.length);
            return true;
        }

        if (event.key === 'ArrowDown') {
            this._setSelectedIndex((this.selectedIndex + 1) % this.items.length);
            return true;
        }

        if (event.key === 'Enter') {
            this._selectItem(this.selectedIndex);
            return true;
        }

        return false;
    }

    _setSelectedIndex(index) {
        this.selectedIndex = index;
        // Directly update the vaadin-list-box since we're not focused
        const listBox = this.shadowRoot?.querySelector('vaadin-list-box');
        if (listBox) {
            listBox.selected = index;
        }
        this._scrollToSelected();
    }

    _scrollToSelected() {
        this.updateComplete.then(() => {
            const listBox = this.shadowRoot.querySelector('vaadin-list-box');
            if (listBox) {
                const items = listBox.querySelectorAll('vaadin-item');
                if (items[this.selectedIndex]) {
                    items[this.selectedIndex].scrollIntoView({ block: 'nearest' });
                }
            }
        });
    }
}

customElements.define('qwc-slash-menu', SlashMenu);
