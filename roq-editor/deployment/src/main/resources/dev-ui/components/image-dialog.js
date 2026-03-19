import { LitElement, html, css } from 'lit';
import { dialogRenderer, dialogHeaderRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import '@vaadin/dialog';
import '@vaadin/text-field';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/vertical-layout';
import './image-picker.js';

let cachedJsonRpc = null;

function getJsonRpc() {
    if (!cachedJsonRpc) {
        const editor = document.querySelector('qwc-roq-editor');
        cachedJsonRpc = editor?.jsonRpc || null;
    }
    return cachedJsonRpc;
}

export class ImageDialog extends LitElement {
    static properties = {
        _open: { state: true },
        _resolve: { state: true },
        _values: { state: true },
        _showPicker: { state: true },
        _pageImages: { state: true },
        _publicImages: { state: true },
        _loadingImages: { state: true },
        _uploading: { state: true },
        _uploadError: { state: true },
        _pagePath: { state: true }
    };

    static styles = css`
        .form-row {
            display: flex;
            gap: var(--lumo-space-s);
            align-items: flex-end;
        }

        .form-row vaadin-text-field {
            flex: 1;
        }

        .picker-toggle {
            margin-bottom: var(--lumo-space-s);
        }
    `;

    constructor() {
        super();
        this._open = false;
        this._resolve = null;
        this._values = { src: '', alt: '', title: '' };
        this._showPicker = false;
        this._pageImages = [];
        this._publicImages = [];
        this._loadingImages = false;
        this._uploading = false;
        this._uploadError = null;
        this._pagePath = '';
    }

    show(defaultValues = {}, pagePath = '') {
        return new Promise((resolve) => {
            // Cache jsonRpc reference when dialog opens
            if (!cachedJsonRpc) {
                const editor = document.querySelector('qwc-roq-editor');
                cachedJsonRpc = editor?.jsonRpc || null;
            }
            
            this._values = {
                src: defaultValues.src || '',
                alt: defaultValues.alt || '',
                title: defaultValues.title || ''
            };
            this._pagePath = pagePath;
            this._resolve = resolve;
            this._showPicker = false;
            this._pageImages = [];
            this._publicImages = [];
            this._uploading = false;
            this._uploadError = null;
            this._open = true;
            this.requestUpdate();
        });
    }

    render() {
        return html`
            <vaadin-dialog
                .opened=${this._open}
                @closed=${() => { if (this._resolve) this._handleCancel(); }}
                ${dialogHeaderRenderer(() => html`
                    <h2 style="margin:0; font-size: 1.25rem; font-weight: 600;">
                        ${this._showPicker ? 'Select Image' : 'Add Image'}
                    </h2>
                `, [this._showPicker])}
                ${dialogRenderer(() => this._showPicker ? this._renderPicker() : this._renderForm(), 
                    [this._values, this._showPicker, this._pageImages, this._publicImages, this._loadingImages, this._uploading, this._uploadError])}
                ${dialogFooterRenderer(() => html`
                    ${this._showPicker ? html`
                        <vaadin-button theme="tertiary" @click=${() => this._showPicker = false}>
                            <vaadin-icon icon="font-awesome-solid:arrow-left" slot="prefix"></vaadin-icon>
                            Back
                        </vaadin-button>
                        <span style="flex: 1;"></span>
                        <vaadin-button theme="tertiary" @click=${this._handleCancel}>
                            Cancel
                        </vaadin-button>
                    ` : html`
                        <vaadin-button theme="tertiary" @click=${this._handleCancel}>
                            Cancel
                        </vaadin-button>
                        <vaadin-button theme="primary" @click=${this._handleConfirm}>
                            OK
                        </vaadin-button>
                    `}
                `, [this._showPicker])}
            ></vaadin-dialog>
        `;
    }

    _renderForm() {
        return html`
            <vaadin-vertical-layout theme="spacing" style="align-items: stretch; width: 25rem; max-width: 100%;">
                <div class="form-row">
                    <vaadin-text-field
                        label="Source"
                        autofocus
                        .value=${this._values.src}
                        @value-changed=${(e) => this._updateValue('src', e.detail.value)}
                    ></vaadin-text-field>
                    <vaadin-button 
                        theme="secondary" 
                        class="picker-toggle"
                        @click=${this._openPicker}
                        title="Browse images"
                    >
                        <vaadin-icon icon="font-awesome-solid:folder-open" slot="prefix"></vaadin-icon>
                        Browse
                    </vaadin-button>
                </div>
                <vaadin-text-field
                    label="Alternate text"
                    .value=${this._values.alt}
                    @value-changed=${(e) => this._updateValue('alt', e.detail.value)}
                ></vaadin-text-field>
                <vaadin-text-field
                    label="Title"
                    .value=${this._values.title}
                    @value-changed=${(e) => this._updateValue('title', e.detail.value)}
                ></vaadin-text-field>
            </vaadin-vertical-layout>
        `;
    }

    _renderPicker() {
        return html`
            <qwc-image-picker
                .pagePath=${this._pagePath}
                .pageImages=${this._pageImages}
                .publicImages=${this._publicImages}
                .loading=${this._loadingImages}
                .uploading=${this._uploading}
                .uploadError=${this._uploadError}
                @image-selected=${this._onImageSelected}
                @image-upload=${this._onImageUpload}
            ></qwc-image-picker>
        `;
    }

    _updateValue(key, value) {
        this._values = { ...this._values, [key]: value };
    }

    async _openPicker() {
        this._showPicker = true;
        this._loadingImages = true;

        const jsonRpc = getJsonRpc();
        if (jsonRpc) {
            try {
                const [pageResult, publicResult] = await Promise.all([
                    this._pagePath ? jsonRpc.listImages({ pagePath: this._pagePath, location: 'page' }) : Promise.resolve({ result: { images: [] } }),
                    jsonRpc.listImages({ pagePath: this._pagePath, location: 'public' })
                ]);

                this._pageImages = pageResult.result?.images || [];
                this._publicImages = publicResult.result?.images || [];

                if (pageResult.result?.errorMessage) {
                    console.warn('Error loading page images:', pageResult.result.errorMessage);
                }
                if (publicResult.result?.errorMessage) {
                    console.warn('Error loading public images:', publicResult.result.errorMessage);
                }
            } catch (error) {
                console.error('Error loading images:', error);
            }
        }

        this._loadingImages = false;
    }

    _onImageSelected(e) {
        const { image } = e.detail;
        this._values = { ...this._values, src: image.path };
        this._showPicker = false;
    }

    async _onImageUpload(e) {
        const { file, location } = e.detail;
        this._uploading = true;
        this._uploadError = null;

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('pagePath', this._pagePath || '');
            formData.append('location', location);

            const response = await fetch('/q/roq-editor/api/upload-image', {
                method: 'POST',
                body: formData
            });

            const result = await response.json();

            if (result.errorMessage) {
                this._uploadError = result.errorMessage;
            } else {
                const newImage = {
                    name: file.name,
                    path: result.path,
                    size: file.size
                };

                if (location === 'page') {
                    this._pageImages = [...this._pageImages, newImage];
                } else {
                    this._publicImages = [...this._publicImages, newImage];
                }

                this._values = { ...this._values, src: result.path };
                this._showPicker = false;
            }
        } catch (error) {
            console.error('Error uploading image:', error);
            this._uploadError = error.message;
        }

        this._uploading = false;
    }

    _handleConfirm() {
        if (this._resolve) {
            this._resolve({ ...this._values });
            this._resolve = null;
        }
        this._open = false;
    }

    _handleCancel() {
        if (this._resolve) {
            this._resolve({});
            this._resolve = null;
        }
        this._open = false;
    }
}

customElements.define('qwc-image-dialog', ImageDialog);

let dialogInstance = null;

export function showImageDialog(defaultValues = {}, pagePath = '') {
    if (!dialogInstance) {
        dialogInstance = document.createElement('qwc-image-dialog');
        document.body.appendChild(dialogInstance);
    }
    return dialogInstance.show(defaultValues, pagePath);
}
