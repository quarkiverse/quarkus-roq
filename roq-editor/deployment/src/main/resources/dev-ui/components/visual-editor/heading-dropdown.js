/**
 * Heading dropdown component for TipTap editor
 * Reusable dropdown for selecting heading levels (H1-H6) or paragraph
 * Uses Vaadin combo-box for the dropdown
 */

import '@vaadin/combo-box';
import { LitElement, css, html } from 'lit';
import { editorContext } from './editor-context.js';
import { ContextConsumer } from '../../bundle.js';

export class HeadingDropdown extends LitElement {
    static properties = {
        /** Mode: 'insert' for floating menu, 'toggle' for bubble menu */
        mode: { type: String },
        /** Position to insert content at (used in 'insert' mode) */
        insertPos: { type: Number },
        /** Currently selected value */
        _selectedValue: { state: true },
        /** Whether the dropdown is open */
        opened: { type: Boolean, reflect: true },
    };

    constructor() {
        super();
        this.mode = 'toggle'; // 'toggle' or 'insert'
        this.insertPos = null;
        this._selectedValue = 'paragraph';
        this._isUpdatingFromEditor = false;
        this.opened = false;
        
        this._items = [
            { label: 'Paragraph', value: 'paragraph' },
            { label: 'Heading 1', value: 'h1' },
            { label: 'Heading 2', value: 'h2' },
            { label: 'Heading 3', value: 'h3' },
            { label: 'Heading 4', value: 'h4' },
            { label: 'Heading 5', value: 'h5' },
            { label: 'Heading 6', value: 'h6' },
        ];
        
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
            display: inline-block;
        }
        
        vaadin-combo-box {
            --vaadin-combo-box-overlay-width: 150px;
            width: 120px;
        }
        
        vaadin-combo-box::part(input-field) {
            background: transparent;
            border: none;
            min-height: 32px;
            padding: 0;
        }
        
        vaadin-combo-box::part(toggle-button) {
            color: var(--lumo-body-text-color);
        }
        
        vaadin-combo-box:hover::part(input-field) {
            background: var(--lumo-contrast-10pct);
        }
    `;

    get editor() {
        return this._editorConsumer.value?.editor || null;
    }

    connectedCallback() {
        super.connectedCallback();
        // Set up periodic updates for selection state when editor is active
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

    _getCurrentHeading() {
        if (!this.editor) return null;
        
        for (let level = 1; level <= 6; level++) {
            if (this.editor.isActive('heading', { level })) {
                return level;
            }
        }
        return null;
    }

    _updateSelectedValue() {
        const level = this._getCurrentHeading();
        const newValue = level ? `h${level}` : 'paragraph';
        if (this._selectedValue !== newValue) {
            this._isUpdatingFromEditor = true;
            this._selectedValue = newValue;
            // Reset the flag after the update cycle
            requestAnimationFrame(() => {
                this._isUpdatingFromEditor = false;
            });
        }
    }

    _handleChange(e) {
        // Ignore changes triggered by programmatic updates
        if (this._isUpdatingFromEditor) return;
        
        const comboBox = e.target;
        const selectedItem = comboBox.selectedItem;
        
        if (!selectedItem || !this.editor) return;

        const value = selectedItem.value;
        
        if (value === 'paragraph') {
            this._selectParagraph();
        } else if (value.startsWith('h')) {
            const level = parseInt(value.charAt(1));
            this._selectHeading(level);
        }
    }

    _selectHeading(level) {
        if (!this.editor) return;
        
        if (this.mode === 'insert' && this.insertPos !== null) {
            // Insert mode: insert a new heading at the specified position
            this.editor.chain()
                .focus()
                .insertContentAt(this.insertPos, { 
                    type: 'heading', 
                    attrs: { level: level } 
                })
                .setTextSelection(this.insertPos + 1)
                .run();
            
            this.dispatchEvent(new CustomEvent('heading-inserted', {
                bubbles: true,
                composed: true,
                detail: { level }
            }));
        } else {
            // Toggle mode: change current block to heading
            this.editor.chain().focus().toggleHeading({ level }).run();
        }
    }

    _selectParagraph() {
        if (!this.editor) return;
        
        if (this.mode === 'insert' && this.insertPos !== null) {
            // Insert mode: insert a new paragraph at the specified position
            this.editor.chain()
                .focus()
                .insertContentAt(this.insertPos, { 
                    type: 'paragraph'
                })
                .setTextSelection(this.insertPos + 1)
                .run();
            
            this.dispatchEvent(new CustomEvent('paragraph-inserted', {
                bubbles: true,
                composed: true
            }));
        } else {
            // Toggle mode: change current block to paragraph
            this.editor.chain().focus().setParagraph().run();
        }
    }

    _handleOpenedChanged(e) {
        this.opened = e.detail.value;
        this.dispatchEvent(new CustomEvent('dropdown-opened-changed', {
            bubbles: true,
            composed: true,
            detail: { opened: this.opened }
        }));
    }

    render() {
        return html`
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

customElements.define('qwc-heading-dropdown', HeadingDropdown);
