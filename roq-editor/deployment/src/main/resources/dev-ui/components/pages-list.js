import { LitElement, html, css } from 'lit';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/icon';

export class PagesList extends LitElement {
    
    static properties = {
        pages: { type: Array }
    };

    static styles = css`
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--lumo-space-m);
        }
    `;

    render() {
        const pages = this.pages || [];
        
        return html`
            <div class="toolbar">
                <h2>Pages</h2>
            </div>
            ${pages.length > 0 
                ? html`
                    <vaadin-grid .items="${pages}" 
                                 class="datatable" 
                                 theme="no-border"
                                 @active-item-changed="${this._onRowClick}">
                        <vaadin-grid-column auto-width
                                            header="Path"
                                            flex-grow="1"
                                            ${columnBodyRenderer(this._pathRenderer, [])}>
                        </vaadin-grid-column>
                        <vaadin-grid-column auto-width
                                            header="Title"
                                            flex-grow="1"
                                            ${columnBodyRenderer(this._titleRenderer, [])}>
                        </vaadin-grid-column>
                    </vaadin-grid>
                `
                : html`<p>No pages found.</p>`
            }
        `;
    }

    _titleRenderer(page) {
        return html`${page.title}`;
    }

    _pathRenderer(page) {
        return html`${page.path}`;
    }

    _onRowClick(e) {
        const page = e.detail.value;
        if (page) {
            // Dispatch event to parent component
            this.dispatchEvent(new CustomEvent('page-clicked', {
                bubbles: true,
                composed: true,
                detail: { page }
            }));
        }
    }
}

customElements.define('qwc-pages-list', PagesList);

