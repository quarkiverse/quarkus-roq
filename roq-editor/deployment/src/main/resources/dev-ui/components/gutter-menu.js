/**
 * Gutter menu component for TipTap editor
 * Appears in the left gutter on hover for each paragraph/block
 * Provides "+" button for creating new blocks and drag handle for reordering
 */

import { renderFloatingMenu } from './floating-menu.js';

export function initializeGutterMenu(editorElement, editor) {
    if (!editorElement || !editor) {
        return;
    }

    if (!editor.view || !editor.state) {
        // Retry after a short delay
        setTimeout(() => initializeGutterMenu(editorElement, editor), 100);
        return;
    }

    // Get the root node (could be document or shadow root)
    const root = editorElement.getRootNode();
    const isShadowRoot = root instanceof ShadowRoot;

    // Find the editor wrapper - it should be the parent of editorElement
    let editorWrapper = editorElement.parentElement;

    // If we're in shadow DOM and parentElement is null, search in the shadow root
    if (!editorWrapper && isShadowRoot) {
        editorWrapper = root.querySelector('.editor-wrapper');
    }

    // Fallback: if still no wrapper, use the editor element's parent or the editor itself
    if (!editorWrapper) {
        editorWrapper = editorElement.parentElement || editorElement;
    }

    // Make sure wrapper has position relative for absolute positioning to work
    if (editorWrapper && editorWrapper !== editorElement) {
        const wrapperStyle = window.getComputedStyle(editorWrapper);
        if (wrapperStyle.position === 'static') {
            editorWrapper.style.position = 'relative';
        }
    }

    // Check if gutter menu already exists to avoid duplicates
    const existingMenu = editorWrapper.querySelector('.gutter-menu');
    if (existingMenu) {
        return; // Already initialized
    }

    const gutterMenu = document.createElement('div');
    gutterMenu.className = 'gutter-menu';

    editorWrapper.appendChild(gutterMenu);

    // Create the menu buttons with inline styles (since we're in shadow DOM)
    const addButton = document.createElement('button');
    addButton.className = 'gutter-menu-button gutter-add-button';
    addButton.innerHTML = '+';
    addButton.title = 'Add block';
    addButton.setAttribute('data-action', 'add');

    const dragButton = document.createElement('button');
    dragButton.className = 'gutter-menu-button gutter-drag-button';
    dragButton.innerHTML = '⋮⋮';
    dragButton.title = 'Drag to reorder';
    dragButton.setAttribute('data-action', 'drag');

    gutterMenu.appendChild(addButton);
    gutterMenu.appendChild(dragButton);

    // Create floating menu for add button
    const floatingMenu = document.createElement('div');
    floatingMenu.className = 'gutter-floating-menu';
    floatingMenu.style.display = 'none';
    floatingMenu.style.position = 'absolute';
    floatingMenu.style.zIndex = '20';
    floatingMenu.style.pointerEvents = 'auto';
    editorWrapper.appendChild(floatingMenu);

    // Render floating menu content using shared function
    floatingMenu.innerHTML = renderFloatingMenu();

    let currentBlockElement = null;
    let hideTimeout = null;

    // Function to find the block element (paragraph, heading, etc.) at a given position
    function findBlockElement(pos) {
        try {
            if (pos === null || pos === undefined || !editor.state?.doc) {
                return null;
            }

            // Validate position is within document bounds
            if (pos < 0 || pos > editor.state.doc.content.size) {
                return null;
            }

            const resolved = editor.state.doc.resolve(pos);
            if (!resolved?.$anchor?.parent) {
                return null;
            }

            // Find the DOM element for this node using TipTap's view
            const view = editor.view;
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
            while (element && element !== editorElement) {
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

    // Function to position the gutter menu
    function positionGutterMenu(blockElement) {
        if (!blockElement) {
            gutterMenu.style.display = 'none';
            return;
        }

        try {
            const rect = blockElement.getBoundingClientRect();
            const wrapperRect = editorWrapper.getBoundingClientRect();

            // Calculate position relative to wrapper
            const top = rect.top - wrapperRect.top + editorElement.scrollTop;
            const left = rect.left - wrapperRect.left - 35;

            gutterMenu.style.display = 'flex';
            gutterMenu.style.top = `${top}px`;
            gutterMenu.style.left = `${left}px`;
        } catch (e) {
            console.warn('Error positioning gutter menu:', e);
        }
    }

    // Function to position the floating menu
    function positionFloatingMenu(blockElement) {
        if (!blockElement) {
            floatingMenu.style.display = 'none';
            return;
        }

        const rect = blockElement.getBoundingClientRect();
        const editorRect = editorElement.getBoundingClientRect();
        const menuRect = floatingMenu.getBoundingClientRect();

        floatingMenu.style.display = 'block';
        // Position above the block, aligned with the gutter menu
        floatingMenu.style.top = `${rect.top - editorRect.top + editorElement.scrollTop - menuRect.height - 5}px`;
        floatingMenu.style.left = `${rect.left - editorRect.left - 10}px`;
    }

    // Show gutter menu on hover
    function showGutterMenu(event) {
        try {
            if (!editor.view || !editor.state) {
                return;
            }

            let blockElement = null;

            // First try: Use TipTap's posAtCoords - this is the most reliable way
            try {
                const coords = editor.view.posAtCoords({
                    left: event.clientX,
                    top: event.clientY
                });

                if (coords && coords.pos !== null && coords.pos !== undefined) {
                    blockElement = findBlockElement(coords.pos);
                }
            } catch (e) {
                // If posAtCoords fails, try direct DOM approach
            }

            // Fallback: Find block element directly from DOM using ProseMirror content
            if (!blockElement) {
                try {
                    // Get the ProseMirror content element
                    const pmContent = editorElement.querySelector('.ProseMirror');
                    if (!pmContent) {
                        return;
                    }

                    // Get the rect of ProseMirror to check if mouse is within bounds
                    const pmRect = pmContent.getBoundingClientRect();
                    const mouseX = event.clientX;
                    const mouseY = event.clientY;

                    // Check if mouse is within ProseMirror bounds
                    if (mouseX < pmRect.left || mouseX > pmRect.right ||
                        mouseY < pmRect.top || mouseY > pmRect.bottom) {
                        return;
                    }

                    if (!blockElement && pmContent) {
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
                    }
                } catch (e) {
                    console.warn('Error finding block element:', e);
                }
            }

            // Show and position the menu if we found a block element
            if (blockElement) {
                if (blockElement !== currentBlockElement) {
                    currentBlockElement = blockElement;
                    positionGutterMenu(blockElement);

                    if (hideTimeout) {
                        clearTimeout(hideTimeout);
                        hideTimeout = null;
                    }
                } else if (gutterMenu.style.display === 'none') {
                    // Re-position if menu was hidden
                    positionGutterMenu(blockElement);
                }
            }
        } catch (e) {
            console.warn('Error showing gutter menu:', e);
        }
    }

    // Hide gutter menu
    function hideGutterMenu() {
        if (hideTimeout) return;
        hideTimeout = setTimeout(() => {
            // Only hide if mouse is not over the menu itself
            if (!gutterMenu.matches(':hover') && !floatingMenu.matches(':hover')) {
                gutterMenu.style.display = 'none';
                floatingMenu.style.display = 'none';
                currentBlockElement = null;
            }
            hideTimeout = null;
        }, 200);
    }

    // Keep menu visible when hovering over it
    function keepMenuVisible() {
        if (hideTimeout) {
            clearTimeout(hideTimeout);
            hideTimeout = null;
        }
    }

    // Event listeners
    const handleMouseMove = (e) => {
        const rect = editorElement.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        if (x >= 0 && x <= rect.width && y >= 0 && y <= rect.height) {
            showGutterMenu(e);
        } else {
            hideGutterMenu();
        }
    };

    // Attach to both editorElement and document for better coverage
    editorElement.addEventListener('mousemove', handleMouseMove, true);

    // Also listen on the document to catch events that might bubble up
    const handleDocumentMouseMove = (e) => {
        // Only handle if it's within the editor area
        const rect = editorElement.getBoundingClientRect();
        if (e.clientX >= rect.left && e.clientX <= rect.right &&
            e.clientY >= rect.top && e.clientY <= rect.bottom) {
            handleMouseMove(e);
        }
    };
    document.addEventListener('mousemove', handleDocumentMouseMove, true);

    gutterMenu.addEventListener('mouseenter', keepMenuVisible);
    gutterMenu.addEventListener('mouseleave', hideGutterMenu);
    floatingMenu.addEventListener('mouseenter', keepMenuVisible);
    floatingMenu.addEventListener('mouseleave', () => {
        floatingMenu.style.display = 'none';
    });

    // Handle add button click
    addButton.addEventListener('click', (e) => {
        e.stopPropagation();
        if (floatingMenu.style.display === 'none') {
            positionFloatingMenu(currentBlockElement);
        } else {
            floatingMenu.style.display = 'none';
        }
    });

    // Handle floating menu button clicks
    floatingMenu.addEventListener('click', (e) => {
        const button = e.target.closest('.tiptap-menu-button');
        if (!button || !editor) return;

        const command = button.dataset.command;
        const level = button.dataset.level;

        // Get position after current block
        if (!currentBlockElement) return;

        const pos = editor.view.posAtDOM(currentBlockElement, 0);
        if (pos === null) return;

        if (command === 'bulletList') {
            editor.chain()
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
            editor.chain()
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
            editor.chain().focus().insertContentAt(pos, { type: command, attrs: { level: parseInt(level) } }).setTextSelection(pos - 1).run();
        }

        floatingMenu.style.display = 'none';
    });

    // Handle drag button - use ProseMirror's drag functionality
    dragButton.setAttribute('draggable', 'true');

    dragButton.addEventListener('dragstart', (e) => {
        if (!currentBlockElement) {
            e.preventDefault();
            return;
        }

        const pos = editor.view.posAtDOM(currentBlockElement, 0);
        if (pos === null) {
            e.preventDefault();
            return;
        }

        const resolvedPos = editor.state.doc.resolve(pos);
        const blockSize = resolvedPos.parent.nodeSize;
        const from = pos;
        const to = pos + blockSize;

        // Set selection to the entire block
        editor.chain().focus().setTextSelection({ from, to }).run();

        // Get the slice for dragging
        const slice = editor.state.selection.content();
        const view = editor.view;

        // Set up drag data
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/html', currentBlockElement.outerHTML);

        // Store drag info in the view
        view.dragging = { slice, move: true };

        // Create drag image
        const dragImage = currentBlockElement.cloneNode(true);
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
    });

    dragButton.addEventListener('dragend', () => {
        editor.view.dragging = null;
    });

    // Update position on scroll
    const handleScroll = () => {
        if (currentBlockElement && gutterMenu.style.display !== 'none') {
            positionGutterMenu(currentBlockElement);
        }
    };

    editorElement.addEventListener('scroll', handleScroll);

    // Store references for cleanup
    const cleanup = () => {
        editorElement.removeEventListener('mousemove', handleMouseMove, true);
        document.removeEventListener('mousemove', handleDocumentMouseMove, true);
        editorElement.removeEventListener('scroll', handleScroll);
        if (gutterMenu && gutterMenu.parentElement) {
            gutterMenu.remove();
        }
        if (floatingMenu && floatingMenu.parentElement) {
            floatingMenu.remove();
        }
    };

    // Return cleanup function
    return cleanup;
}

/**
 * Handle drop events for block dragging
 * Only handles drops if we're dragging blocks internally
 */
export function handleDrop(view, event, slice, moved) {
    // Only handle drops if we're dragging blocks internally
    if (!moved) {
        return false; // Let default handler deal with external drops
    }

    const coords = view.posAtCoords({
        left: event.clientX,
        top: event.clientY
    });

    if (!coords || coords.pos === null || coords.pos === undefined) {
        return false;
    }

    const pos = coords.pos;
    const $pos = view.state.doc.resolve(pos);

    // Check if we're inside a paragraph (not at block boundaries)
    const parent = $pos.parent;
    const isParagraph = parent.type.name === 'paragraph';
    const isAtParagraphStart = isParagraph && $pos.parentOffset === 0;
    const isAtParagraphEnd = isParagraph && $pos.parentOffset === parent.content.size;
    const isInsideParagraph = isParagraph && !isAtParagraphStart && !isAtParagraphEnd;

    // We want to allow drops only:
    // 1. At depth 1 (between top-level blocks) - this is the document level
    // 2. At the start or end of a paragraph (block boundaries)
    // 3. At document start/end

    let targetPos = pos;

    // Always ensure we're inserting between blocks (not inside them)
    // This prevents drops inside paragraphs and ensures clean insertion
    if (isInsideParagraph) {
        // Prevent drop inside paragraph - find nearest block boundary
        const paragraphStart = $pos.start($pos.depth);
        const paragraphEnd = $pos.end($pos.depth);

        // Find which boundary is closer, then get position between blocks
        const isCloserToStart = Math.abs(pos - paragraphStart) < Math.abs(pos - paragraphEnd);

        if (isCloserToStart) {
            // Insert before paragraph - get position at depth 1
            targetPos = paragraphStart > 0 ? paragraphStart : 0;
        } else {
            // Insert after paragraph - get position after the paragraph block
            targetPos = paragraphEnd;
        }
    } else {
        // At a block boundary - ensure we're inserting between blocks
        // If at start of block, insert before it; if at end, insert after
        if (isAtParagraphStart && $pos.depth > 1) {
            // At start of nested block - insert before it
            targetPos = $pos.start($pos.depth);
        } else if (isAtParagraphEnd && $pos.depth > 1) {
            // At end of nested block - insert after it
            targetPos = $pos.after($pos.depth);
        } else if ($pos.depth === 1) {
            // At depth 1 - we're between blocks, use position as is
            // But ensure we're at a valid insertion point
            if ($pos.parentOffset === 0 && pos > 0) {
                // At start of block - insert before it
                targetPos = pos;
            } else {
                // At end or middle - use position as is
                targetPos = pos;
            }
        } else {
            // Use position as is if it's valid
            targetPos = pos;
        }
    }

    // Ensure targetPos is valid
    if (targetPos < 0) targetPos = 0;
    if (targetPos > view.state.doc.content.size) targetPos = view.state.doc.content.size;

    // Perform the drop
    const tr = view.state.tr;
    const { from, to } = view.state.selection;

    // Delete the dragged content if it's a move
    if (moved && from !== undefined && to !== undefined) {
        // Only delete if we're not dropping on the same position
        if (targetPos < from || targetPos > to) {
            const deleteSize = to - from;
            const adjustedPos = targetPos > to ? targetPos - deleteSize : targetPos;

            tr.delete(from, to);
            // Use replaceRange to ensure clean insertion at block boundary
            tr.replace(adjustedPos, adjustedPos, slice);
        } else {
            // Dropping on same position, just cancel
            return true;
        }
    } else {
        tr.replace(targetPos, targetPos, slice);
    }

    view.dispatch(tr);
    return true; // Handled
}

/**
 * Handle dragover events for block dragging
 * Prevents drops inside paragraphs (only allows at block boundaries)
 */
export function handleDragOver(view, event) {
    // Only handle internal drags
    if (!view.dragging || !view.dragging.slice) {
        return false;
    }

    const coords = view.posAtCoords({
        left: event.clientX,
        top: event.clientY
    });

    if (!coords || coords.pos === null || coords.pos === undefined) {
        return false;
    }

    const pos = coords.pos;
    const $pos = view.state.doc.resolve(pos);
    const parent = $pos.parent;

    // Check if we're inside a paragraph (not at boundaries)
    if (parent.type.name === 'paragraph') {
        const isAtStart = $pos.parentOffset === 0;
        const isAtEnd = $pos.parentOffset === parent.content.size;

        // If we're inside the paragraph (not at start or end), prevent drop
        if (!isAtStart && !isAtEnd) {
            event.dataTransfer.dropEffect = 'none';
            return true; // Prevent drop inside paragraph
        }
    }

    return false;
}
