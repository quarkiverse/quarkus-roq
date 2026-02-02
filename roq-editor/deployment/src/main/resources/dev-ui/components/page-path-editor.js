import { LitElement, html, css } from 'lit';
import '@vaadin/icon';
import {config} from 'build-time-data';

export class PagePathEditor extends LitElement {

    static properties = {
        page: { type: Object },
    };

    static styles = css`
        
        :host {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-m);
        }

        vaadin-button {
            --lumo-icon-size-m: 16px;
        }


        
        .page-path-change-suggestion {
            display: flex;
            flex-direction: column;
            align-items: start;
        }
        
        .page-path {
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-contrast-50pct);
            font-family: var(--lumo-font-family-mono);
            margin: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            flex: 1;
            min-width: 0;
        }
        .page-path.stale {
            text-decoration: line-through;
        }
    `;

    render() {
        return html`
          ${config.suggestedPath.enabled && this.page.suggestedPath ? html`
            <div class="page-path-change-suggestion">
              <span class="page-path stale">${this.page.path}</span>
              <span class="page-path suggested">${this.page.suggestedPath}</span>
            </div>
            <vaadin-button
              class="sync-path-button"
              theme="secondary icon warning"
              @click="${this._onSyncPath}"
              title="Synchronise page file path">
              <vaadin-icon icon="font-awesome-solid:camera-rotate" slot="prefix"></vaadin-icon>
            </vaadin-button>
          ` : html`<span class="page-path">${this.page.path}</span>`}
        `;
    }

    _onSyncPath(e) {
        e.stopPropagation(); // Prevent triggering the card click
        e.preventDefault(); // Prevent any default behavior
        this.dispatchEvent(new CustomEvent('page-sync-path', {
            bubbles: true, // Need to bubble to reach pages-list
            composed: true,
            detail: { page: this.page }
        }));
    }

}

customElements.define('page-path-editor', PagePathEditor);

