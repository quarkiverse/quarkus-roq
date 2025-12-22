/**
 * Gutter menu Lit component for TipTap editor
 * Appears in the left gutter on hover for each paragraph/block
 * Provides "+" button for creating new blocks and drag handle for reordering
 */

import { LitElement, css, html } from 'lit';
import './floating-menu.js';

export class GutterMenu extends LitElement {
    static properties = {
        floatingMenu: { type: Object }
    };

    static styles = css`
        .gutter-menu-button {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 24px;
            height: 24px;
            padding: 0;
            border: 1px solid var(--lumo-contrast-20pct);
            background: var(--lumo-base-color);
            color: var(--lumo-body-text-color);
            cursor: pointer;
            border-radius: var(--lumo-border-radius-s);
            font-size: var(--lumo-font-size-s);
            font-weight: 600;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }
        .gutter-menu-button:hover {
            background: var(--lumo-primary-color-10pct);
            border-color: var(--lumo-primary-color);
            color: var(--lumo-primary-color);
        }
        .gutter-add-button {
            font-size: var(--lumo-font-size-l);
            line-height: 1;
        }
        .gutter-drag-button {
            font-size: var(--lumo-font-size-xs);
            line-height: 1.2;
            cursor: grab;
        }
        .gutter-drag-button:active {
            cursor: grabbing;
        }
    `;

    constructor() {
        super();
        this.floatingMenu = null;
    }

    firstUpdated() {
        const addButton = this.shadowRoot.querySelector('.gutter-add-button');

        if (addButton) {
            addButton.addEventListener('click', this._handleAddClick.bind(this));
        }
    }

    render() {
        return html`
            <button class="gutter-menu-button gutter-add-button" title="Add block">+</button>
            <button class="gutter-menu-button gutter-drag-button" dragable title="Drag to reorder">⋮⋮</button>
        `;
    }

    /**
     * Handle add button click
     */
    _handleAddClick(e) {
        e.stopPropagation();
        if (this.floatingMenu) {
            this.floatingMenu.toggle(e);
        }
    }
}

customElements.define('qwc-gutter-menu', GutterMenu);

