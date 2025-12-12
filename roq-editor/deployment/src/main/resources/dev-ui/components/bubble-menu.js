/**
 * Bubble menu component for TipTap editor
 * Appears when text is selected
 */

import { showPrompt } from './prompt-dialog.js';

export function renderBubbleMenu() {
    return `
        <div class="tiptap-menu">
            <button class="tiptap-menu-button" data-command="bold" title="Bold">B</button>
            <button class="tiptap-menu-button" data-command="italic" title="Italic">I</button>
            <div class="tiptap-menu-separator"></div>
            <button class="tiptap-menu-button" data-command="link" title="Link">Link</button>
            <button class="tiptap-menu-button" data-command="image" title="Image">Image</button>
            <div class="tiptap-menu-separator" data-show-on-list></div>
            <button class="tiptap-menu-button" data-command="bulletList" data-show-on-list title="Bullet List">•</button>
            <button class="tiptap-menu-button" data-command="orderedList" data-show-on-list title="Ordered List">1.</button>
        </div>
    `;
}

export function attachBubbleMenuListeners(container, editor) {
    if (!container || !editor) return;
    
    container.addEventListener('click', (e) => {
        const button = e.target.closest('.tiptap-menu-button');
        if (!button || !editor) return;
        
        const command = button.dataset.command;
        
        if (command === 'bold') {
            editor.chain().focus().toggleBold().run();
        } else if (command === 'italic') {
            editor.chain().focus().toggleItalic().run();
        } else if (command === 'link') {
            showPrompt('Enter URL:', '').then(url => {
                if (url) {
                    editor.chain().focus().setLink({ href: url }).run();
                }
            });
        } else if (command === 'image') {
            showPrompt('Enter image URL:', '').then(url => {
                if (url) {
                    editor.chain().focus().setImage({ src: url }).run();
                }
            });
        } else if (command === 'bulletList') {
            editor.chain().focus().toggleBulletList().run();
        } else if (command === 'orderedList') {
            editor.chain().focus().toggleOrderedList().run();
        }
    });
}

function isCommandActive(editor, command) {
    if (!editor) return false;
    
    switch (command) {
        case 'bold':
            return editor.isActive('bold');
        case 'italic':
            return editor.isActive('italic');
        case 'bulletList':
            return editor.isActive('bulletList');
        case 'orderedList':
            return editor.isActive('orderedList');
        default:
            return false;
    }
}

export function updateBubbleMenu(container, editor) {
    if (!container || !editor) return;
    
    // Update button states based on editor state
    const buttons = container.querySelectorAll('.tiptap-menu-button');
    buttons.forEach(button => {
        const command = button.dataset.command;
        if (command) {
            const isActive = isCommandActive(editor, command);
            button.classList.toggle('is-active', isActive);
        }
    });
    
    // Show/hide list buttons based on whether we're in a list
    const isInList = editor.isActive('bulletList') || editor.isActive('orderedList');
    const listElements = container.querySelectorAll('[data-show-on-list]');
    listElements.forEach(el => {
        el.style.display = isInList ? '' : 'none';
    });
}

