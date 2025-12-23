import '@qomponent/qui-code-block';
import '@vaadin/button';
import '@vaadin/icon';
import { LitElement, css, html } from 'lit';
import { BubbleMenu, ConfCodeBlockLowlight, ContextProvider, DragHandle, Editor, Image, Link, Markdown, StarterKit, Table, TableCell, TableHeader, TableRow } from '../bundle.js';
import { combineFrontmatter, parseFrontmatter } from '../utils/frontmatter.js';
import { editorContext } from './editor-context.js';
import './bubble-menu.js';
import './floating-menu.js';
import './frontmatter-panel.js';
import './gutter-menu.js';
import './toolbar.js';
import { PostUtils } from './post-utils.js';
import { QuteBlock } from './qute-block.js';

export class RoqEditor extends LitElement {

    static properties = {
        content: { type: String },
        filePath: { type: String },
        loading: { type: Boolean },
        saving: { type: Boolean },
        _editedContent: { state: true },
        _isDirty: { state: true },
        _frontmatter: { state: true },
        _bodyContent: { state: true },
        _originalContent: { state: true },
        _activeTab: { state: true }
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
        .tiptap-editor .tiptap {
            padding-left: 50px;
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
        .tiptap-editor table {
            border-collapse: collapse;
            margin: 1em 0;
            width: 100%;
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            overflow: hidden;
        }
        .tiptap-editor table td,
        .tiptap-editor table th {
            border: 1px solid var(--lumo-contrast-20pct);
            padding: var(--lumo-space-xs) var(--lumo-space-s);
            text-align: left;
        }
        .tiptap-editor table th {
            background: var(--lumo-contrast-10pct);
            font-weight: 600;
        }
        .tiptap-editor table tr:nth-child(even) {
            background: var(--lumo-contrast-5pct);
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
        .editor-panel {
            display: flex;
            flex-direction: column;
            flex: 1;
            overflow: hidden;
        }
        .editor-panel[hidden] {
            display: none;
        }
        .tiptap > pre {
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
        
        /* Qute Template Block Styles */
        .tiptap-editor [data-qute] {
            border: 1px solid var(--lumo-primary-color-50pct);
            border-radius: var(--lumo-border-radius-m);
            margin: 1em 0;
            background: var(--lumo-primary-color-10pct);
        }
        .tiptap-editor .qute-template-label {
            background: var(--lumo-primary-color);
            color: var(--lumo-primary-contrast-color);
            padding: 0.25em 0.75em;
            font-size: var(--lumo-font-size-xs);
            font-weight: 600;
            border-radius: var(--lumo-border-radius-m) var(--lumo-border-radius-m) 0 0;
            user-select: none;
        }
        .tiptap-editor .qute-template-content {
            padding: 0.75em 1em;
            font-family: var(--lumo-font-family-monospace);
            font-size: 0.9em;
            line-height: 1.6;
            white-space: pre-wrap;
            outline: none;
            word-break: break-word;
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
        this._activeTab = 'editor';

        this._provider = new ContextProvider(this, {
            context: editorContext,
            initialValue: { editor: null, editorElement: null }
        });
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
                    : this._editor.getHTML();

                if (currentContent !== this._bodyContent) {
                    // Set flag to prevent dirty state during content update
                    this._isInitializing = true;
                    this._setContent();
                    this._editedContent = this._bodyContent;
                    this._isDirty = false;
                    // Clear flag after content is set
                    requestAnimationFrame(() => {
                        this._isInitializing = false;
                    });
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
        this._editor.commands.setContent(this._bodyContent, { contentType: this._isMarkdownFile() ? 'markdown' : 'html' });
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

        const bubbleMenuContainer = this.shadowRoot.querySelector('qwc-bubble-menu');

        // If container doesn't exist yet, wait for next render cycle
        if (!bubbleMenuContainer) {
            requestAnimationFrame(() => {
                this._tryInitializeEditor();
            });
            return;
        }

        this._editorElement = editorElement;
        const initialContent = this._editedContent || this._bodyContent || '';
        const isMarkdown = this._isMarkdownFile();

        // Set flag to prevent dirty state during initialization
        this._isInitializing = true;

        const baseExtensions = isMarkdown
            ? [StarterKit.configure({ link: false, codeBlock: false }), Markdown.configure({
                html: true,
                transformPastedText: true,
                transformCopiedText: true,
                markedOptions: {
                    gfm: true
                }
            })]
            : [StarterKit.configure({ link: false })];

        // Build extensions array
        const extensions = [
            ...baseExtensions,
            ...(isMarkdown ? [Table, TableRow, TableHeader, TableCell, ConfCodeBlockLowlight] : []),
            QuteBlock,
            Image,
            Link.configure({
                openOnClick: false,
            }),
            DragHandle.configure({
                render: () => this.shadowRoot.getElementById('gutter-menu'),
                dragHandleWidth: 24,
            }),
        ];

        if (bubbleMenuContainer) {
            extensions.push(BubbleMenu.configure({
                element: bubbleMenuContainer,
                shouldShow: ({ view, state }) => {
                    // Don't show bubble menu when dragging
                    if (view.dragging) {
                        return false;
                    }
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
            contentType: isMarkdown ? 'markdown' : 'html',
            editable: !this.saving,
            onUpdate: ({ editor }) => {
                if (isMarkdown) {
                    this._editedContent = editor.getMarkdown();
                } else {
                    this._editedContent = editor.getHTML();
                }
                // Skip dirty check during initialization
                if (!this._isInitializing) {
                    // Check if dirty by comparing combined Frontmatter + body with original
                    const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
                    const currentFrontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
                    const combinedContent = combineFrontmatter(currentFrontmatter, this._editedContent);
                    this._isDirty = combinedContent !== this._originalContent;
                }

                // Menu states are updated automatically via context consumer
                // Request update to refresh undo/redo button states
                this.requestUpdate();
            },
            editorProps: {
                attributes: {
                    'data-placeholder': 'Edit file content...',
                    class: 'tiptap-editor'
                },
            },
            onSelectionUpdate: ({ editor }) => {
                // Menu states are updated automatically via context consumer
                // Request update to refresh undo/redo button states
                this.requestUpdate();
            },
        });

        this._provider.setValue({ editor: this._editor, editorElement: this._editorElement });

        requestAnimationFrame(() => {
            // Delay gutter menu initialization to ensure editor is fully ready
            setTimeout(() => {
                this._isInitializing = false;
            }, 200);
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
                        <qwc-toolbar 
                            previewUrl="${this._getPreviewUrl()}" 
                            .activeTab="${this._activeTab}"
                            @tab-changed="${this._onTabChanged}">
                        </qwc-toolbar>
                        <div class="editor-panel" ?hidden="${this._activeTab === 'preview'}">
                            <qwc-gutter-menu id="gutter-menu">
                                <qwc-floating-menu></qwc-floating-menu>
                            </qwc-gutter-menu>
                            <div class="editor-layout">
                                ${this._isHtml() || this._isMarkdownFile() ? html`<div class="editor-main">
                                    <div class="editor-wrapper">
                                        <qwc-bubble-menu></qwc-bubble-menu>
                                        <div class="tiptap-editor"></div>
                                    </div>
                                </div>` : html`
                        <qui-code-block showlinenumbers editable
                            mode="adoc"
                            .content=${this._bodyContent}
                            @value-changed="${this._onCodeBlockChange}">
                        </qui-code-block>`}
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

    _onTabChanged(e) {
        this._activeTab = e.detail.tab;
        this.requestUpdate();
    }

    _onCodeBlockChange(e) {
        const codeBlock = e.target;
        const newContent = codeBlock.value || codeBlock.content || '';

        this._editedContent = newContent;
        this._isDirty = newContent !== this._originalContent;
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
                // Set flag to prevent dirty state during content reset
                this._isInitializing = true;
                this._setContent();
                requestAnimationFrame(() => {
                    this._isInitializing = false;
                });
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

    _getPreviewUrl() {
        if (!this.filePath || !this._frontmatter) {
            return null;
        }
        return PostUtils.generatePreviewUrl(this._frontmatter, this.filePath);
    }

}

customElements.define('qwc-editor', RoqEditor);

