import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import './page-card.js';

export class PagesList extends LitElement {
    
    static properties = {
        pages: { type: Array },
        collectionId: { type: String }
    };

    static styles = css`
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--lumo-space-m);
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
        return html`
            <div class="toolbar">
                <h2>${this.collectionId?.toUpperCase() || 'PAGES'}</h2>
                <vaadin-button 
                    theme="primary" 
                    @click="${this._addNewPage}">
                    <vaadin-icon icon="font-awesome-solid:plus" slot="prefix"></vaadin-icon>
                    Add New ${type}
                </vaadin-button>
            </div>
            ${pages.length > 0 
                ? html`
                    <div class="pages">
                        ${pages.map(page => html`
                            <qwc-page-card 
                                .page="${page}">
                            </qwc-page-card>
                        `)}
                    </div>
                `
                : html`<p>No ${type} found. Click "Add New ${type}" to create your first ${type}.</p>`
            }
        `;
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

