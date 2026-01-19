import '@qomponent/qui-code-block';
import '@vaadin/button';
import '@vaadin/icon';
import { LitElement, css, html } from 'lit';
import { BubbleMenu, ConfCodeBlockLowlight, ContextProvider, DragHandle, Editor, Image, Link, Markdown, StarterKit, Table, TableCell, TableHeader, TableRow } from '../bundle.js';
import {combineFrontmatter, parseAndFormatDate, parseFrontmatter} from '../utils/frontmatter.js';
import { editorContext } from './editor-context.js';
import './bubble-menu.js';
import './floating-menu.js';
import './table-menu.js';
import './frontmatter-panel.js';
import './gutter-menu.js';
import './preview-panel.js';
import './toolbar.js';
import { RawBlock } from './raw-block.js';
import { SlashCommand } from './slash-command.js';
import { hljsTheme } from '../hljs-theme.js';

export class RoqVisualEditor extends LitElement {

    static properties = {
        content: { type: String },
        markup: { type: String },
        fileExtension: { type: String },
        filePath: { type: String },
        previewUrl: { type: String },
        date: { type: String },
        dateFormat: { type: String },
        loading: { type: Boolean },
        saving: { type: Boolean },
        _editedContent: { state: true },
        _isDirty: { state: true },
        _frontmatter: { state: true },
        _bodyContent: { state: true },
        _originalContent: { state: true },
        _originalDate: { state: true },
        _activeTab: { state: true }
    };

    static styles = [
        hljsTheme,
        css`
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
            background: var(--lumo-base-color);
        }
        .tiptap-editor {
            flex: 1;
            width: 100%;
            height: 100%;
            min-height: 0;
            box-sizing: border-box;
            overflow-y: auto;
            position: relative;
            cursor: text;
        }
        .tiptap-editor .tiptap {
            padding-left: 50px;
        }
        .tiptap-editor:focus {
            outline: none;
        }
        .tiptap-editor:disabled {
            opacity: 0.6;
            cursor: not-allowed;
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
        .editor-main[hidden] {
            visibility: hidden;
            height: 0;
            overflow: hidden;
            flex: 0;
        }
        .code-panel {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .code-panel[hidden] {
            display: none;
        }
        .code-panel qui-themed-code-block {
            flex: 1;
            min-height: 0;
            overflow: auto;
            cursor: text;
        }
        .preview-container {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .preview-container[hidden] {
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
        
        /* Raw Block Styles */
        .tiptap-editor [data-raw] {
            border: 1px solid var(--lumo-primary-color-50pct);
            border-radius: var(--lumo-border-radius-m);
            margin: 1em 0;
            background: var(--lumo-primary-color-10pct);
        }
        .tiptap-editor .raw-block-label {
            background: var(--lumo-primary-color);
            color: var(--lumo-primary-contrast-color);
            padding: 0.25em 0.75em;
            font-size: var(--lumo-font-size-xs);
            font-weight: 600;
            border-radius: var(--lumo-border-radius-m) var(--lumo-border-radius-m) 0 0;
            user-select: none;
        }
        .tiptap-editor .raw-block-content {
            padding: 0.75em 1em;
            font-family: var(--lumo-font-family-monospace);
            font-size: 0.9em;
            line-height: 1.6;
            white-space: pre-wrap;
            outline: none;
            word-break: break-word;
        }
        .tiptap-editor a {
            color: var(--lumo-primary-text-color);
            text-decoration-color: var(--lumo-primary-color-50pct);
            text-underline-offset: 2px;
            transition: color 0.15s ease, text-decoration-color 0.15s ease;
        }
        .tiptap-editor a:hover {
            color: var(--lumo-primary-color);
            text-decoration-color: var(--lumo-primary-color);
        }
        span.suggestion.is-empty::after {
            content: attr(data-decoration-content);
        }
        span.suggestion {
            background: var(--lumo-primary-color-10pct);
            border-radius: var(--lumo-border-radius-m);
            outline: 5.5px solid var(--lumo-primary-color-10pct);
        }
    `];

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
        this._originalDate = '';
        this._isInitializing = false;
        this._activeTab = 'editor';

        this._provider = new ContextProvider(this, {
            context: editorContext,
            initialValue: { editor: null, editorElement: null }
        });

        this._handleKeyDown = this._handleKeyDown.bind(this);

