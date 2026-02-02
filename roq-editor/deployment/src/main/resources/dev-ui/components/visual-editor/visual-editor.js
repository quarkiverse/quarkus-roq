import '@qomponent/qui-code-block';
import '@vaadin/button';
import '@vaadin/icon';
import {css, html} from 'lit';
import {
    BubbleMenu,
    Placeholder,
    ConfCodeBlockLowlight,
    ContextProvider,
    DragHandle,
    Editor,
    Link,
    Markdown,
    StarterKit,
    Table,
    TableCell,
    TableHeader,
    TableRow,
} from '../../bundle.js';
import {combineFrontmatter, parseAndFormatDate, parseFrontmatter} from '../../utils/frontmatter.js';
import {editorContext} from './editor-context.js';
import './bubble-menu.js';
import './table-menu.js';
import './frontmatter-panel.js';
import './gutter-menu.js';
import '../preview-panel.js';
import '../toolbar.js';
import {RoqImage} from './extensions/image.js';
import {RawBlock} from './extensions/raw-block.js';
import {SlashCommand} from './extensions/slash-command.js';
import {hljsTheme} from '../../hljs-theme.js';
import {BaseEditor} from '../base-editor.js';
import { showConfirm } from '../confirm-dialog.js';

export class RoqVisualEditor extends BaseEditor {
    static properties = {
        ...BaseEditor.properties,
        dateFormat: {type: String},

        // local state
        _frontmatter: {state: true},
        _storedFrontmatter: {state: true},
        _storedBodyContent: {state: true},
    };

    static styles = [
        BaseEditor.styles,
        hljsTheme,
        css`
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
                padding-left: 60px;
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
            
            .tiptap p.is-empty::before {
                color: var(--lumo-contrast-60pct);
                content: attr(data-placeholder);
                float: left;
                height: 0;
                pointer-events: none;
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
            }

            pre code, .tiptap pre code {
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
            .tiptap.ProseMirror img {
                max-width: 100%;
                height: auto;
                display: block;
            }
            .tiptap.ProseMirror .tiptap-image-img {
                border-radius: .25rem;
            }            
        `,
    ];

    constructor() {
        super();
        this._editor = null;
        this._editorElement = null;
        this._frontmatter = {};
        this._storedBodyContent = '';
        this._isInitializing = false;

        this._activeTab = 'editor'; // Visual editor defaults to visual "editor"
        this._codeEditorValue = '';
        this._needsCodeBlockContentUpdate = true;

        this._provider = new ContextProvider(this, {
            context: editorContext,
            initialValue: {editor: null, editorElement: null},
        });
    }

    get showEditorTab() {
        return true;
    }

    /** ---- Lifecycle ---- */

    firstUpdated() {
        super.firstUpdated();
        this._tryInitializeEditor();

        // Initialize code block if tab is already "code"
        if (this._activeTab === 'code' && this._needsCodeBlockContentUpdate) {
            requestAnimationFrame(() => this._updateCodeBlockContent());
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._editor && !this._editor.isDestroyed) {
            this._editor.destroy();
            this._editor = null;
        }
    }

    updated(changed) {
        // AsciiDoc not supported
        if (this.page.markup === 'asciidoc') {
            this._error = 'Error: AsciiDoc is not supported in Visual Editor';
            return;
        }

        // Initialize editor if needed
        if (!this._editor) {
            this._tryInitializeEditor();
        }

        // Propagate content to editor/frontmatter
        if (changed.has('content') && this._hasContent() && !this._isError()) {
            const parsed = parseFrontmatter(this.content);
            this._frontmatter = parsed.frontmatter;
            this._storedBodyContent = parsed.body;
            this._storedContent = this.content;
            this._storedFrontmatter = this._frontmatter;

            // Seed code block side
            this._codeEditorValue = parsed.body;
            this._needsCodeBlockContentUpdate = true;

            const panel = this._getFrontmatterPanel();
            if (panel) {
                panel.frontmatter = this._frontmatter;
                if (parsed.fieldTypes) {
                    panel._fieldTypes = {...parsed.fieldTypes};
                }
            }

            if (this._editor && !this._editor.isDestroyed) {
                const isMarkdown = this._isMarkdownFile();
                const currentContent = isMarkdown ? this._editor.getMarkdown() : this._editor.getHTML();
                if (currentContent !== this._storedBodyContent) {
                    // Avoid dirty flag on initialization
                    this._isInitializing = true;
                    this._setContent();
                    this._editorValue = this._storedBodyContent;
                    this._isDirty = false;
                    requestAnimationFrame(() => {
                        this._isInitializing = false;
                    });
                }
            } else {
                this._editorValue = this._storedBodyContent;
                this._isDirty = false;
            }
        }

        if (changed.has('saving') && this._editor && !this._editor.isDestroyed) {
            this._editor.setEditable(!this.saving);
        }

        if (this._needsCodeBlockContentUpdate) {
            this._updateCodeBlockContent();
        }
    }

    /** ---- Helpers ---- */

    _isMarkdownFile() {
        return this.page.markup === 'markdown';
    }

