import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import './page-path-editor.js';

export class PageCard extends LitElement {
    
    static properties = {
        page: { type: Object }
    };

    static styles = css`
        .page-card {
            display: flex;
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            overflow: hidden;
            transition: all 0.2s ease;
            cursor: pointer;
            position: relative;
        }

        .spacer {
            flex-grow: 1;
        }


        .page-card:hover {
            border-color: var(--lumo-primary-color);
            box-shadow: 0 2px 8px var(--lumo-primary-color-10pct);
            transform: translateY(-1px);
        }

        vaadin-button {
            --lumo-icon-size-m: 16px;
        }

        .delete-button {
            opacity: 0;
            transition: opacity 0.2s ease;
        }
    
        .page-card:hover .delete-button {
            opacity: 1;
        }
        
        .page-content {
            flex: 1;
            padding: var(--lumo-space-m);
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            min-width: 0;
        }
        .page-header {
            margin-bottom: var(--lumo-space-s);
            display: flex;
            flex-direction: column;
        }
        .page-title {
            display: flex;
            align-items: center;
            flex-flow: row nowrap;
            gap: var(--lumo-space-m);
        }
        .page-title h2 {
            font-size: var(--lumo-font-size-l);
            font-weight: 600;
            color: var(--lumo-body-text-color);
            margin: 0 0 var(--lumo-space-xs) 0;
            line-height: 1.4;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .page-title a {
            color: inherit;
            text-decoration: none;
        }
        .page-title a:hover {
            color: var(--lumo-primary-color);
        }
        .page-footer {
            display: flex;
            flex-wrap: wrap;
            flex-flow: row nowrap;
            gap: var(--lumo-space-s);
            align-items: center;
            margin-top: auto;
            padding-top: var(--lumo-space-s);
            border-top: 1px solid var(--lumo-contrast-10pct);
        }
        
        .page-markup {
            display: inline-flex;
            align-items: center;
            padding: 2px var(--lumo-space-xs);
            background: var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-s);
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-contrast-70pct);
            font-weight: 500;
            text-transform: uppercase;
        }
        .page-meta {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-contrast-60pct);
            margin-top: var(--lumo-space-xs);
        }
        .page-path {
            font-family: var(--lumo-font-family-mono);
            color: var(--lumo-contrast-50pct);
        }
        .page-date {
            color: var(--lumo-contrast-60pct);
        }
        .meta-separator {
            color: var(--lumo-contrast-30pct);
        }
    `;

    render() {
        if (!this.page) {
            return html``;
        }

        const title = this.page.title || '';
        const path = this.page.path;
        const markup = this.page.markup;
        const description = this.page.description || '';
        const date = this.page.date || '';

        return html`
          <article class="page-card" @click="${this._onCardClick}">
            <div class="page-content">
              <div class="page-header">
                <div class="page-title">
                  ${markup
                    ? html`<span class="page-markup">${markup.toUpperCase()}</span>`
                    : html``
                  }
                  <h2>${title}</h2>
                  <span class="spacer" ></span>
                  <a href="${this.page.url}" target="_blank">
                    <vaadin-button theme="icon tertiary" class="preview-button" @click="${this._stopPropagation}">
                      <vaadin-icon icon="font-awesome-solid:arrow-up-right-from-square"></vaadin-icon>
                    </vaadin-button>
                  </a>
                </div>
                <div class="page-meta">
                  <page-path-editor .page="${this.page}"/>
                  ${path && date
                    ? html`<span class="meta-separator">•</span>`
                    : html``
                  }
                  ${date
                    ? html`<span class="page-date">${date}</span>`
                    : html``
                  }
                </div>
              </div>
              <div class="page-footer">
                ${description
                  ? html`<p class="page-path" title="${description}">${description}</p>`
                  : html``
                }
                <span class="spacer"></span>
                <vaadin-button
                  class="delete-button"
                  theme="tertiary error icon"
                  @click="${this._onDeleteClick}"
                  title="Delete page">
                  <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                </vaadin-button>
              </div>
            </div>
          </article>
        `;
    }

    _stopPropagation(e) {
        e.stopPropagation();
    }

    _onCardClick(e) {
        // Don't trigger if clicking on a link
        if (e.target.tagName === 'A') {
            return;
        }
        e.preventDefault();
        e.stopPropagation();
        this.dispatchEvent(new CustomEvent('page-open', {
            composed: true,
            bubbles: true,
            detail: { page: this.page }
        }));
    }

    _onDeleteClick(e) {
        e.stopPropagation();
        e.preventDefault();
        this.dispatchEvent(new CustomEvent('page-delete', {
            composed: true,
            bubbles: true,
            detail: { page: this.page }
        }));
    }
}

customElements.define('qwc-page-card', PageCard);

