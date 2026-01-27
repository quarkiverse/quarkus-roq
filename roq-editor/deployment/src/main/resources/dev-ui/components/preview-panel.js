import { LitElement, css, html } from 'lit';

export class PreviewPanel extends LitElement {

    static properties = {
        previewUrl: { type: String }
    };

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            height: 100%;
            overflow: hidden;
        }
        .preview-container {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .preview-iframe {
            flex: 1;
            width: 100%;
            border: none;
            background: var(--lumo-base-color);
        }
     
    `;

    constructor() {
        super();
        this.previewUrl = null;
    }

    connectedCallback() {
        super.connectedCallback();
        window.addEventListener('request-preview-refresh', this._onRefresh);
    }
    disconnectedCallback() {
        window.removeEventListener('request-preview-refresh', this._onRefresh);
        super.disconnectedCallback();
    }

    _onRefresh = () => {
        const iframe = this.renderRoot.querySelector('.preview-iframe');
        iframe?.contentWindow?.location.reload();
    }


    render() {
        if (!this.previewUrl) {
            return html``;
        }

        return html`
            <div class="preview-container">
                <iframe 
                    class="preview-iframe"
                    src="${this.previewUrl}"
                    title="Preview">
                </iframe>
            </div>
        `;
    }



}

customElements.define('qwc-preview-panel', PreviewPanel);

