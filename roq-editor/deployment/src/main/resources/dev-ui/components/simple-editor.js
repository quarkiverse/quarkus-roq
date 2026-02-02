import '@qomponent/qui-code-block';
import '@vaadin/button';
import '@vaadin/icon';
import {css, html} from 'lit';
import {BaseEditor} from './base-editor.js';
import {hljsTheme} from '../hljs-theme.js';
import { showConfirm } from './confirm-dialog.js';

export class RoqSimpleEditor extends BaseEditor {
    static properties = {
        ...BaseEditor.properties,
    };

    static styles = [
        // inherit base styles
        BaseEditor.styles,
        // component-specific
        hljsTheme,
        css`
      .editor-layout {
        display: flex;
        flex-direction: row;
        flex: 1;
        overflow: hidden;
        gap: 0;
      }

      .editor-panel {
        display: flex;
        flex-direction: column;
        flex: 1;
        overflow: hidden;
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
    `,
    ];

    constructor() {
        super();
        this._isInitializing = false;
        this._activeTab = 'code'; // Simple editor defaults to code tab
    }

    // No "visual editor" tab in the simple editor
    get showEditorTab() {
        return false;
    }

    /** Keep code block in sync when content prop changes */
    updated(changed) {
        if (changed.has('content') && this._hasContent() && !this._isError()) {
            this._storedContent = this.content;
            this._editorValue = this.content;
            this._isDirty = false;
            this._updateCodeEditorContent();
        }
    }

    _updateCodeEditorContent() {
        const codeBlock = this.shadowRoot?.querySelector('#code-editor');
        if (codeBlock) {
            const content = this._editorValue ?? '';
            codeBlock.content = content;
            if (codeBlock.value !== undefined) {
                codeBlock.value = content;
            }
        }
    }

    _renderEditorPanels() {
        // Only render when not in preview
        const hidePanel = this._activeTab === 'preview';
        return html`
      <div class="editor-panel" ?hidden="${hidePanel}">
        <div class="editor-layout">
          <div class="code-panel" ?hidden="${this._activeTab !== 'code'}">
            <qui-themed-code-block
              id="code-editor"
              showlinenumbers
              editable
              mode="${this.page.extension}"
              @value-changed="${this._onCodeBlockChange}"
            >
            </qui-themed-code-block>
          </div>
        </div>
      </div>
    `;
    }

    _onCodeBlockChange(e) {
        const codeBlock = e.target;
        this._editorValue = codeBlock.value ?? codeBlock.content ?? '';
        this._isDirty = this._editorValue !== this._storedContent;

    }

    _save() {
        if (!this._isDirty || this.saving) {
            return;
        }
        this.saving = true;
        this.dispatchEvent(
            new CustomEvent('save-content', {
                bubbles: true,
                composed: true,
                detail: {
                    content: this._editorValue,
                    path: this.page.path,
                },
            }),
        );
    }

    async _cancel() {
        if (!this._isDirty) return;
        const confirmed = await showConfirm(
            'You have unsaved changes!',
            { title: 'Do you really want to close?', confirmText: 'Discard Changes', theme: 'error' }
        );
        if (confirmed) {
            this._editorValue = this._storedContent;
            this._isDirty = false;
            this._updateCodeEditorContent();
        }
    }
}

customElements.define('qwc-simple-editor', RoqSimpleEditor);
