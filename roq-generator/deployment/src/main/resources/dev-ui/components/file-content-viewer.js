import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import { Editor, StarterKit, Markdown } from '../editor.js';

export class FileContentEditor extends LitElement {
    
    static properties = {
        content: { type: String },
        filePath: { type: String },
        sourceFilePath: { type: String },
        loading: { type: Boolean },
        saving: { type: Boolean },
        _editedContent: { state: true },
        _isDirty: { state: true }
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
            margin: 0;
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
    `;

    constructor() {
        super();
        this._editedContent = '';
        this._isDirty = false;
        this.saving = false;
        this._editor = null;
        this._editorElement = null;
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
                if (this._editor && !this._editor.isDestroyed) {
                    const isMarkdown = this._isMarkdownFile();
                    // Get current content based on file type
                    const currentContent = isMarkdown
                        ? this._editor.getMarkdown()
                        : this._editor.getHTML();
                    
                    // Only update if content actually changed to avoid unnecessary updates
                    if (currentContent !== this.content) {
                        if (isMarkdown) {
                            this._editor.commands.setContent(this.content, { contentType: 'markdown' });
                        } else {
                            this._editor.commands.setContent(this.content);
                        }
                        this._editedContent = this.content;
                        this._isDirty = false;
                    }
                } else {
                    // Editor not initialized yet, store content for initialization
                    this._editedContent = this.content;
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

        this._editorElement = editorElement;
        const initialContent = this._editedContent || this.content || '';
        const isMarkdown = this._isMarkdownFile();
        
        // Configure extensions based on file type
        const extensions = isMarkdown 
            ? [StarterKit, Markdown.configure({ 
                html: false, // Don't parse HTML in markdown
                transformPastedText: true,
                transformCopiedText: true
            })]
            : [StarterKit];

        this._editor = new Editor({
            element: editorElement,
            extensions: extensions,
            content: initialContent,
            contentType: isMarkdown ? 'markdown' : 'html',
            editable: !this.saving,
            onUpdate: ({ editor }) => {
                // For markdown files, get markdown text; for others, get HTML
                if (isMarkdown) {
                    this._editedContent = editor.getMarkdown();
                } else {
                    this._editedContent = editor.getHTML();
                }
                this._isDirty = this._editedContent !== this.content;
            },
            editorProps: {
                attributes: {
                    'data-placeholder': 'Edit file content...',
                    class: 'tiptap-editor'
                }
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
                            <div class="editor-wrapper">
                                <div class="tiptap-editor"></div>
                            </div>
                        `
                    }
                </div>
            </div>
        `;
    }

    _save() {
        if (!this._isDirty || this.saving) {
            return;
        }

        // Get content from editor if available, otherwise use _editedContent
        let contentToSave;
        if (this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            if (isMarkdown) {
                contentToSave = this._editor.getMarkdown();
            } else {
                contentToSave = this._editor.getHTML();
            }
        } else {
            contentToSave = this._editedContent;
        }

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
                if (this._editor && !this._editor.isDestroyed) {
                    const isMarkdown = this._isMarkdownFile();
                    if (isMarkdown) {
                        this._editor.commands.setContent(this.content, { contentType: 'markdown' });
                    } else {
                        this._editor.commands.setContent(this.content);
                    }
                }
                this._editedContent = this.content;
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
        let savedContent;
        if (this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            if (isMarkdown) {
                savedContent = this._editor.getMarkdown();
            } else {
                savedContent = this._editor.getHTML();
            }
        } else {
            savedContent = this._editedContent;
        }
        this.content = savedContent;
        this._editedContent = savedContent;
        this._isDirty = false;
        this.saving = false;
    }

    // Method to be called from parent when save fails
    markSaveError() {
        this.saving = false;
    }
}

customElements.define('qwc-file-content-viewer', FileContentEditor);

