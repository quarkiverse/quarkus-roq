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

    const gutterMenu = root.getElementById('gutter-menu');
    // Create floating menu component for add button
    const floatingMenu = document.createElement('qwc-floating-menu');
    floatingMenu.editor = editor;
    floatingMenu.editorElement = editorElement;
    gutterMenu.floatingMenu = floatingMenu;
    editorWrapper.appendChild(floatingMenu);
}
