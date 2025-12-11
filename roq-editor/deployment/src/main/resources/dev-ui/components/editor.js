import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import { Editor, StarterKit, Markdown, Image, Link, FloatingMenu, BubbleMenu } from '../bundle.js';
import { parseFrontmatter, combineFrontmatter, hasFrontmatter } from '../utils/frontmatter.js';
import './frontmatter-panel.js';

export class FileContentEditor extends LitElement {
    
    static properties = {
        content: { type: String },
        filePath: { type: String },
        sourceFilePath: { type: String },
        loading: { type: Boolean },
        saving: { type: Boolean },
        _editedContent: { state: true },
        _isDirty: { state: true },
        _frontmatter: { state: true },
        _bodyContent: { state: true },
        _originalContent: { state: true }
    };

    static styles = css`
        :host {
            display: block;
            height: 100%;
        }
        .editor-container {
            display: flex;
            flex-direction: column;
            height: 100%;
        }
        .editor-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: var(--lumo-space-m);
            border-bottom: 1px solid var(--lumo-contrast-20pct);
            background: var(--lumo-base-color);
        }
        .editor-title {
            font-size: var(--lumo-font-size-l);
            font-weight: 600;
            margin: 0;
            color: var(--lumo-body-text-color);
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
        }
        .dirty-indicator {
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-primary-color);
            font-weight: normal;
        }
        .header-actions {
            display: flex;
            gap: var(--lumo-space-s);
            align-items: center;
        }
        .editor-content {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            padding: var(--lumo-space-m);
            background: var(--lumo-base-color);
        }
        .editor-wrapper {
            flex: 1;
            display: flex;
            flex-direction: column;
            min-height: 0;
            overflow: hidden;
        }
        .tiptap-editor {
            flex: 1;
            font-family: 'Courier New', monospace;
            font-size: var(--lumo-font-size-s);
            width: 100%;
            min-height: 0;
            padding: var(--lumo-space-m);
            border-radius: var(--lumo-border-radius-m);
            border: 1px solid var(--lumo-contrast-20pct);
            background: var(--lumo-contrast-5pct);
            color: var(--lumo-body-text-color);
            box-sizing: border-box;
            overflow-y: auto;
        }
        .tiptap-editor:focus {
            outline: none;
            border-color: var(--lumo-primary-color);
            box-shadow: 0 0 0 2px var(--lumo-primary-color-10pct);
        }
        .tiptap-editor:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }
        .tiptap-editor p {
            margin: 1em 0;
        }
        .tiptap-editor p.is-editor-empty:first-child::before {
            content: attr(data-placeholder);
            float: left;
            color: var(--lumo-contrast-60pct);
            pointer-events: none;
            height: 0;
        }
        .loading {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: var(--lumo-space-xl);
            color: var(--lumo-contrast-60pct);
        }
        .error {
            padding: var(--lumo-space-m);
            background: var(--lumo-error-color-10pct);
            color: var(--lumo-error-text-color);
            border-radius: var(--lumo-border-radius-m);
            border: 1px solid var(--lumo-error-color-50pct);
        }
        .read-only-content {
            font-family: 'Courier New', monospace;
            font-size: var(--lumo-font-size-s);
            white-space: pre-wrap;
            word-wrap: break-word;
            background: var(--lumo-contrast-5pct);
            padding: var(--lumo-space-m);
            border-radius: var(--lumo-border-radius-m);
            border: 1px solid var(--lumo-contrast-20pct);
        }
        .editor-layout {
            display: flex;
            flex-direction: row;
            flex: 1;
            overflow: hidden;
            gap: 0;
        }
        .editor-main {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            min-width: 0;
        }
        .frontmatter-panel {
            width: 350px;
            min-width: 300px;
            max-width: 500px;
            flex-shrink: 0;
        }
        .floating-menu,
        .bubble-menu {
            /* TipTap/Tippy extensions handle visibility automatically via shouldShow callbacks */
            /* These containers are attachment points for Tippy - visibility is managed by the extensions */
        }
        .tiptap-menu {
            display: flex;
            gap: var(--lumo-space-xs);
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-xs);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
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
        pre {
            background-color: var(--lumo-shade-30pct);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            padding: 1em;
            margin: 1em 0;
            overflow-x: auto;
            font-family: var(--lumo-font-family-monospace);
            font-size: 0.9em;
            line-height: 1.5;

        code {
            background: transparent;
            border: none;
            padding: 0;
            font-size: inherit;
            color: inherit;
            }
        }
        pre code {
            background: transparent;
            border: none;
            padding: 0;
        }
    `;

