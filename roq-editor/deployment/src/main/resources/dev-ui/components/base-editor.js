
// base-editor.js
import { LitElement, css, html } from 'lit';
import { hljsTheme } from '../hljs-theme.js';
import { showConfirm } from './confirm-dialog.js';
import './preview-panel.js';
import './toolbar.js';

export class BaseEditor extends LitElement {
    static properties = {
        content: { type: String },
        page: { type: Object },
        loading: { type: Boolean },
        saving: { type: Boolean },

        // Shared internal state
        _error: { state: true },
        _editorValue: { state: true },
        _isDirty: { state: true },
        _storedContent: { state: true },
        _activeTab: { state: true },
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

      .preview-container {
        min-height: 100%;
        flex: 1;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }

      .preview-container[hidden] {
        display: none;
      }
    `,
    ];

    constructor() {
        super();
        this._editorValue = '';
        this._storedContent = '';
        this._isDirty = false;
        this._activeTab = 'editor';
        this._error = null;
        this.loading = false;
        this.saving = false;
        this.page = null;

        this._handleKeyDown = this._handleKeyDown.bind(this);
    }

    /** Subclasses can override to show/hide the visual editor tab */
    get showEditorTab() {
        return false; // Simple editor defaults to no "editor" tab
    }

    /** Subclasses can override to return a different preview URL if needed */
    _getPreviewUrl = () => {
        return this.page?.url;
    }

    /** ---- Lifecycle ---- */
    firstUpdated() {
        window.addEventListener('keydown', this._handleKeyDown, true);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        window.removeEventListener('keydown', this._handleKeyDown, true);
    }

    /** ---- Shared guards ---- */
    _hasContent() {
        return this.content != null;
    }

    _isError() {
        return this._error;
    }

    /** ---- Shared UI ---- */
    _renderHeader() {
        const isError = this._isError();
        return html`
      <div class="editor-header">
        <h3 class="editor-title">
          <page-path-editor .page="${this.page}"/>
          ${this._isDirty ? html`<span class="dirty-indicator">(unsaved changes)</span>` : ''}
        </h3>
        <div class="header-actions">
          ${!isError
            ? html`
                <vaadin-button
                  theme="primary"
                  ?disabled="${!this._isDirty || this.saving}"
                  @click="${this._save}"
                >
                  <vaadin-icon icon="font-awesome-solid:floppy-disk" slot="prefix"></vaadin-icon>
                  ${this.saving ? 'Saving...' : 'Save'}
                </vaadin-button>
                <vaadin-button
                  theme="tertiary"
                  ?disabled="${!this._isDirty || this.saving}"
                  @click="${this._cancel}"
                >
                  Cancel
                </vaadin-button>
              `
            : ''}
          <vaadin-button theme="tertiary" @click="${this._close}">
            <vaadin-icon icon="font-awesome-solid:xmark" slot="prefix"></vaadin-icon>
            Close
          </vaadin-button>
        </div>
      </div>
    `;
    }

    /** Subclass must implement actual panels (editor/code/frontmatter, etc.) */
    _renderEditorPanels() {
        return html``;
    }

    render() {
        if (this.loading) {
            return html`
        <div class="editor-container">
          ${this._renderHeader()}
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
        ${this._renderHeader()}
        <div class="editor-content">
          ${isError
            ? html`<div class="error">${this.error}</div>`
            : html`
                <qwc-toolbar
                  .activeTab="${this._activeTab}"
                  .showEditorTab=${this.showEditorTab}
                  @tab-changed="${this._onTabChanged}"
                  .previewUrl="${this._getPreviewUrl()}"
                ></qwc-toolbar>

                <div class="preview-container" ?hidden="${this._activeTab !== 'preview'}">
                  <qwc-preview-panel .previewUrl="${this._getPreviewUrl()}"></qwc-preview-panel>
                </div>

                ${this._renderEditorPanels()}
              `}
        </div>
      </div>
    `;
    }

    /** ---- Shared tab handling (subclasses may hook into before/after) ---- */
    async _onTabChanged(e) {
        const newTab = e.detail.tab;
        const previousTab = this._activeTab;

        if (this._isDirty && newTab === 'preview') {
            const save = await showConfirm('You have unsaved changes which won\'t be visible...', { title: 'Do you want to save for the preview?', cancelText: 'No thanks',  confirmText: 'Save Changes' });
            if (save) {
                this._save();
                return;
            }
        }

        if (this._beforeTabChange) {
            this._beforeTabChange(previousTab, newTab);
        }

        this._activeTab = newTab;

        if (this._afterTabChange) {
            this._afterTabChange(previousTab, newTab);
        }

        this.requestUpdate();
    }

    /** ---- Shared keyboard shortcut ---- */
    _handleKeyDown(e) {
        // Mod+S
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            this._save();
            return false;
        }
    }

    /** ---- Shared dialog flows ---- */
    async _close() {
        if (this._isDirty) {
            const confirmed = await showConfirm(
                'You have unsaved changes 😅',
                { title: "Do you really want to close?",  confirmText: 'Discard Changes', theme: 'error' }
            );
            if (!confirmed) return;
        }
        this.dispatchEvent(
            new CustomEvent('close-viewer', {
                bubbles: true,
                composed: true,
            }),
        );
    }

    /** Subclasses must implement:
     *  - _save()
     *  - _cancel()
     */

    /** ---- Shared save result helpers ---- */
    markSaved() {
        // Default: simple content editor behavior
        this.content = this._editorValue;
        this._storedContent = this.content;
        this._isDirty = false;
        this.saving = false;
    }

    markSaveError() {
        this.saving = false;
    }
}
