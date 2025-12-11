/**
 * Floating menu component for TipTap editor
 * Appears when the cursor is in an empty text block
 */

export function renderFloatingMenu() {
    return `
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

export function attachFloatingMenuListeners(container, editor) {
    if (!container || !editor) return;
    
    container.addEventListener('click', (e) => {
        const button = e.target.closest('.tiptap-menu-button');
        if (!button || !editor) return;
        
        const command = button.dataset.command;
        const level = button.dataset.level;
        
        if (command === 'heading' && level) {
            editor.chain().focus().toggleHeading({ level: parseInt(level) }).run();
        } else if (command === 'bulletList') {
            editor.chain().focus().toggleBulletList().run();
        } else if (command === 'orderedList') {
            editor.chain().focus().toggleOrderedList().run();
        } else if (command === 'codeBlock') {
            editor.chain().focus().toggleCodeBlock().run();
        }
    });
}