    constructor() {
        super();
        this._editedContent = '';
        this._isDirty = false;
        this.saving = false;
        this._editor = null;
        this._editorElement = null;
        this._frontmatter = {};
        this._bodyContent = '';
        this._originalContent = '';
        this._isInitializing = false;
    }

    firstUpdated() {
        // Initialize editor after first render if content is available
        this._tryInitializeEditor();
    }

    updated(changedProperties) {
        // Try to initialize editor if it doesn't exist yet and content is available
        if (!this._editor) {
            this._tryInitializeEditor();
        }

        if (changedProperties.has('content') && this.content !== null && this.content !== undefined) {
            const isError = this.content.startsWith('Error') || this.content.startsWith('File not found');
            if (!isError) {
                const parsed = parseFrontmatter(this.content);
                this._frontmatter = parsed.frontmatter;
                this._bodyContent = parsed.body;
                this._originalContent = this.content;
                
                const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
                if (panel) {
                    panel.frontmatter = this._frontmatter;
                    if (parsed.fieldTypes) {
                        panel._fieldTypes = { ...parsed.fieldTypes };
                    }
                }
                
                if (this._editor && !this._editor.isDestroyed) {
                    const isMarkdown = this._isMarkdownFile();
                    // Get current content based on file type
                    const currentContent = isMarkdown
                        ? this._editor.getMarkdown()
                        : this._editor.getHTML();
                    
                    if (currentContent !== this._bodyContent) {
                        this._isInitializing = true;
                        if (isMarkdown) {
                            this._editor.commands.setContent(this._bodyContent, { contentType: 'markdown' });
                        } else {
                            this._editor.commands.setContent(this._bodyContent);
                        }
                        this._editedContent = this._bodyContent;
                        this._isDirty = false;
                        setTimeout(() => {
                            this._isInitializing = false;
                        }, 100);
                    }
                } else {
                    this._editedContent = this._bodyContent;
                    this._isDirty = false;
                }
            }
        }
        if (changedProperties.has('saving') && this._editor && !this._editor.isDestroyed) {
            this._editor.setEditable(!this.saving);
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._editor && !this._editor.isDestroyed) {
            this._editor.destroy();
            this._editor = null;
        }
    }

    _isMarkdownFile() {
        // Use sourceFilePath if available (actual file path), otherwise fall back to filePath
        const pathToCheck = this.sourceFilePath || this.filePath;
        if (!pathToCheck) {
            return false;
        }
        const markdownExtensions = ['.md', '.markdown', '.mdown', '.mkd', '.mkdn'];
        const lowerPath = pathToCheck.toLowerCase();
        return markdownExtensions.some(ext => lowerPath.endsWith(ext));
    }

