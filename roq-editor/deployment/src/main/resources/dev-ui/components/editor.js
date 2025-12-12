import '@vaadin/button';
import '@vaadin/icon';
import { LitElement, css, html } from 'lit';
import { BubbleMenu, Editor, FloatingMenu, Image, Link, Markdown, StarterKit } from '../bundle.js';
import { combineFrontmatter, parseFrontmatter } from '../utils/frontmatter.js';
import { attachBubbleMenuListeners, renderBubbleMenu, updateBubbleMenu } from './bubble-menu.js';
import { attachFloatingMenuListeners, renderFloatingMenu } from './floating-menu.js';
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
                        : (this._isHtml() ? this._editor.getHTML() : this._editor.getText());

                    if (currentContent !== this._bodyContent) {
                        this._isInitializing = true;
                        this._setContent();
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
        return PostUtils.extractFileType({ path: this.filePath }) === 'Markdown';
    }

    _isHtml() {
        return PostUtils.extractFileType({ path: this.filePath }) === 'HTML';
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
                        index === content.length - 1 ? null : { type: 'hardBreak' } ])).flat().filter(Boolean),
                    })
                }),
            });
        }
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

        floatingMenuContainer.innerHTML = renderFloatingMenu();
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
            Link.configure({
                openOnClick: false,
            }),
        ];

        // Add FloatingMenu if container exists
        if (floatingMenuContainer) {
            extensions.push(FloatingMenu.configure({
                element: floatingMenuContainer,
                shouldShow: ({ _, state }) => {
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

        this._isInitializing = true;

        this._editor = new Editor({
            element: editorElement,
            extensions: extensions,
            content: initialContent,
            contentType: isMarkdown ? 'markdown' : (isHtml ? 'html' : 'text'),
            editable: !this.saving,
            onUpdate: ({ editor }) => {
                if (this._isInitializing) {
                    return;
                }

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
                }
            },
            onSelectionUpdate: ({ editor }) => {
                const bubbleMenuContainer = this.shadowRoot.querySelector('.bubble-menu');
                if (bubbleMenuContainer) {
                    updateBubbleMenu(bubbleMenuContainer, editor);
                }
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
            attachFloatingMenuListeners(floatingMenuContainer, this._editor);
        }

        if (bubbleMenuContainer) {
            attachBubbleMenuListeners(bubbleMenuContainer, this._editor);
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
                    this._setContent();
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

