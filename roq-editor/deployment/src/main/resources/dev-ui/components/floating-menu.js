/**
 * Floating menu component for TipTap editor
 * Lit component that appears when the "+" button is clicked in the gutter menu
 */

import { LitElement, css, html } from 'lit';

export class FloatingMenu extends LitElement {
    static properties = {
        visible: { type: Boolean, reflect: true },
        editor: { type: Object },
        editorElement: { type: Object },
        currentBlockElement: { type: Object }
    };

    static styles = css`
        :host {
            position: absolute;
            z-index: 20;
            pointer-events: auto;
            display: none;
        }
        :host([visible]) {
            display: block;
        }
        .tiptap-menu {
            display: flex;
            gap: var(--lumo-space-xs);
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-xs);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
            margin: 0;
        }
        .tiptap-menu-button {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: var(--lumo-space-xs) var(--lumo-space-s);
            border: none;
            background: transparent;
            color: var(--lumo-body-text-color);
            cursor: pointer;
            border-radius: var(--lumo-border-radius-s);
            font-size: var(--lumo-font-size-s);
            min-width: 32px;
            height: 32px;
        }
        .tiptap-menu-button:hover {
            background: var(--lumo-contrast-10pct);
        }
        .tiptap-menu-button.is-active {
            background: var(--lumo-primary-color-10pct);
            color: var(--lumo-primary-color);
        }
        .tiptap-menu-button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .tiptap-menu-separator {
            width: 1px;
            background: var(--lumo-contrast-20pct);
            margin: var(--lumo-space-xs) 0;
        }
    `;

    constructor() {
        super();
        this.visible = false;
        this.editor = null;
        this.editorElement = null;
        this.currentBlockElement = null;
    }

    firstUpdated() {
        // Attach click listeners
        this.addEventListener('click', this._handleClick.bind(this));
        this.addEventListener('mouseenter', this._handleMouseEnter.bind(this));
        this.addEventListener('mouseleave', this._handleMouseLeave.bind(this));
    }

    render() {
        return html`
            <div class="tiptap-menu">
                <button class="tiptap-menu-button" data-command="heading" data-level="1" title="Heading 1">H1</button>
                <button class="tiptap-menu-button" data-command="heading" data-level="2" title="Heading 2">H2</button>
                <button class="tiptap-menu-button" data-command="heading" data-level="3" title="Heading 3">H3</button>
                <button class="tiptap-menu-button" data-command="heading" data-level="4" title="Heading 4">H4</button>
                <button class="tiptap-menu-button" data-command="heading" data-level="5" title="Heading 5">H5</button>
                <button class="tiptap-menu-button" data-command="heading" data-level="6" title="Heading 6">H6</button>
                <div class="tiptap-menu-separator"></div>
                <button class="tiptap-menu-button" data-command="bulletList" title="Bullet List">• List</button>
                <button class="tiptap-menu-button" data-command="orderedList" title="Ordered List">1. List</button>
                <div class="tiptap-menu-separator"></div>
                <button class="tiptap-menu-button" data-command="codeBlock" title="Code Block">Code</button>
            </div>
        `;
    }

    /**
     * Position the floating menu relative to a block element
     */
    position(blockElement) {
        if (!blockElement || !this.editorElement) {
            this.hide();
            return;
        }

        // Show first to get accurate dimensions
        this.show();
        
        // Use requestAnimationFrame to ensure the element is rendered before getting dimensions
        requestAnimationFrame(() => {
            const rect = blockElement.getBoundingClientRect();
            const editorRect = this.editorElement.getBoundingClientRect();
            const menuRect = this.getBoundingClientRect();

            // Position above the block, aligned with the gutter menu
            const top = rect.top - editorRect.top + this.editorElement.scrollTop - menuRect.height - 5;
            const left = rect.left - editorRect.left - 10;

            this.style.top = `${top}px`;
            this.style.left = `${left}px`;
        });
    }

    /**
     * Show the floating menu
     */
    show() {
        this.visible = true;
    }

    /**
     * Hide the floating menu
     */
    hide() {
        this.visible = false;
    }

    /**
     * Toggle the floating menu visibility
     */
    toggle(blockElement) {
        if (this.visible) {
            this.hide();
        } else {
            this.position(blockElement);
        }
    }

    /**
     * Handle button clicks in the menu
     */
    _handleClick(e) {
        // Use composedPath to find the button in Shadow DOM
        const path = e.composedPath();
        const button = path.find(el => el.classList && el.classList.contains('tiptap-menu-button'));
        if (!button || !this.editor || !this.currentBlockElement) return;

        const command = button.dataset.command;
        const level = button.dataset.level;

        // Get position after current block
        const pos = this.editor.view.posAtDOM(this.currentBlockElement, 0);
        if (pos === null) return;

        if (command === 'bulletList') {
            this.editor.chain()
                .focus()
                .insertContentAt(pos, {
                    type: 'bulletList',
                    content: [{
                        type: 'listItem',
                        content: [{
                            type: 'paragraph',
                            content: []
                        }]
                    }]
                })
                .setTextSelection(pos + 1)
                .run();
        } else if (command === 'orderedList') {
            this.editor.chain()
                .focus()
                .insertContentAt(pos, {
                    type: 'orderedList',
                    content: [{
                        type: 'listItem',
                        content: [{
                            type: 'paragraph',
                            content: []
                        }]
                    }]
                })
                .setTextSelection(pos + 1)
                .run();
        } else {
            this.editor.chain()
                .focus()
                .insertContentAt(pos, { 
                    type: command, 
                    attrs: { level: parseInt(level) } 
                })
                .setTextSelection(pos - 1)
                .run();
        }

        this.hide();
    }

    /**
     * Handle mouse enter - keep menu visible
     */
    _handleMouseEnter() {
        // Dispatch event to notify parent to keep menu visible
        this.dispatchEvent(new CustomEvent('menu-enter', { bubbles: true, composed: true }));
    }

    /**
     * Handle mouse leave - hide menu
     */
    _handleMouseLeave() {
        this.hide();
    }
}

customElements.define('qwc-floating-menu', FloatingMenu);
