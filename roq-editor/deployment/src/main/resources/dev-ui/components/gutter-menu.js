/**
 * Gutter menu initialization and management
 * Creates and manages the gutter menu Lit component
 */

import './gutter-menu-component.js';
import './floating-menu.js';

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
    const existingMenu = editorWrapper.querySelector('qwc-gutter-menu');
    if (existingMenu) {
        return; // Already initialized
    }

    // Create gutter menu component
    const gutterMenu = document.createElement('qwc-gutter-menu');
    gutterMenu.editor = editor;
    gutterMenu.editorElement = editorElement;
    gutterMenu.editorWrapper = editorWrapper;
    editorWrapper.appendChild(gutterMenu);

    // Create floating menu component for add button
    const floatingMenu = document.createElement('qwc-floating-menu');
    floatingMenu.editor = editor;
    floatingMenu.editorElement = editorElement;
    gutterMenu.floatingMenu = floatingMenu;
    editorWrapper.appendChild(floatingMenu);

    // Keep floating menu visible when hovering over it
    floatingMenu.addEventListener('menu-enter', () => {
        gutterMenu.keepVisible();
    });

    // Event handlers
    const handleMouseMove = (e) => {
        const rect = editorElement.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        if (x >= 0 && x <= rect.width && y >= 0 && y <= rect.height) {
            const blockElement = gutterMenu.findBlockElementFromEvent(e);
            if (blockElement) {
                gutterMenu.showForBlock(blockElement);
            }
        } else {
            gutterMenu.scheduleHide();
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

    // Update position on scroll
    const handleScroll = () => {
        gutterMenu.updatePositionOnScroll();
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
