import { LitElement, html, css } from 'lit';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/icon';

export class TagsList extends LitElement {
    
    static properties = {
        tags: { type: Array }
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
        const tags = this.tags || [];
        
        return html`
            <div class="toolbar">
                <h2>Tags</h2>
            </div>
            ${tags.length > 0 
                ? html`
                    <vaadin-grid .items="${tags}" class="datatable" theme="no-border">
                        <vaadin-grid-column auto-width
                                            header="Path"
                                            flex-grow="1"
                                            ${columnBodyRenderer(this._pathRenderer, [])}>
                        </vaadin-grid-column>
                        <vaadin-grid-column auto-width
                                            header="File"
                                            flex-grow="1"
                                            ${columnBodyRenderer(this._fileRenderer, [])}>
                        </vaadin-grid-column>
                        <vaadin-grid-column path="source" flex-grow="0" width="10em"></vaadin-grid-column>
                        <vaadin-grid-column header="Link"
                                            width="6em"
                                            flex-grow="0"
                                            ${columnBodyRenderer(this._linkRenderer, [])}>
                        </vaadin-grid-column>
                    </vaadin-grid>
                `
                : html`<p>No tags found.</p>`
            }
        `;
    }

    _fileRenderer(tag) {
        return html`${tag.outputPath}`;
    }

    _pathRenderer(tag) {
        return html`${tag.path}`;
    }

    _linkRenderer(tag) {
        return html`<a href="${tag.path}" style="color: white" target="_blank">
            <vaadin-icon class="linkOut" icon="font-awesome-solid:up-right-from-square"/>
        </a>`;
    }
}

customElements.define('qwc-tags-list', TagsList);

