import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/text-field';
import './page-card.js';

export class PagesList extends LitElement {
    
    static properties = {
        pages: { type: Array },
        collectionId: { type: String },
        _filterText: { type: String, state: true }
    };

    constructor() {
        super();
        this._filterText = '';
    }

    static styles = css`
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--lumo-space-m);
        }
        .filter-input {
            min-width: 250px;
        }
        .pages-list {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-l);
        }
    `;

    render() {
        const pages = this.pages || [];
        const type = this.collectionId ? 'Document' : 'Page';
        const filteredPages = this._filterPages(pages);
        return html`
            <div class="toolbar">
                <h2>${this.collectionId?.toUpperCase() || 'PAGES'}</h2>
                <vaadin-text-field
                    class="filter-input"
                    placeholder="Filter by title or description..."
                    .value="${this._filterText}"
                    @input="${this._onFilterInput}"
                    clear-button-visible>
                    <vaadin-icon icon="font-awesome-solid:magnifying-glass" slot="prefix"></vaadin-icon>
                </vaadin-text-field>
                <vaadin-button 
                    theme="primary" 
                    @click="${this._addNewPage}">
                    <vaadin-icon icon="font-awesome-solid:plus" slot="prefix"></vaadin-icon>
                    Add New ${type}
                </vaadin-button>
            </div>
            ${filteredPages.length > 0 
                ? html`
                    <div class="pages">
                        ${filteredPages.map(page => html`
                            <qwc-page-card 
                                .page="${page}">
                            </qwc-page-card>
                        `)}
                    </div>
                `
                : pages.length > 0
                    ? html`<p>No ${type} matching "${this._filterText}".</p>`
                    : html`<p>No ${type} found. Click "Add New ${type}" to create your first ${type}.</p>`
            }
        `;
    }

    _filterPages(pages) {
        if (!this._filterText) {
            return pages;
        }
        const filterLower = this._filterText.toLowerCase();
        return pages.filter(page => {
            const title = (page.title || '').toLowerCase();
            const description = (page.description || '').toLowerCase();
            return title.includes(filterLower) || description.includes(filterLower);
        });
    }

    _onFilterInput(e) {
        this._filterText = e.target.value;
    }

    _addNewPage() {
        // Dispatch event to parent component
        this.dispatchEvent(new CustomEvent('add-new-page', {
            detail: {
                collectionId: this.collectionId
            }
        }));
    }

}

customElements.define('qwc-pages-list', PagesList);

