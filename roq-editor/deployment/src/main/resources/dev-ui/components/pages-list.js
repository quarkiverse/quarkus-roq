import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/text-field';
import './page-card.js';
import './sync-status-bar.js';

export class PagesList extends LitElement {
    
    static properties = {
        pages: { type: Array },
        collectionId: { type: String },
        syncStatus: { type: Object },
        syncing: { type: Boolean },
        publishing: { type: Boolean },
        _syncingAllPaths: { type: Boolean, state: true },
        _filterText: { type: String, state: true }
    };

    constructor() {
        super();
        this._filterText = '';
        this._syncingAllPaths = false;
    }

    static styles = css`
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--lumo-space-m);
        }
        .toolbar-actions {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
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
        const unsyncedPages = pages.filter(p => p.suggestedPath);
        const hasSync = this.syncStatus && this.syncStatus.branch && this.syncStatus.branch !== 'no-git-repo';
        return html`
            <div class="toolbar">
                <h2>${this.collectionId?.toUpperCase() || 'PAGES'}</h2>
                <div class="toolbar-actions">
                    <vaadin-text-field
                        class="filter-input"
                        placeholder="Filter by title or description..."
                        .value="${this._filterText}"
                        @input="${this._onFilterInput}"
                        clear-button-visible>
                        <vaadin-icon icon="font-awesome-solid:magnifying-glass" slot="prefix"></vaadin-icon>
                    </vaadin-text-field>
                    ${hasSync ? html`
                        <qwc-sync-status-bar
                            .status="${this.syncStatus}"
                            .syncing="${this.syncing}"
                            .publishing="${this.publishing}">
                        </qwc-sync-status-bar>
                    ` : ''}
                    ${unsyncedPages.length > 0 ? html`
                        <vaadin-button
                            theme="warning"
                            ?disabled="${this._syncingAllPaths}"
                            @click="${this._onSyncAllNames}">
                            <vaadin-icon icon="font-awesome-solid:camera-rotate" slot="prefix"></vaadin-icon>
                            Sync all names (${unsyncedPages.length})
                        </vaadin-button>
                    ` : ''}
                    <vaadin-button
                        theme="primary"
                        @click="${this._addNewPage}">
                        <vaadin-icon icon="font-awesome-solid:plus" slot="prefix"></vaadin-icon>
                        Add New ${type}
                    </vaadin-button>
                </div>
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

    _onSyncAllNames() {
        const pages = (this.pages || []).filter(p => p.suggestedPath);
        this.dispatchEvent(new CustomEvent('sync-all-names', {
            bubbles: true,
            composed: true,
            detail: { pages }
        }));
    }

}

customElements.define('qwc-pages-list', PagesList);

