import '@vaadin/button';
import '@vaadin/icon';
import { LitElement, css, html } from 'lit';
import { BubbleMenu, Editor, DragHandle, Image, Markdown, StarterKit } from '../bundle.js';
import { combineFrontmatter, parseFrontmatter } from '../utils/frontmatter.js';
import { attachBubbleMenuListeners, renderBubbleMenu, updateBubbleMenu } from './bubble-menu.js';
import { initializeGutterMenu } from './gutter-menu.js';
import './frontmatter-panel.js';
import { PostUtils } from './post-utils.js';

export class FileContentEditor extends LitElement {

    static properties = {
        content: { type: String },
        filePath: { type: String },
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
            position: relative;
        }
        .tiptap-editor {
            flex: 1;
            width: 100%;
            min-height: 0;
            padding: var(--lumo-space-m);
            border-radius: var(--lumo-border-radius-m);
            border: 1px solid var(--lumo-contrast-20pct);
            background: var(--lumo-contrast-5pct);
            box-sizing: border-box;
            overflow-y: auto;
            position: relative;
        }
        .tiptap-editor > div {
            padding-left: 50px;
        }
        .gutter-menu {
            position: absolute;
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
            z-index: 10;
            pointer-events: auto;
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
        .gutter-floating-menu {
            position: absolute;
            z-index: 20;
            pointer-events: auto;
        }
        .gutter-floating-menu .tiptap-menu {
            margin: 0;
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
        .tiptap-editor blockquote {
            margin: 1em 0;
            padding-left: 1em;
            border-left: 2px solid var(--lumo-contrast-20pct);
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

        if (changedProperties.has('content') && this._hasContent() && !this._isError()) {
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
                    : (this._isHtml() ? this._editor.getHTML() : this._editor.getText());

                if (currentContent !== this._bodyContent) {
                    this._setContent();
                    this._editedContent = this._bodyContent;
                    this._isDirty = false;
                }
            } else {
                this._editedContent = this._bodyContent;
                this._isDirty = false;
            }
        }
        if (changedProperties.has('saving') && this._editor && !this._editor.isDestroyed) {
            this._editor.setEditable(!this.saving);
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._gutterMenuCleanup) {
            this._gutterMenuCleanup();
            this._gutterMenuCleanup = null;
        }
        if (this._editor && !this._editor.isDestroyed) {
            this._editor.destroy();
            this._editor = null;
        }
    }

    _isMarkdownFile() {
        return PostUtils.extractFileType({ path: this.filePath }) === 'Markdown';
    }

    _isHtml() {
        return PostUtils.extractFileType({ path: this.filePath }) === 'HTML';
    }

    _hasContent() {
        return this.content != null && this.content !== undefined;
    }

    _isError() {
        return this._hasContent() && (this.content.startsWith('Error') || this.content.startsWith('File not found'));
    }

    _setContent() {
        const isMarkdown = this._isMarkdownFile();
        const isHtml = this._isHtml();
        if (isMarkdown || isHtml) {
            this._editor.commands.setContent(this._bodyContent, { contentType: isMarkdown ? 'markdown' : 'html' });
        } else {
            this._editor.commands.setContent({
                type: 'doc',
                content: this._bodyContent.split('\n\n').map(block => {
                    const content = block.split('\n');
                    return ({
                        type: 'paragraph',
                        content: content.filter(line => line.trim() !== '').map((line, index) => ([{
                            type: 'text',
                            text: line,
                        },
                        index === content.length - 1 ? null : { type: 'hardBreak' }])).flat().filter(Boolean),
                    })
                }),
            });
        }
    }

    _tryInitializeEditor() {
        // Don't initialize if loading, no content, or error state
        if (this.loading || !this._hasContent() || this._isError()) {
            return;
        }

        const editorElement = this.shadowRoot.querySelector('.tiptap-editor');
        if (!editorElement || this._editor) {
            return;
        }

        const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');

        // If container doesn't exist yet, wait for next render cycle
        if (!bubbleMenuContainer) {
            requestAnimationFrame(() => {
                this._tryInitializeEditor();
            });
            return;
        }

        bubbleMenuContainer.innerHTML = renderBubbleMenu();

        this._editorElement = editorElement;
        const initialContent = this._editedContent || this._bodyContent || '';
        const isMarkdown = this._isMarkdownFile();
        const isHtml = this._isHtml();

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
            DragHandle.configure({
                dragHandleWidth: 24,
            }),
        ];

        if (bubbleMenuContainer) {
            extensions.push(BubbleMenu.configure({
                element: bubbleMenuContainer,
                shouldShow: ({ _, state }) => {
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

        this._editor = new Editor({
            element: editorElement,
            extensions: extensions,
            content: initialContent,
            contentType: isMarkdown ? 'markdown' : (isHtml ? 'html' : 'text'),
            editable: !this.saving,
            onUpdate: ({ editor }) => {
                if (isMarkdown) {
                    this._editedContent = editor.getMarkdown();
                } else if (isHtml) {
                    this._editedContent = editor.getHTML();
                } else {
                    this._editedContent = editor.getText();
                }
                // Check if dirty by comparing combined Frontmatter + body with original
                const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
                const currentFrontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
                const combinedContent = combineFrontmatter(currentFrontmatter, this._editedContent);
                this._isDirty = combinedContent !== this._originalContent;

                // Update menu states
                const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');
                if (bubbleMenuContainer) {
                    updateBubbleMenu(bubbleMenuContainer, editor);
                }
            },
            editorProps: {
                attributes: {
                    'data-placeholder': 'Edit file content...',
                    class: 'tiptap-editor'
                },
                handleDrop: (view, event, slice, moved) => {
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
                },
                handleDOMEvents: {
                    dragover: (view, event) => {
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
                }
            },
            onSelectionUpdate: ({ editor }) => {
                const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');
                if (bubbleMenuContainer) {
                    updateBubbleMenu(bubbleMenuContainer, editor);
                }
            },
        });

        requestAnimationFrame(() => {
            this._attachMenuListeners();
            // Delay gutter menu initialization to ensure editor is fully ready
            setTimeout(() => {
                if (!this._gutterMenuCleanup) {
                    this._initializeGutterMenu();
                }
            }, 200);
        });
    }

    _attachMenuListeners() {
        const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');

        attachBubbleMenuListeners(bubbleMenuContainer, this._editor);
    }

    _initializeGutterMenu() {
        if (this._editor && this._editorElement && !this._gutterMenuCleanup) {
            try {
                this._gutterMenuCleanup = initializeGutterMenu(this._editorElement, this._editor);
            } catch (e) {
                console.error('Error initializing gutter menu:', e);
            }
        }
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

        if (!this._hasContent()) {
            return html``;
        }

        const isError = this._isError();

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
                                        <div class="bubble-menu"></div>
                                        <div class="tiptap-editor"></div>
                                    </div>
                                </div>
                                <qwc-frontmatter-panel
                                    .frontmatter="${this._frontmatter}"
                                    @frontmatter-changed="${this._onFrontmatterChanged}">
                                </qwc-frontmatter-panel>
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
            } else if (this._isHtml()) {
                bodyContent = this._editor.getHTML();
            } else {
                bodyContent = this._editor.getText();
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
        if (!this._isDirty) return;
        
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
                this._setContent();
            }
            this._editedContent = this._bodyContent;
            this._isDirty = false;
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

