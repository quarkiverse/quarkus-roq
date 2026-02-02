import { LitElement, css, html } from 'lit';
import { ContextConsumer } from '../bundle.js';
import { editorContext } from './visual-editor/editor-context.js';

export class Toolbar extends LitElement {

  static properties = {
    previewUrl: { type: String },
    activeTab: { type: String, reflect: true, attribute: 'active-tab' },
    showEditorTab: { type: Boolean }
  };

  static styles = css`
      :host {
          display: flex;
          flex-direction: column;
      }
      .tabs {
          display: flex;
          gap: var(--lumo-space-xs);
          border-bottom: 1px solid var(--lumo-contrast-20pct);
          padding: 0;
          background: var(--lumo-base-color);
      }
      .tabs vaadin-button {
          font-size: var(--lumo-font-size-xxs);
      }
      .tab {
          padding: var(--lumo-space-s) var(--lumo-space-m);
          border: none;
          background: transparent;
          color: var(--lumo-contrast-70pct);
          cursor: pointer;
          border-bottom: 2px solid transparent;
          font-size: var(--lumo-font-size-s);
          transition: all 0.2s;
      }
      .tab:hover {
          color: var(--lumo-body-text-color);
      }
      .tab.active {
          color: var(--lumo-primary-color);
          border-bottom-color: var(--lumo-primary-color);
      }
      .editor-toolbar {
          display: flex;
          gap: var(--lumo-space-s);
          border-bottom: 1px solid var(--lumo-contrast-20pct);
          background: var(--lumo-base-color);
      }
      .editor-toolbar vaadin-button {
          font-size: var(--lumo-font-size-xxs);
      }

      .spacer {
          flex-grow: 1;
      }

     
    `;

  constructor() {
    super();
    this.activeTab = 'editor';
    this.showEditorTab = true;
    this._editor = null;

    this._editorConsumer = new ContextConsumer(this, {
      context: editorContext,
      subscribe: true
    });
  }

  _onTabClick(tab) {
    this.dispatchEvent(new CustomEvent('tab-changed', {
      bubbles: true,
      composed: true,
      detail: { tab }
    }));
  }

  get editor() {
    return this._editorConsumer.value?.editor && !this._editorConsumer.value?.editor.isDestroyed ? this._editorConsumer.value?.editor : null;
  }
  
  _canUndo() {
    return this.editor && this.editor.can().undo();
  }

  _canRedo() {
    return this.editor && this.editor.can().redo();
  }

  _undo() {
    if (this.editor) {
      this.editor.chain().focus().undo().run();
      this.requestUpdate();
    }
  }

  _redo() {
    if (this.editor) {
      this.editor.chain().focus().redo().run();
      this.requestUpdate();
    }
  }

    _onRefreshPreview() {
        this.dispatchEvent(new CustomEvent('request-preview-refresh', {
            bubbles: true,
            composed: true
        }));
    }

  render() {
      return html`
        <div class="tabs">
          ${this.showEditorTab ? html`
            <button
              class="tab ${this.activeTab === 'editor' ? 'active' : ''}"
              @click="${() => this._onTabClick('editor')}">
              Editor
            </button>` : ''}
          <button
            class="tab ${this.activeTab === 'code' ? 'active' : ''}"
            @click="${() => this._onTabClick('code')}">
            Code
          </button>
          <button
            class="tab ${this.activeTab === 'preview' ? 'active' : ''}"
            @click="${() => this._onTabClick('preview')}">
            Preview
          </button>
          <a href="${this.previewUrl}" target="_blank" rel="noopener noreferrer">
            <vaadin-button theme="icon">
              <vaadin-icon icon="font-awesome-solid:arrow-up-right-from-square"></vaadin-icon>
            </vaadin-button>
          </a>
          <div class="spacer"></div>
          ${this.activeTab === "preview" ? html`
            <vaadin-button
              class="refresh-button"
              theme="tertiary rotate icon"
              @click="${this._onRefreshPreview}"
              title="Refresh preview">
              <vaadin-icon icon="font-awesome-solid:rotate" slot="prefix"></vaadin-icon>
            </vaadin-button>` : ''}
        </div>
        ${this.showEditorTab && this.activeTab === "editor" ? html`
          <div class="editor-toolbar">
            <vaadin-button
              theme="tertiary"
              ?disabled="${!this._canUndo()}"
              @click="${this._undo}">
              <vaadin-icon icon="font-awesome-solid:arrow-rotate-left" slot="prefix"></vaadin-icon>
            </vaadin-button>
            <vaadin-button
              theme="tertiary"
              ?disabled="${!this._canRedo()}"
              @click="${this._redo}">
              <vaadin-icon icon="font-awesome-solid:arrow-rotate-right" slot="prefix"></vaadin-icon>
              </vaadin:button>
          </div>` : ''}
    `;
  }


}

customElements.define('qwc-toolbar', Toolbar);

