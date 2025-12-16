/**
 * Gutter menu component for TipTap editor
 * Appears in the left gutter on hover for each paragraph/block
 * Provides "+" button for creating new blocks and drag handle for reordering
 */

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

    // Render floating menu content
    floatingMenu.innerHTML = `
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
