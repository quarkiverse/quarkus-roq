/**
 * Gutter menu Lit component for TipTap editor
 * Appears in the left gutter on hover for each paragraph/block
 * Provides "+" button for creating new blocks and drag handle for reordering
 */

import { LitElement, css, html } from 'lit';
import './floating-menu.js';

export class GutterMenu extends LitElement {
    static properties = {
        visible: { type: Boolean, reflect: true },
        editor: { type: Object },
        editorElement: { type: Object },
        editorWrapper: { type: Object },
        currentBlockElement: { type: Object },
        floatingMenu: { type: Object }
    };

    static styles = css`
        :host {
            position: absolute;
            display: none;
            flex-direction: column;
            gap: var(--lumo-space-xs);
            z-index: 10;
            pointer-events: auto;
        }
        :host([visible]) {
            display: flex;
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
    `;

    constructor() {
        super();
        this.visible = false;
        this.editor = null;
        this.editorElement = null;
        this.editorWrapper = null;
        this.currentBlockElement = null;
        this.floatingMenu = null;
        this._hideTimeout = null;
        this._isHovered = false;
    }

    firstUpdated() {
        // Attach event listeners
        this.addEventListener('mouseenter', this._handleMouseEnter.bind(this));
        this.addEventListener('mouseleave', this._handleMouseLeave.bind(this));

        const addButton = this.shadowRoot.querySelector('.gutter-add-button');
        const dragButton = this.shadowRoot.querySelector('.gutter-drag-button');

        if (addButton) {
            addButton.addEventListener('click', this._handleAddClick.bind(this));
        }

        if (dragButton) {
            dragButton.setAttribute('draggable', 'true');
            dragButton.addEventListener('dragstart', this._handleDragStart.bind(this));
            dragButton.addEventListener('dragend', this._handleDragEnd.bind(this));
        }
    }

    render() {
        return html`
            <button class="gutter-menu-button gutter-add-button" title="Add block">+</button>
            <button class="gutter-menu-button gutter-drag-button" title="Drag to reorder">⋮⋮</button>
        `;
    }

    /**
     * Find the block element (paragraph, heading, etc.) at a given position
     */
    findBlockElement(pos) {
        try {
            if (pos === null || pos === undefined || !this.editor?.state?.doc) {
                return null;
            }

            // Validate position is within document bounds
            if (pos < 0 || pos > this.editor.state.doc.content.size) {
                return null;
            }

            const resolved = this.editor.state.doc.resolve(pos);
            if (!resolved?.$anchor?.parent) {
                return null;
            }

            // Find the DOM element for this node using TipTap's view
            const view = this.editor.view;
            if (!view) {
                return null;
            }

            const domAtPos = view.domAtPos(pos);
            if (!domAtPos || !domAtPos.node) {
                return null;
            }

            let element = domAtPos.node;

            // If it's a text node, get the parent element
            if (element.nodeType === Node.TEXT_NODE) {
                element = element.parentElement;
            }

            // Walk up the DOM tree to find the block element
            while (element && element !== this.editorElement) {
                if (element.nodeType === Node.ELEMENT_NODE) {
                    const tagName = element.tagName?.toLowerCase();
                    if (!tagName) break;

                    // Check if it's a block-level element
                    if (['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote', 'ul', 'ol', 'pre', 'li'].includes(tagName)) {
                        // For list items, we want the list container
                        if (tagName === 'li') {
                            const list = element.closest('ul, ol');
                            if (list) return list;
                        }
                        return element;
                    }
                    // Check if it has the ProseMirror class (TipTap's wrapper)
                    if (element.classList && element.classList.contains('ProseMirror')) {
                        break;
                    }
                }
                element = element.parentElement;
            }
        } catch (e) {
            // Silently ignore errors - they're expected when mouse is outside editor
            return null;
        }

        return null;
    }

    /**
     * Find block element from mouse event
     */
    findBlockElementFromEvent(event) {
        if (!this.editor?.view || !this.editor?.state) {
            return null;
        }

        let blockElement = null;

        // First try: Use TipTap's posAtCoords - this is the most reliable way
        try {
            const coords = this.editor.view.posAtCoords({
                left: event.clientX,
                top: event.clientY
            });

            if (coords && coords.pos !== null && coords.pos !== undefined) {
                blockElement = this.findBlockElement(coords.pos);
            }
        } catch (e) {
            // If posAtCoords fails, try direct DOM approach
        }

        // Fallback: Find block element directly from DOM using ProseMirror content
        if (!blockElement) {
            try {
                // Get the ProseMirror content element
                const pmContent = this.editorElement.querySelector('.ProseMirror');
                if (!pmContent) {
                    return null;
                }

                // Get the rect of ProseMirror to check if mouse is within bounds
                const pmRect = pmContent.getBoundingClientRect();
                const mouseX = event.clientX;
                const mouseY = event.clientY;

                // Check if mouse is within ProseMirror bounds
                if (mouseX < pmRect.left || mouseX > pmRect.right ||
                    mouseY < pmRect.top || mouseY > pmRect.bottom) {
                    return null;
                }

                // Get all block elements
                const allBlocks = pmContent.querySelectorAll('p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, pre');
                let closestBlock = null;
                let minDistance = Infinity;

                for (const block of allBlocks) {
                    const rect = block.getBoundingClientRect();
                    const centerY = rect.top + rect.height / 2;
                    const distance = Math.abs(event.clientY - centerY);

                    // Check if mouse is horizontally within the block
                    if (event.clientX >= rect.left && event.clientX <= rect.right) {
                        if (distance < minDistance) {
                            minDistance = distance;
                            closestBlock = block;
                        }
                    }
                }

                if (closestBlock) {
                    blockElement = closestBlock;
                }
            } catch (e) {
                console.warn('Error finding block element:', e);
            }
        }

        return blockElement;
    }

