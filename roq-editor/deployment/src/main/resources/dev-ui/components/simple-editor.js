import '@qomponent/qui-code-block';
import '@vaadin/button';
import '@vaadin/icon';
import {css, html, LitElement} from 'lit';
import './preview-panel.js';
import './toolbar.js';
import {hljsTheme} from '../hljs-theme.js';

export class RoqSimpleEditor extends LitElement {

    static properties = {
        content: {type: String},
        fileExtension: {type: String},
        filePath: {type: String},
        previewUrl: {type: String},
        loading: {type: Boolean},
        saving: {type: Boolean},
        _editedContent: {state: true},
        _isDirty: {state: true},
        _originalContent: {state: true},
        _activeTab: {state: true}
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

            .editor-panel {
                display: flex;
                flex-direction: column;
                flex: 1;
                overflow: hidden;
            }

            .editor-panel[hidden] {
                display: none;
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
        `
    ];


    constructor() {
        super();
        this._editedContent = '';
        this._isDirty = false;
        this.saving = false;
        this._originalContent = '';
        this._isInitializing = false;
        this._activeTab = 'code';

        this._handleKeyDown = this._handleKeyDown.bind(this);
    }

    firstUpdated() {
        window.addEventListener('keydown', this._handleKeyDown, true);
    }

    _updateCodeEditorContent() {
        const codeBlock = this.shadowRoot.querySelector('#code-editor');
        if (codeBlock) {
            const content = this._editedContent;
            codeBlock.content = content;
            // Also set value in case the component uses that for its internal state
            if (codeBlock.value !== undefined) {
                codeBlock.value = content;
            }
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
        if (changedProperties.has('content') && this._hasContent() && !this._isError()) {
            this._originalContent = this.content;
            this._editedContent = this.content;
            this._isDirty = false;
            this._updateCodeEditorContent();
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        window.removeEventListener('keydown', this._handleKeyDown, true);
    }

    _hasContent() {
        return this.content != null;
    }

    _isError() {
        return this._hasContent() && (this.content.startsWith('Error') || this.content.startsWith('File not found'));
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
                ? html`
                  <div class="error">${this.content}</div>`
                : html`
                  <qwc-toolbar
                    .activeTab="${this._activeTab}"
                    .showEditorTab=${false}
                    @tab-changed="${this._onTabChanged}">
                  </qwc-toolbar>
                  <div class="preview-container" ?hidden="${this._activeTab !== 'preview'}">
                    <qwc-preview-panel .previewUrl="${this._getPreviewUrl()}"></qwc-preview-panel>
                  </div>
                  <div class="editor-panel" ?hidden="${this._activeTab === 'preview'}">
                    <div class="editor-layout">
                      <div class="code-panel" ?hidden="${this._activeTab !== 'code'}">
                        <qui-themed-code-block id="code-editor" showlinenumbers editable
                                               mode="${this.fileExtension}"
                                               @value-changed="${this._onCodeBlockChange}">
                        </qui-themed-code-block>
                      </div>
                    </div>
                  </div>
                `
              }
            </div>
          </div>
        `;
    }

    _onTabChanged(e) {
        const newTab = e.detail.tab;
        const previousTab = this._activeTab;

        if (newTab === "previewNewTab") {
            window.open(this._getPreviewUrl(), '_blank');
            return;
        }

        this._activeTab = newTab;
        this.requestUpdate();
    }

    _onCodeBlockChange(e) {
        const codeBlock = e.target;
        const newContent = codeBlock.value || codeBlock.content || '';

        // Store in non-reactive variable to avoid cursor jumping
        this._editedContent = newContent;
        const newIsDirty = newContent !== this._originalContent;

        // Only trigger re-render if dirty state actually changed
        if (this._isDirty !== newIsDirty) {
            this._isDirty = newIsDirty;
        }
    }

    _save() {
        if (!this._isDirty || this.saving) {
            return;
        }
        this.saving = true;
        this.dispatchEvent(new CustomEvent('save-content', {
            bubbles: true,
            composed: true,
            detail: {
                content: this._editedContent,
                filePath: this.filePath
            }
        }));
    }

    _cancel() {
        if (!this._isDirty) return;

        if (confirm('You have unsaved changes. Are you sure you want to discard them?')) {
            this._editedContent = this._originalContent;
            this._isDirty = false;

            this._updateCodeEditorContent();
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
        this.content = this._editedContent;
        this._originalContent = this.content;
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

customElements
    .define(
        'qwc-simple-editor'
        ,
        RoqSimpleEditor
    )
;

