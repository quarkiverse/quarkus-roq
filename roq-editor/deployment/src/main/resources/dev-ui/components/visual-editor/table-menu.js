/**
 * Table menu component for TipTap editor
 * Shows "..." buttons for row and column operations
 * - Column button appears centered above the current column
 * - Row button appears to the left of the current row
 */

import '@vaadin/button';
import '@vaadin/context-menu';
import { LitElement, css, html } from 'lit';
import { editorContext } from './editor-context.js';
import { ContextConsumer } from '../../bundle.js';

export class TableMenu extends LitElement {
    static properties = {
        _visible: { state: true },
        _columnButtonStyle: { state: true },
        _rowButtonStyle: { state: true },
        _canDeleteRow: { state: true },
        _canDeleteColumn: { state: true },
    };

    constructor() {
        super();
        this._visible = false;
        this._columnButtonStyle = '';
        this._rowButtonStyle = '';
        this._canDeleteRow = true;
        this._canDeleteColumn = true;
        this._updateFrame = null;
        this._lastUpdateTime = 0;
        this._updateThrottle = 50;
        
        this._editorConsumer = new ContextConsumer(this, {
            context: editorContext,
            subscribe: true,
            callback: () => {
                this._setupEditorUpdates();
            }
        });
    }

    static styles = css`
        :host {
            display: block;
            position: absolute;
            top: 0;
            left: 0;
            pointer-events: none;
            z-index: 50;
        }
        
        .button-container {
            position: absolute;
            pointer-events: auto;
        }
        
        .menu-button {
            min-width: 24px;
            width: 24px;
            height: 24px;
            padding: 0;
        }
        
        [hidden] {
            display: none !important;
        }
    `;

    get editor() {
        return this._editorConsumer.value?.editor || null;
    }

    get editorElement() {
        return this._editorConsumer.value?.editorElement || null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._setupEditorUpdates();
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this._cleanupEditorUpdates();
    }

    _setupEditorUpdates() {
        this._cleanupEditorUpdates();
        
        if (!this.editor) return;
        
        const updateLoop = () => {
            if (this.editor && this.isConnected) {
                const now = Date.now();
                if ((now - this._lastUpdateTime) >= this._updateThrottle) {
                    this._updatePosition();
                    this._lastUpdateTime = now;
                }
                this._updateFrame = requestAnimationFrame(updateLoop);
            } else {
                this._cleanupEditorUpdates();
            }
        };
        
        this._updateFrame = requestAnimationFrame(updateLoop);
    }

    _cleanupEditorUpdates() {
        if (this._updateFrame) {
            cancelAnimationFrame(this._updateFrame);
            this._updateFrame = null;
        }
    }

    _updatePosition() {
        if (!this.editor || !this.editorElement) {
            this._visible = false;
            return;
        }

        const isInTable = this.editor.isActive('table');
        if (!isInTable) {
            this._visible = false;
            return;
        }

        this._visible = true;
        this._canDeleteRow = this.editor.can().deleteRow();
        this._canDeleteColumn = this.editor.can().deleteColumn();

        // Find the current cell
        const { selection } = this.editor.state;
        const domAtPos = this.editor.view.domAtPos(selection.from);
        const node = domAtPos.node;
        
        // Find the cell element (td or th)
        let cellElement = node.nodeType === Node.TEXT_NODE 
            ? node.parentElement?.closest('td, th')
            : node.closest?.('td, th') || node;
        
        if (!cellElement || !['TD', 'TH'].includes(cellElement.tagName)) {
            this._visible = false;
            return;
        }

        // Find the table element
        const tableElement = cellElement.closest('table');
        if (!tableElement) {
            this._visible = false;
            return;
        }

        // Get editor container's bounding rect for relative positioning
        const editorRect = this.editorElement.getBoundingClientRect();
        const tableRect = tableElement.getBoundingClientRect();
        const cellRect = cellElement.getBoundingClientRect();

        // Position column button: centered above the current column
        const columnButtonLeft = cellRect.left - editorRect.left + (cellRect.width / 2) - 12; // 12 = half button width
        const columnButtonTop = tableRect.top - editorRect.top - 28; // 28 = button height + gap
        this._columnButtonStyle = `left: ${columnButtonLeft}px; top: ${columnButtonTop}px;`;

        // Position row button: to the left of the current row, vertically centered
        const rowButtonLeft = tableRect.left - editorRect.left - 28; // 28 = button width + gap
        const rowButtonTop = cellRect.top - editorRect.top + (cellRect.height / 2) - 12; // 12 = half button height
        this._rowButtonStyle = `left: ${rowButtonLeft}px; top: ${rowButtonTop}px;`;
    }

    get _columnMenuItems() {
        return [
            { text: 'Add column before', action: 'addColumnBefore' },
            { text: 'Add column after', action: 'addColumnAfter' },
            { component: 'hr' },
            { text: 'Delete column', action: 'deleteColumn', disabled: !this._canDeleteColumn, className: 'danger' }
        ];
    }

    get _rowMenuItems() {
        return [
            { text: 'Add row above', action: 'addRowBefore' },
            { text: 'Add row below', action: 'addRowAfter' },
            { component: 'hr' },
            { text: 'Delete row', action: 'deleteRow', disabled: !this._canDeleteRow, className: 'danger' }
        ];
    }

    render() {
        if (!this._visible) {
            return html``;
        }

        return html`
            <!-- Column menu button -->
            <div class="button-container" style="${this._columnButtonStyle}">
                <vaadin-context-menu
                    open-on="click"
                    .items="${this._columnMenuItems}"
                    @item-selected="${this._onColumnMenuItemSelected}">
                    <vaadin-button theme="icon" class="menu-button" title="Column options">
                        <vaadin-icon icon="font-awesome-solid:ellipsis"></vaadin-icon>
                    </vaadin-button>
                </vaadin-context-menu>
            </div>
            
            <!-- Row menu button -->
            <div class="button-container" style="${this._rowButtonStyle}">
                <vaadin-context-menu
                    open-on="click"
                    .items="${this._rowMenuItems}"
                    @item-selected="${this._onRowMenuItemSelected}">
                    <vaadin-button theme="icon" class="menu-button" title="Row options">
                        <vaadin-icon icon="font-awesome-solid:ellipsis"></vaadin-icon>
                    </vaadin-button>
                </vaadin-context-menu>
            </div>
        `;
    }

    _onColumnMenuItemSelected(e) {
        const action = e.detail.value.action;
        if (action) {
            this._executeCommand(action);
        }
    }

    _onRowMenuItemSelected(e) {
        const action = e.detail.value.action;
        if (action) {
            this._executeCommand(action);
        }
    }

    _executeCommand(command) {
        if (!this.editor) return;
        
        switch (command) {
            case 'addColumnBefore':
                this.editor.chain().focus().addColumnBefore().run();
                break;
            case 'addColumnAfter':
                this.editor.chain().focus().addColumnAfter().run();
                break;
            case 'deleteColumn':
                this.editor.chain().focus().deleteColumn().run();
                break;
            case 'addRowBefore':
                this.editor.chain().focus().addRowBefore().run();
                break;
            case 'addRowAfter':
                this.editor.chain().focus().addRowAfter().run();
                break;
            case 'deleteRow':
                this.editor.chain().focus().deleteRow().run();
                break;
        }
    }
}

customElements.define('qwc-table-menu', TableMenu);