    _tryInitializeEditor() {
        // Don't initialize if loading, no content, or error state
        if (this.loading || !this.content || this.content === null || this.content === undefined) {
            return;
        }

        const isError = this.content.startsWith('Error') || this.content.startsWith('File not found');
        if (isError) {
            return;
        }

        const editorElement = this.shadowRoot.querySelector('.tiptap-editor');
        if (!editorElement || this._editor) {
            return;
        }

        const floatingMenuContainer = this.shadowRoot.querySelector('.floating-menu');
        const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');
        
        // If containers don't exist yet, wait for next render cycle
        if (!floatingMenuContainer || !bubbleMenuContainer) {
            requestAnimationFrame(() => {
                this._tryInitializeEditor();
            });
            return;
        }

        floatingMenuContainer.innerHTML = this._renderFloatingMenu();
        bubbleMenuContainer.innerHTML = this._renderBubbleMenu();

        this._editorElement = editorElement;
        const initialContent = this._editedContent || this._bodyContent || '';
        const isMarkdown = this._isMarkdownFile();
        
        const baseExtensions = isMarkdown 
            ? [StarterKit, Markdown.configure({ 
                html: false, // Don't parse HTML in markdown
                transformPastedText: true,
                transformCopiedText: true
            })]
            : [StarterKit];
        
        // Build extensions array
        const extensions = [
            ...baseExtensions,
            Image,
            Link.configure({
                openOnClick: false,
            }),
        ];
        
        // Add FloatingMenu if container exists
        if (floatingMenuContainer) {
            extensions.push(FloatingMenu.configure({
                element: floatingMenuContainer,
                shouldShow: ({ view, state }) => {
                    const { selection } = state;
                    const { $anchor, empty } = selection;
                    if (!empty || selection.from !== selection.to) {
                        return false; // Don't show if text is selected (BubbleMenu should show instead)
                    }
                    if (!$anchor || !$anchor.parent) {
                        return false;
                    }
                    const isRootDepth = $anchor.depth === 1;
                    const isEmptyTextBlock = $anchor.parent.isTextblock && !$anchor.parent.textContent;
                    return isRootDepth && isEmptyTextBlock;
                },
                tippyOptions: {
                    placement: 'top-start',
                    offset: [0, 8],
                },
            }));
        }
        
        if (bubbleMenuContainer) {
            extensions.push(BubbleMenu.configure({
                element: bubbleMenuContainer,
                shouldShow: ({ view, state }) => {
                    const { selection } = state;
                    if (selection.empty || selection.from === selection.to) {
                        return false;
                    }
                    return true;
                },
                tippyOptions: {
                    placement: 'top',
                    offset: [0, 8],
                },
            }));
        }

        this._isInitializing = true;

        this._editor = new Editor({
            element: editorElement,
            extensions: extensions,
            content: initialContent,
            contentType: isMarkdown ? 'markdown' : 'html',
            editable: !this.saving,
            onUpdate: ({ editor }) => {
                if (this._isInitializing) {
                    return;
                }
                
                // For markdown files, get markdown text; for others, get HTML
                if (isMarkdown) {
                    this._editedContent = editor.getMarkdown();
                } else {
                    this._editedContent = editor.getHTML();
                }
                // Check if dirty by comparing combined Frontmatter + body with original
                const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
                const currentFrontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
                const combinedContent = combineFrontmatter(currentFrontmatter, this._editedContent);
                this._isDirty = combinedContent !== this._originalContent;
                
                // Update menu states
                this._updateMenus();
            },
            editorProps: {
                attributes: {
                    'data-placeholder': 'Edit file content...',
                    class: 'tiptap-editor'
                }
            },
            onSelectionUpdate: () => {
                // Update menu visibility based on selection
                this._updateMenus();
            },
        });
        
        setTimeout(() => {
            this._attachMenuListeners();
        }, 0);

        setTimeout(() => {
            this._isInitializing = false;
        }, 100);
    }

    _attachMenuListeners() {
        if (!this._editor) return;
        
        const floatingMenuContainer = this.shadowRoot.querySelector('.floating-menu');
        const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');
        
        if (floatingMenuContainer) {
            this._attachFloatingMenuListeners(floatingMenuContainer);
        }
        
        if (bubbleMenuContainer) {
            this._attachBubbleMenuListeners(bubbleMenuContainer);
        }
    }


    _updateMenus() {
        if (!this._editor) return;
        
        const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');
        if (bubbleMenuContainer) {
            // Update button states based on editor state
            const buttons = bubbleMenuContainer.querySelectorAll('.tiptap-menu-button');
            buttons.forEach(button => {
                const command = button.dataset.command;
                if (command) {
                    const isActive = this._isCommandActive(command);
                    button.classList.toggle('is-active', isActive);
                }
            });
            
            // Show/hide list buttons based on whether we're in a list
            const isInList = this._editor.isActive('bulletList') || this._editor.isActive('orderedList');
            const listElements = bubbleMenuContainer.querySelectorAll('[data-show-on-list]');
            listElements.forEach(el => {
                el.style.display = isInList ? '' : 'none';
            });
        }
    }