    /**
     * Position the gutter menu relative to a block element
     */
    position(blockElement) {
        if (!blockElement || !this.editorWrapper || !this.editorElement) {
            this.hide();
            return;
        }

        try {
            const rect = blockElement.getBoundingClientRect();
            const wrapperRect = this.editorWrapper.getBoundingClientRect();

            // Calculate position relative to wrapper
            const top = rect.top - wrapperRect.top + this.editorElement.scrollTop;
            const left = rect.left - wrapperRect.left - 35;

            this.style.top = `${top}px`;
            this.style.left = `${left}px`;
            this.show();
        } catch (e) {
            console.warn('Error positioning gutter menu:', e);
        }
    }

    /**
     * Show the gutter menu
     */
    show() {
        this.visible = true;
    }

    /**
     * Hide the gutter menu
     */
    hide() {
        this.visible = false;
    }

    /**
     * Show gutter menu for a block element
     */
    showForBlock(blockElement) {
        if (!blockElement) {
            return;
        }

        if (blockElement !== this.currentBlockElement) {
            this.currentBlockElement = blockElement;
            if (this.floatingMenu) {
                this.floatingMenu.currentBlockElement = blockElement;
            }
            this.position(blockElement);

            if (this._hideTimeout) {
                clearTimeout(this._hideTimeout);
                this._hideTimeout = null;
            }
        } else if (!this.visible) {
            // Re-position if menu was hidden
            this.position(blockElement);
        }
    }

    /**
     * Schedule hiding the menu
     */
    scheduleHide() {
        if (this._hideTimeout) return;
        this._hideTimeout = setTimeout(() => {
            // Only hide if mouse is not over the menu itself
            if (!this._isHovered && (!this.floatingMenu || !this.floatingMenu.visible)) {
                this.hide();
                if (this.floatingMenu) {
                    this.floatingMenu.hide();
                }
                this.currentBlockElement = null;
            }
            this._hideTimeout = null;
        }, 200);
    }

    /**
     * Keep menu visible (cancel hide timeout)
     */
    keepVisible() {
        if (this._hideTimeout) {
            clearTimeout(this._hideTimeout);
            this._hideTimeout = null;
        }
    }

    /**
     * Handle mouse enter - keep menu visible
     */
    _handleMouseEnter() {
        this._isHovered = true;
        this.keepVisible();
    }

    /**
     * Handle mouse leave - schedule hide
     */
    _handleMouseLeave() {
        this._isHovered = false;
        this.scheduleHide();
    }

    /**
     * Handle add button click
     */
    _handleAddClick(e) {
        e.stopPropagation();
        if (this.floatingMenu) {
            this.floatingMenu.currentBlockElement = this.currentBlockElement;
            this.floatingMenu.toggle(this.currentBlockElement);
        }
    }

    /**
     * Handle drag start
     */
    _handleDragStart(e) {
        if (!this.currentBlockElement) {
            e.preventDefault();
            return;
        }

        const pos = this.editor.view.posAtDOM(this.currentBlockElement, 0);
        if (pos === null) {
            e.preventDefault();
            return;
        }

        const resolvedPos = this.editor.state.doc.resolve(pos);
        const blockSize = resolvedPos.parent.nodeSize;
        const from = pos;
        const to = pos + blockSize;

        // Set selection to the entire block
        this.editor.chain().focus().setTextSelection({ from, to }).run();

        // Get the slice for dragging
        const slice = this.editor.state.selection.content();
        const view = this.editor.view;

        // Set up drag data
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/html', this.currentBlockElement.outerHTML);

        // Store drag info in the view
        view.dragging = { slice, move: true };

        // Create drag image
        const dragImage = this.currentBlockElement.cloneNode(true);
        dragImage.style.opacity = '0.5';
        dragImage.style.position = 'absolute';
        dragImage.style.top = '-1000px';
        document.body.appendChild(dragImage);
        e.dataTransfer.setDragImage(dragImage, 0, 0);

        // Clean up drag image after a short delay
        setTimeout(() => {
            if (dragImage.parentElement) {
                document.body.removeChild(dragImage);
            }
        }, 0);
    }

    /**
     * Handle drag end
     */
    _handleDragEnd() {
        if (this.editor?.view) {
            this.editor.view.dragging = null;
        }
    }

    /**
     * Update position on scroll
     */
    updatePositionOnScroll() {
        if (this.currentBlockElement && this.visible) {
            this.position(this.currentBlockElement);
        }
    }
}

customElements.define('qwc-gutter-menu', GutterMenu);

