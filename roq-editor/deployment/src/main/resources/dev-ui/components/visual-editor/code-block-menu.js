/**
 * Code block menu component for TipTap editor
 * Shows a language dropdown when the cursor is inside a code block
 */

import { LitElement, css, html } from 'lit';
import { editorContext } from './editor-context.js';
import { ContextConsumer } from '../../bundle.js';
import './language-dropdown.js';

export class CodeBlockMenu extends LitElement {
    static properties = {
        _visible: { state: true },
        _menuStyle: { state: true },
        _anchorPos: { state: true },
        _dropdownOpen: { state: true },
    };

    static MENU_INSET = 8;

    constructor() {
        super();
        this._visible = false;
        this._menuStyle = '';
        this._anchorPre = null;
        this._anchorPos = null;
        this._dropdownOpen = false;
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

        .menu-container {
            position: absolute;
            pointer-events: auto;
            padding: 0.15rem 0.25rem;
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-s);
            box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
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

    updated(changedProperties) {
        if (changedProperties.has('_visible') && this._visible) {
            requestAnimationFrame(() => this._applyMenuPosition());
        }
    }

    _setupEditorUpdates() {
        this._cleanupEditorUpdates();

        if (!this.editor) {
            return;
        }

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

    _findPreElement(node) {
        if (!node) {
            return null;
        }

        if (node.nodeType === Node.TEXT_NODE) {
            return node.parentElement?.closest('pre') ?? null;
        }

        if (node.nodeName === 'PRE') {
            return node;
        }

        return node.closest?.('pre') ?? null;
    }

    _findCodeBlockPos(state) {
        const { $from } = state.selection;
        for (let depth = $from.depth; depth > 0; depth--) {
            if ($from.node(depth).type.name === 'codeBlock') {
                return $from.before(depth);
            }
        }
        return null;
    }

    _hideMenu() {
        this._visible = false;
        this._anchorPre = null;
        this._anchorPos = null;
    }

    _updatePosition() {
        if (!this.editor || !this.editorElement) {
            this._hideMenu();
            return;
        }

        if (this.editor.isActive('table')) {
            this._hideMenu();
            return;
        }

        const inCodeBlock = this.editor.isActive('codeBlock');
        const anchorPos = this._findCodeBlockPos(this.editor.state);

        if (!inCodeBlock && !this._dropdownOpen) {
            this._hideMenu();
            return;
        }

        if (anchorPos != null) {
            this._anchorPos = anchorPos;
        }

        if (this._anchorPos == null) {
            this._hideMenu();
            return;
        }

        const { selection } = this.editor.state;
        const domAtPos = this.editor.view.domAtPos(selection.from);
        const preElement = this._findPreElement(domAtPos.node) ?? this._anchorPre;

        if (!preElement) {
            if (!this._dropdownOpen) {
                this._hideMenu();
            }
            return;
        }

        this._visible = true;
        this._anchorPre = preElement;
        this._applyMenuPosition();
    }

    _applyMenuPosition() {
        if (!this._visible || !this._anchorPre || !this.editorElement) {
            return;
        }

        const preElement = this._anchorPre;
        const editorRect = this.editorElement.getBoundingClientRect();
        const preRect = preElement.getBoundingClientRect();
        const menu = this.shadowRoot?.querySelector('.menu-container');

        if (!menu) {
            requestAnimationFrame(() => this._applyMenuPosition());
            return;
        }

        const menuWidth = menu.offsetWidth;
        const menuHeight = menu.offsetHeight;
        if (menuWidth === 0 || menuHeight === 0) {
            requestAnimationFrame(() => this._applyMenuPosition());
            return;
        }

        const styles = getComputedStyle(preElement);
        const paddingTop = parseFloat(styles.paddingTop) || CodeBlockMenu.MENU_INSET;
        const paddingRight = parseFloat(styles.paddingRight) || CodeBlockMenu.MENU_INSET;
        const paddingLeft = parseFloat(styles.paddingLeft) || CodeBlockMenu.MENU_INSET;

        const preLeft = preRect.left - editorRect.left;
        const preRight = preRect.right - editorRect.left;
        const preTop = preRect.top - editorRect.top;

        let left = preRight - paddingRight - menuWidth;
        const minLeft = preLeft + paddingLeft;
        const maxLeft = preRight - paddingRight - menuWidth;
        left = Math.min(Math.max(left, minLeft), maxLeft);

        const top = preTop + Math.max(
            CodeBlockMenu.MENU_INSET / 2,
            (paddingTop - menuHeight) / 2
        );

        const menuStyle = `left: ${left}px; top: ${top}px;`;
        if (this._menuStyle !== menuStyle) {
            this._menuStyle = menuStyle;
        }
    }

    _handleDropdownOpenedChanged(e) {
        this._dropdownOpen = e.detail.opened;
        if (!this._dropdownOpen) {
            this._updatePosition();
        }
    }

    render() {
        if (!this._visible || this._anchorPos == null) {
            return html``;
        }

        return html`
            <div class="menu-container" style="${this._menuStyle}">
                <qwc-language-dropdown
                    .codeBlockPos="${this._anchorPos}"
                    @dropdown-opened-changed="${this._handleDropdownOpenedChanged}"
                ></qwc-language-dropdown>
            </div>
        `;
    }
}

customElements.define('qwc-code-block-menu', CodeBlockMenu);
