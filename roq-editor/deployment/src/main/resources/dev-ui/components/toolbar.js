import { LitElement, css, html } from 'lit';
import { ContextConsumer } from '../bundle.js';
import { editorContext } from './editor-context.js';
import './preview-panel.js';

export class Toolbar extends LitElement {

  static properties = {
    previewUrl: { type: String },
    activeTab: { type: String, reflect: true, attribute: 'active-tab' }
  };

  static styles = css`
        :host {
            display: flex;
            flex-direction: column;
        }
        :host([active-tab="preview"]) {
            height: 100%;
        }
        .tabs {
            display: flex;
            gap: var(--lumo-space-xs);
            border-bottom: 1px solid var(--lumo-contrast-20pct);
            padding: 0 var(--lumo-space-m);
            background: var(--lumo-base-color);
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
            padding: var(--lumo-space-s) var(--lumo-space-m);
            border-bottom: 1px solid var(--lumo-contrast-20pct);
            background: var(--lumo-base-color);
        }
    `;

  constructor() {
    super();
    this.activeTab = 'editor';
    this._editor = null;
    this.previewUrl = null;

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

  render() {
    return html`
            <div class="tabs">
                <button 
                    class="tab ${this.activeTab === 'editor' ? 'active' : ''}"
                    @click="${() => this._onTabClick('editor')}">
                    Editor
                </button>
                <button 
                    class="tab ${this.activeTab === 'preview' ? 'active' : ''}"
                    @click="${() => this._onTabClick('preview')}">
                    Preview
                </button>
            </div>
            ${this.activeTab === "editor" ? html`
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
                    </vaadin-button>
                </div>` : html`<qwc-preview-panel .previewUrl="${this.previewUrl}"></qwc-preview-panel>`}
        `;
  }
}

customElements.define('qwc-toolbar', Toolbar);

