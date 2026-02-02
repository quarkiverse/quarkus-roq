/**
 * Gutter menu Lit component for TipTap editor
 * Appears in the left gutter on hover for each paragraph/block
 * Provides "+" button for creating new blocks and drag handle for reordering
 */

import {LitElement, css, html} from 'lit';
import { editorContext } from './editor-context.js';
import { ContextConsumer } from '../../bundle.js';

export class GutterMenu extends LitElement {
    static properties = {};

    constructor() {
        super();

        this._editorConsumer = new ContextConsumer(this, {
            context: editorContext,
            subscribe: true
        });
    }

    static styles = css`
        :host {
            display: flex;
            flex-direction: row;
            gap: 2px;
            padding-right: 5px;
            padding-left: 5px;
            position: relative;
            transition-property: top;
            transition-duration: .2s;
            transition-timing-function: ease-out;
        }
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
        ::slotted(qwc-floating-menu) {
            position: absolute;
            bottom: 100%;
            left: 0;
            margin-bottom: 5px;
        }
    `;

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
          <slot></slot>
        `;
    }

    get editor() {
        return this._editorConsumer.value?.editor || null;
    }

    get editorElement() {
        return this._editorConsumer.value?.editorElement || null;
    }

    /**
     * Position the floating menu relative to a block element
     * Now uses CSS positioning relative to parent gutter menu
     */
    position(e) {
        if (!this.editor || !this.editorElement) return;

        const coords = this.editor.view.posAtCoords({
            left: e.clientX,
            top: e.clientY
        });
        this.pos = coords.pos;
    }

    /**
     * Handle add button click
     */
    _handleAddClick(e) {
        e.stopPropagation();
        // Use composedPath to find the button in Shadow DOM
        const path = e.composedPath();
        if (!this.editor) return;

        const coords = this.editor.view.posAtCoords({
            left: e.clientX,
            top: e.clientY
        });

        const pos = coords.pos;
        if (pos === null) return;

        this.editor.commands.openSlashMenu(pos);
    }
}

customElements.define('qwc-gutter-menu', GutterMenu);