        // Non-reactive storage for code block content to avoid cursor jumping
        this._codeBlockContent = '';
        // Track if code block content needs to be set (on tab switch or file load)
        this._needsCodeBlockContentUpdate = true;
    }

    firstUpdated() {
        // Initialize editor after first render if content is available
        this._tryInitializeEditor();

        window.addEventListener('keydown', this._handleKeyDown, true);

        // Set initial code block content if starting on code tab
        if (this._activeTab === 'code' && this._needsCodeBlockContentUpdate) {
            requestAnimationFrame(() => this._updateCodeBlockContent());
        }
    }

    _updateCodeBlockContent() {
        const codeBlock = this.shadowRoot.querySelector('#code-editor');
        if (codeBlock) {
            const content = this._codeBlockContent || this._editedContent || this._bodyContent;
            codeBlock.content = content;
            // Also set value in case the component uses that for its internal state
            if (codeBlock.value !== undefined) {
                codeBlock.value = content;
            }
            this._needsCodeBlockContentUpdate = false;
        }
    }

    _handleKeyDown(e) {
        // Check for Mod+S (Ctrl+S on Windows/Linux, Cmd+S on Mac)
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            this._save();
            return false;
        }
    }

    updated(changedProperties) {
        // Try to initialize editor if it doesn't exist yet and content is available
        if (!this._editor) {
            this._tryInitializeEditor();
        }

        if (this.markup === 'asciidoc') {
            this.content = "Error: AsciiDoc is not supported in Visual Editor";
            return;
        }

        if (changedProperties.has('content') && this._hasContent() && !this._isError()) {
            const parsed = parseFrontmatter(this.content);
            this._frontmatter = parsed.frontmatter;
            this._bodyContent = parsed.body;
            this._originalContent = this.content;
            this._originalDate = this.date || '';

            // Initialize code block content
            this._codeBlockContent = parsed.body;
            this._needsCodeBlockContentUpdate = true;

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

        if (this._needsCodeBlockContentUpdate) {
            this._updateCodeBlockContent();
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        window.removeEventListener('keydown', this._handleKeyDown, true);
        if (this._editor && !this._editor.isDestroyed) {
            this._editor.destroy();
            this._editor = null;
        }
    }

    _isMarkdownFile() {
        return this.markup === 'markdown';
    }

    _isHtml() {
        return this.markup === 'html';
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
            RawBlock,
            Image,
            Link.configure({
                openOnClick: false,
            }),
            DragHandle.configure({
                render: () => this.shadowRoot.getElementById('gutter-menu'),
                dragHandleWidth: 24,
            }),
            SlashCommand,
        ];

        if (bubbleMenuContainer) {
            extensions.push(BubbleMenu.configure({
                element: bubbleMenuContainer,
                shouldShow: ({ state: { selection }, editor }) => {
                    // Don't show when in a table - use table menu instead
                    if (editor.isActive('table')) return false;
                    return !selection.empty;
                },
                tippyOptions: {
                    showOnCreate: false,
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

        // Reset selection to end of document to prevent initial full-document selection
        // This is needed because contentType: 'markdown' can cause the entire doc to be selected
        this._editor.commands.setTextSelection(this._editor.state.doc.content.size);

        // Focus the editor
        this._editor.commands.focus();

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
                            .activeTab="${this._activeTab}"
                            .showEditorTab=${true}
                            @tab-changed="${this._onTabChanged}">
                        </qwc-toolbar>
                        <div class="preview-container" ?hidden="${this._activeTab !== 'preview'}">
                            <qwc-preview-panel .previewUrl="${this._getPreviewUrl()}"></qwc-preview-panel>
                        </div>
                        <div class="editor-panel" ?hidden="${this._activeTab === 'preview'}">
                            <div class="editor-layout">
                              <div class="editor-main" ?hidden="${this._activeTab !== 'editor'}">
                                <div class="tiptap-editor">
                                  <qwc-bubble-menu style="visibility: hidden; position: absolute;"></qwc-bubble-menu>
                                  <qwc-table-menu></qwc-table-menu>
                                  <qwc-gutter-menu id="gutter-menu" style="visibility: hidden;">
                                    <qwc-floating-menu></qwc-floating-menu>
                                  </qwc-gutter-menu>
                                </div>
                              </div>
                                <div class="code-panel" ?hidden="${this._activeTab !== 'code'}">
                                    <qui-themed-code-block id="code-editor" showlinenumbers editable
                                        mode="this.fileExtension"
                                        @value-changed="${this._onCodeBlockChange}">
                                    </qui-themed-code-block>
                                </div>
                                <qwc-frontmatter-panel
                                    .frontmatter="${this._frontmatter}"
                                    .date="${this.date}"
                                    .dateFormat="${this.dateFormat}"
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
        // Update dirty state - check both content and date
        const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
        const currentDate = panel ? panel.getDate() : this.date;
        const combinedContent = combineFrontmatter(this._frontmatter, this._editedContent);
        const contentChanged = combinedContent !== this._originalContent;
        const dateChanged = currentDate !== this._originalDate;
        this._isDirty = contentChanged || dateChanged;
    }

    _onTabChanged(e) {
        const newTab = e.detail.tab;
        const previousTab = this._activeTab;

        if (newTab === "previewNewTab") {
            window.open(this._getPreviewUrl(), '_blank');
            return;
        }

        // If switching from code, sync the code block content to _editedContent
        if (previousTab === 'code') {
            const codeBlock = this.shadowRoot.querySelector('qui-themed-code-block');
            if (codeBlock) {
                this._editedContent = codeBlock.value || codeBlock.content || this._codeBlockContent;
            }
        }

        // If switching from code to editor, update tiptap with code changes
        if (previousTab === 'code' && newTab === 'editor' && this._editor && !this._editor.isDestroyed) {
            this._isInitializing = true;
            const isMarkdown = this._isMarkdownFile();
            this._editor.commands.setContent(this._editedContent, { contentType: isMarkdown ? 'markdown' : 'html' });
            requestAnimationFrame(() => {
                this._isInitializing = false;
            });
        }

        // If switching from editor to code, ensure _editedContent is up to date
        if (previousTab === 'editor' && newTab === 'code' && this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            this._editedContent = isMarkdown ? this._editor.getMarkdown() : this._editor.getHTML();
        }

        // Update code block content for next render
        this._codeBlockContent = this._editedContent;

        if (newTab === 'code') {
            this._needsCodeBlockContentUpdate = true;
        }

        this._activeTab = newTab;
        this.requestUpdate();
    }

    _onCodeBlockChange(e) {
        const codeBlock = e.target;
        const newContent = codeBlock.value || codeBlock.content || '';

        // Store in non-reactive variable to avoid cursor jumping
        this._codeBlockContent = newContent;

        // Check dirty state by comparing combined content with original
        const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
        const currentFrontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
        const combinedContent = combineFrontmatter(currentFrontmatter, newContent);
        const newIsDirty = combinedContent !== this._originalContent;

        // Only trigger re-render if dirty state actually changed
        if (this._isDirty !== newIsDirty) {
            this._isDirty = newIsDirty;
        }
    }

    _save() {
        if (!this._isDirty || this.saving) {
            return;
        }

        // Get body content based on active tab
        let bodyContent;
        if (this._activeTab === 'code') {
            // Read directly from code block
            const codeBlock = this.shadowRoot.querySelector('qui-themed-code-block');
            bodyContent = codeBlock ? (codeBlock.value || codeBlock.content || this._codeBlockContent) : this._codeBlockContent;
        } else if (this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            if (isMarkdown) {
                bodyContent = this._editor.getMarkdown();
            } else {
                bodyContent = this._editor.getHTML();
            }
        } else {
            bodyContent = this._editedContent;
        }

        // Get Frontmatter and date from panel
        const panel = this.shadowRoot.querySelector('qwc-frontmatter-panel');
        const frontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
        const fieldTypes = panel ? panel.getFieldTypes() : {};
        const date = parseAndFormatDate(panel ? panel.getDate() : this.date);
        const title = frontmatter.title || '';

        // Combine Frontmatter and body content
        const contentToSave = combineFrontmatter(frontmatter, bodyContent, fieldTypes);

        this.saving = true;
        this.dispatchEvent(new CustomEvent('save-content', {
            bubbles: true,
            composed: true,
            detail: {
                content: contentToSave,
                filePath: this.filePath,
                date: date,
                title: title
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
            this._codeBlockContent = parsed.body;
            this._needsCodeBlockContentUpdate = true;

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

            this._updateCodeBlockContent();
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
        const currentDate = panel ? panel.getDate() : this.date;

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
        this._originalDate = currentDate;
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
        return this.previewUrl;
    }

}

customElements.define('qwc-visual-editor', RoqVisualEditor);