    _setContent() {
        this._editor.commands.setContent(this._storedBodyContent, {
            contentType: this._isMarkdownFile() ? 'markdown' : 'html',
        });
    }

    _tryInitializeEditor() {
        if (this.loading || !this._hasContent() || this._isError()) return;

        const editorElement = this.shadowRoot?.querySelector('.tiptap-editor');
        if (!editorElement || this._editor) return;

        const bubbleMenuContainer = this.shadowRoot?.querySelector('qwc-bubble-menu');
        if (!bubbleMenuContainer) {
            requestAnimationFrame(() => this._tryInitializeEditor());
            return;
        }

        this._editorElement = editorElement;
        const initialContent = this._editorValue || this._storedBodyContent || '';
        const isMarkdown = this._isMarkdownFile();

        this._isInitializing = true;

        const baseExtensions = isMarkdown
            ? [
                StarterKit.configure({link: false, codeBlock: false}),
                Markdown.configure({
                    html: true,
                    transformPastedText: true,
                    transformCopiedText: true,
                    markedOptions: {gfm: true},
                }),
            ]
            : [StarterKit.configure({link: false})];

        const extensions = [
            ...baseExtensions,
            ...(isMarkdown ? [Table, TableRow, TableHeader, TableCell, ConfCodeBlockLowlight] : []),
            RawBlock,
            RoqImage.configure({
                    urlPrefix: this._getPreviewUrl
                }
            ),
            Link.configure({
                openOnClick: false,
            }),
            DragHandle.configure({
                render: () => this.shadowRoot.getElementById('gutter-menu'),
                dragHandleWidth: 24,
            }),
            SlashCommand,
            Placeholder.configure({
                placeholder: "Write some text, or type '/' for blocks & commands",
            }),
            BubbleMenu.configure({
                element: bubbleMenuContainer,
                shouldShow: ({state: {selection}, editor}) => {
                    if (editor.isActive('table')) return false;
                    return !selection.empty;
                },
                tippyOptions: {showOnCreate: false},
            }),
        ];

        this._editor = new Editor({
            element: editorElement,
            extensions,
            content: initialContent,
            contentType: isMarkdown ? 'markdown' : 'html',
            editable: !this.saving,
            onUpdate: ({editor}) => {
                let body = isMarkdown ? editor.getMarkdown() : editor.getHTML();
                this._editorValue = body;
                this._checkDirty(body, this._getFrontmatterPanel()?.getFrontmatter());
                this.requestUpdate();
            },
            editorProps: {
                attributes: {
                    'data-placeholder': 'Edit file content...',
                    class: 'tiptap-editor',
                },
            },
            onSelectionUpdate: () => this.requestUpdate(),
        });

        this._provider.setValue({editor: this._editor, editorElement: this._editorElement});
        // Move selection to end
        this._editor.commands.setTextSelection(this._editor.state.doc.content.size);
        // Focus
        this._editor.commands.focus();

        requestAnimationFrame(() => {
            setTimeout(() => {
                this._isInitializing = false;
            }, 200);
        });
    }

    _getFrontmatterPanel() {
        return this.shadowRoot?.querySelector('qwc-frontmatter-panel');
    }

    _checkDirty(body, frontmatter) {
        if (!this._isInitializing) {
            if (body !== this._storedBodyContent) {
                this._isDirty = true;
            } else if (frontmatter) {
                this._isDirty = JSON.stringify(frontmatter) !== JSON.stringify(this._storedFrontmatter);
            }
        }
    }

    _updateCodeBlockContent() {
        const codeBlock = this.shadowRoot?.querySelector('#code-editor');
        if (codeBlock) {
            const content = this._codeEditorValue || this._editorValue || this._storedBodyContent || '';
            codeBlock.content = content;
            if (codeBlock.value !== undefined) {
                codeBlock.value = content;
            }
            this._needsCodeBlockContentUpdate = false;
        }
    }

    /** ---- Base hooks for tab changes ---- */
    _beforeTabChange(previousTab, newTab) {
        // If switching from code, capture current codeblock text
        if (previousTab === 'code') {
            const codeBlock = this.shadowRoot?.querySelector('qui-themed-code-block');
            if (codeBlock) {
                this._editorValue = codeBlock.value ?? codeBlock.content ?? this._codeEditorValue;
            }
        }

        // Sync code->editor
        if (previousTab === 'code' && newTab === 'editor' && this._editor && !this._editor.isDestroyed) {
            this._isInitializing = true;
            const isMarkdown = this._isMarkdownFile();
            this._editor.commands.setContent(this._editorValue, {contentType: isMarkdown ? 'markdown' : 'html'});
            requestAnimationFrame(() => (this._isInitializing = false));
        }

        // Sync editor->code
        if (previousTab === 'editor' && newTab === 'code' && this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            this._editorValue = isMarkdown ? this._editor.getMarkdown() : this._editor.getHTML();
            this._codeEditorValue = this._editorValue;
            this._needsCodeBlockContentUpdate = true;
        }
    }