    _isCommandActive(command) {
        if (!this._editor) return false;
        
        switch (command) {
            case 'bold':
                return this._editor.isActive('bold');
            case 'italic':
                return this._editor.isActive('italic');
            case 'bulletList':
                return this._editor.isActive('bulletList');
            case 'orderedList':
                return this._editor.isActive('orderedList');
            default:
                return false;
        }
    }

    _renderFloatingMenu() {
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

    _renderBubbleMenu() {
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

    _attachFloatingMenuListeners(container) {
        container.addEventListener('click', (e) => {
            const button = e.target.closest('.tiptap-menu-button');
            if (!button || !this._editor) return;
            
            const command = button.dataset.command;
            const level = button.dataset.level;
            
            if (command === 'heading' && level) {
                this._editor.chain().focus().toggleHeading({ level: parseInt(level) }).run();
            } else if (command === 'bulletList') {
                this._editor.chain().focus().toggleBulletList().run();
            } else if (command === 'orderedList') {
                this._editor.chain().focus().toggleOrderedList().run();
            } else if (command === 'codeBlock') {
                this._editor.chain().focus().toggleCodeBlock().run();
            }
        });
    }

    _attachBubbleMenuListeners(container) {
        container.addEventListener('click', (e) => {
            const button = e.target.closest('.tiptap-menu-button');
            if (!button || !this._editor) return;
            
            const command = button.dataset.command;
            
            if (command === 'bold') {
                this._editor.chain().focus().toggleBold().run();
            } else if (command === 'italic') {
                this._editor.chain().focus().toggleItalic().run();
            } else if (command === 'link') {
                const url = window.prompt('Enter URL:');
                if (url) {
                    this._editor.chain().focus().setLink({ href: url }).run();
                }
            } else if (command === 'image') {
                const url = window.prompt('Enter image URL:');
                if (url) {
                    this._editor.chain().focus().setImage({ src: url }).run();
                }
            } else if (command === 'bulletList') {
                this._editor.chain().focus().toggleBulletList().run();
            } else if (command === 'orderedList') {
                this._editor.chain().focus().toggleOrderedList().run();
            }
        });
    }

    render() {
        if (this.loading) {
            return html`
                <div class="editor-container">
                    <div class="editor-header">
                        <h3 class="editor-title">Loading file content...</h3>
                        <vaadin-button theme="tertiary" @click="${this._close}">
                            <vaadin-icon icon="font-awesome-solid:xmark" slot="prefix"></vaadin-icon>
                            Close
                        </vaadin-button>
                    </div>
                    <div class="loading">
                        <span>Loading...</span>
                    </div>
                </div>
            `;
        }

        if (this.content === null || this.content === undefined) {
            return html``;
        }

        const isError = this.content.startsWith('Error') || this.content.startsWith('File not found');

        return html`
            <div class="editor-container">
                <div class="editor-header">
                    <h3 class="editor-title">
                        ${this.filePath || 'File Editor'}
                        ${this._isDirty ? html`<span class="dirty-indicator">(unsaved changes)</span>` : ''}
                    </h3>
                    <div class="header-actions">
                        ${!isError ? html`
                            <vaadin-button 
                                theme="primary" 
                                ?disabled="${!this._isDirty || this.saving}"
                                @click="${this._save}">
                                <vaadin-icon icon="font-awesome-solid:floppy-disk" slot="prefix"></vaadin-icon>
                                ${this.saving ? 'Saving...' : 'Save'}
                            </vaadin-button>
                            <vaadin-button 
                                theme="tertiary" 
                                ?disabled="${!this._isDirty || this.saving}"
                                @click="${this._cancel}">
                                Cancel
                            </vaadin-button>
                        ` : ''}
                        <vaadin-button theme="tertiary" @click="${this._close}">
                            <vaadin-icon icon="font-awesome-solid:xmark" slot="prefix"></vaadin-icon>
                            Close
                        </vaadin-button>
                    </div>
                </div>
                <div class="editor-content">
                    ${isError 
                        ? html`<div class="error">${this.content}</div>`
                        : html`
                            <div class="editor-layout">
                                <div class="editor-main">
                                    <div class="editor-wrapper">
                                        <div class="floating-menu"></div>
                                        <div class="bubble-menu"></div>
                                        <div class="tiptap-editor"></div>
                                    </div>
                                </div>
                                <div class="frontmatter-panel">
                                    <qwc-frontmatter-panel
                                        .frontmatter="${this._frontmatter}"
                                        @frontmatter-changed="${this._onFrontmatterChanged}">
                                    </qwc-frontmatter-panel>
                                </div>
                            </div>
                        `
                    }
                </div>
            </div>
        `;
    }

    _onFrontmatterChanged(e) {
        this._frontmatter = e.detail.frontmatter;
        // Update dirty state
        const combinedContent = combineFrontmatter(this._frontmatter, this._editedContent);
        this._isDirty = combinedContent !== this._originalContent;
    }

    _save() {
        if (!this._isDirty || this.saving) {
            return;
        }

        // Get body content from editor if available, otherwise use _editedContent
        let bodyContent;
        if (this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            if (isMarkdown) {
                bodyContent = this._editor.getMarkdown();
            } else {
                bodyContent = this._editor.getHTML();
            }
        } else {
            bodyContent = this._editedContent;
        }

        // Get Frontmatter from panel
        const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
        const frontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
        const fieldTypes = panel ? panel.getFieldTypes() : {};

        // Combine Frontmatter and body content
        const contentToSave = combineFrontmatter(frontmatter, bodyContent, fieldTypes);

        this.saving = true;
        this.dispatchEvent(new CustomEvent('save-content', {
            bubbles: true,
            composed: true,
            detail: {
                content: contentToSave,
                filePath: this.filePath
            }
        }));
    }

    _cancel() {
        if (this._isDirty) {
            if (confirm('You have unsaved changes. Are you sure you want to discard them?')) {
                // Reset to original content
                const parsed = parseFrontmatter(this._originalContent);
                this._frontmatter = parsed.frontmatter;
                this._bodyContent = parsed.body;
                
                // Update Frontmatter panel
                const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
                if (panel) {
                    panel.frontmatter = this._frontmatter;
                }
                
                if (this._editor && !this._editor.isDestroyed) {
                    // Set initialization flag to prevent false dirty state when resetting content
                    this._isInitializing = true;
                    const isMarkdown = this._isMarkdownFile();
                    if (isMarkdown) {
                        this._editor.commands.setContent(this._bodyContent, { contentType: 'markdown' });
                    } else {
                        this._editor.commands.setContent(this._bodyContent);
                    }
                    // Clear initialization flag after content reset
                    setTimeout(() => {
                        this._isInitializing = false;
                    }, 100);
                }
                this._editedContent = this._bodyContent;
                this._isDirty = false;
            }
        }
    }

    _close() {
        if (this._isDirty) {
            if (!confirm('You have unsaved changes. Are you sure you want to close?')) {
                return;
            }
        }
        this.dispatchEvent(new CustomEvent('close-viewer', {
            bubbles: true,
            composed: true
        }));
    }

    // Method to be called from parent when save is successful
    markSaved() {
        // Get the saved content (which includes Frontmatter)
        const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
        const frontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
        
        let bodyContent;
        if (this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            if (isMarkdown) {
                bodyContent = this._editor.getMarkdown();
            } else {
                bodyContent = this._editor.getHTML();
            }
        } else {
            bodyContent = this._editedContent;
        }
        
        const savedContent = combineFrontmatter(frontmatter, bodyContent);
        this.content = savedContent;
        this._originalContent = savedContent;
        this._bodyContent = bodyContent;
        this._frontmatter = frontmatter;
        this._editedContent = bodyContent;
        this._isDirty = false;
        this.saving = false;
    }

    // Method to be called from parent when save fails
    markSaveError() {
        this.saving = false;
    }
}

customElements.define('qwc-file-content-viewer', FileContentEditor);

