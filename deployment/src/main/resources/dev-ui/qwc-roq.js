import { LitElement, html, css} from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/form-layout';
import '@vaadin/progress-bar';
import '@vaadin/checkbox';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@qomponent/qui-alert';

export class QwcRoq extends LitElement {

    jsonRpc = new JsonRpc(this);

    // Component style
    static styles = css`
        .button {
            background-color: transparent;
            cursor: pointer;
        }
        .clearIcon {
            color: orange;
        }
        `;

    // Component properties
    static properties = {
        "_pages": {state: true},
    }

    constructor() {
        super();
    }

    // Components callbacks

    /**
     * Called when displayed
     */
    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getStaticPages().then(jsonRpcResponse => {
            this._pages = [];
            jsonRpcResponse.result.forEach(c => {
                this._pages.push(c);
            });
        });
    }

    /**
     * Called when it needs to render the components
     * @returns {*}
     */
    render() {
        if (this._pages) {
            return this._renderTable();
        } else {
            return html`<span>Loading pages...</span>`;
        }
    }

    // View / Templates

    _renderTable() {
        return html`
          <div class="menubar">
            <qui-alert level="warning">
              <div>
                <p>Generating in dev-mode is not using the production application. <br/> Run <code>QUARKUS_ROQ_BATCH=true
                  java -jar target/quarkus-app/quarkus-run.jar</code></p>
                <br/>
                <vaadin-button @click="${this._generate}" id="start-cnt-testing-btn" theme="tertiary" tabindex="0"
                               role="button">
                  <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                  Generate
                </vaadin-button>
              </div>
            </qui-alert>
          </div>
          <vaadin-grid .items="${this._pages}" class="datatable" theme="no-border">
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

            <vaadin-grid-column path="type" flex-grow="0" width="12em"></vaadin-grid-column>
            <vaadin-grid-column header="Link"
                                width="6em"
                                flex-grow="0"
                                ${columnBodyRenderer(this._linkRenderer, [])}>
            </vaadin-grid-column>
          </vaadin-grid>


        `;
    }

    _generate() {
        this.jsonRpc.generate().then(jsonRpcResponse => {
            alert("Roq generation succeeded in directory: " + jsonRpcResponse.result);
        });
    }

    _fileRenderer(page) {
        return html`${page.outputPath}`;
    }

    _pathRenderer(page) {
        return html`${page.path}`;
    }

    _linkRenderer(page) {
        return html`<a href="${page.path}" style="color: white" target="_blank"><vaadin-icon class="linkOut"
                                                                        icon="font-awesome-solid:up-right-from-square"/></a>`;
    }



}
customElements.define('qwc-roq', QwcRoq);