    /** ---- Render panels ---- */
    _renderEditorPanels() {
        const showEditor = this._activeTab === 'editor';
        const showCode = this._activeTab === 'code';
        const hideAll = this._activeTab === 'preview';

        return html`
          <div class="editor-panel" ?hidden="${hideAll}">
            <div class="editor-layout">
              <div class="editor-main" ?hidden="${!showEditor}">
                <div class="tiptap-editor">
                  <qwc-bubble-menu style="visibility: hidden; position: absolute;"></qwc-bubble-menu>
                  <qwc-table-menu></qwc-table-menu>
                  <qwc-gutter-menu id="gutter-menu" style="visibility: hidden;"></qwc-gutter-menu>
                </div>
              </div>

              <div class="code-panel" ?hidden="${!showCode}">
                <qui-themed-code-block
                  id="code-editor"
                  showlinenumbers
                  editable
                  mode="${this.fileExtension}"
                  @value-changed="${this._onCodeBlockChange}"
                >
                </qui-themed-code-block>
              </div>

              <qwc-frontmatter-panel
                .frontmatter="${this._frontmatter}"
                .date="${this.page.date}"
                .dateFormat="${this.dateFormat}"
                @frontmatter-changed="${this._onFrontmatterChanged}"
              >
              </qwc-frontmatter-panel>
            </div>
          </div>
        `;
    }

    /** ---- Events & actions ---- */

    _onFrontmatterChanged(e) {
        this._frontmatter = e.detail.frontmatter;
        this._checkDirty(this._getValueFromCurrentEditor(), e.detail.frontmatter);
    }

    _onCodeBlockChange(e) {
        const codeBlock = e.target;
        this._codeEditorValue = codeBlock.value ?? codeBlock.content ?? '';
        this._checkDirty(this._codeEditorValue, this._getFrontmatterPanel()?.getFrontmatter());
    }

    _save() {
        if (!this._isDirty || this.saving) return;

        let bodyContent = this._getValueFromCurrentEditor();

        const panel = this._getFrontmatterPanel();
        const frontmatter = panel ? panel.getFrontmatter() : this._frontmatter;
        const fieldTypes = panel ? panel.getFieldTypes() : {};
        const date = parseAndFormatDate(panel ? panel.getDate() : this.page.date);
        const title = frontmatter.title || '';

        const contentToSave = combineFrontmatter(frontmatter, bodyContent, fieldTypes);

        this.saving = true;
        this.dispatchEvent(
            new CustomEvent('save-content', {
                bubbles: true,
                composed: true,
                detail: {
                    content: contentToSave,
                    path: this.page.path,
                    date,
                    title,
                },
            }),
        );
    }

    _getValueFromCurrentEditor() {
        // Choose source for body content
        let bodyContent;
        if (this._activeTab === 'code') {
            const codeBlock = this.shadowRoot?.querySelector('qui-themed-code-block');
            return codeBlock ? codeBlock.value ?? codeBlock.content ?? this._codeEditorValue : this._codeEditorValue;
        } else if (this._editor && !this._editor.isDestroyed) {
            const isMarkdown = this._isMarkdownFile();
            return isMarkdown ? this._editor.getMarkdown() : this._editor.getHTML();
        } else {
            return this._editorValue;
        }
    }

    async _cancel() {
        if (!this._isDirty) return;

        const confirmed = await showConfirm(
            'You have unsaved changes!',
            { title: 'Do you really want to close?', confirmText: 'Discard Changes', theme: 'error' }
        );
        if (confirmed) {
            const parsed = parseFrontmatter(this._storedContent);
            this._frontmatter = parsed.frontmatter;
            this._storedFrontmatter = parsed.frontmatter;
            this._storedBodyContent = parsed.body;
            this._codeEditorValue = parsed.body;
            this._needsCodeBlockContentUpdate = true;

            const panel = this._getFrontmatterPanel();
            if (panel) {
                panel.frontmatter = this._frontmatter;
            }

            if (this._editor && !this._editor.isDestroyed) {
                this._isInitializing = true;
                this._setContent();
                requestAnimationFrame(() => {
                    this._isInitializing = false;
                });
            }

            this._editorValue = this._storedBodyContent;
            this._isDirty = false;

            this._updateCodeBlockContent();
        }
    }

    /** ---- Save lifecycle ---- */
    markSaved() {
        const panel = this._getFrontmatterPanel();
        const frontmatter = panel ? panel.getFrontmatter() : this._frontmatter;

        let bodyContent;
        if (this._editor && !this._editor.isDestroyed) {
            bodyContent = this._isMarkdownFile() ? this._editor.getMarkdown() : this._editor.getHTML();
        } else {
            bodyContent = this._editorValue;
        }

        const content = combineFrontmatter(frontmatter, bodyContent);
        this.content = content;
        this._storedContent = content;
        this._storedFrontmatter = frontmatter;
        this._storedBodyContent = bodyContent;
        this._frontmatter = frontmatter;
        this._editorValue = bodyContent;
        this._isDirty = false;
        this.saving = false;
    }
}

customElements.define('qwc-visual-editor', RoqVisualEditor);
