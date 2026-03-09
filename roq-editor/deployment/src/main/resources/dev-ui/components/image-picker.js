import { LitElement, html, css } from 'lit';
import '@vaadin/tabs';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/upload';
import '@vaadin/progress-bar';

export class ImagePicker extends LitElement {
  static properties = {
    pagePath: { type: String },
    pageImages: { type: Array },
    publicImages: { type: Array },
    loading: { type: Boolean },
    uploading: { type: Boolean },
    uploadError: { type: String },
    _selectedTab: { state: true },
    _error: { state: true }
  };

  static styles = css`
        :host {
            display: block;
        }

        .picker-container {
            min-width: 400px;
            max-width: 600px;
        }

        .tabs-container {
            margin-bottom: var(--lumo-space-m);
        }

        .images-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
            gap: var(--lumo-space-s);
            max-height: 300px;
            overflow-y: auto;
            padding: var(--lumo-space-s);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            background: var(--lumo-contrast-5pct);
        }

        .image-item {
            position: relative;
            aspect-ratio: 1;
            border: 2px solid transparent;
            border-radius: var(--lumo-border-radius-s);
            overflow: hidden;
            cursor: pointer;
            transition: all 0.2s ease;
            background: var(--lumo-base-color);
        }

        .image-item:hover {
            border-color: var(--lumo-primary-color-50pct);
            transform: scale(1.02);
        }

        .image-item.selected {
            border-color: var(--lumo-primary-color);
            box-shadow: 0 0 0 2px var(--lumo-primary-color-50pct);
        }

        .image-item img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .image-item .image-name {
            position: absolute;
            bottom: 0;
            left: 0;
            right: 0;
            padding: var(--lumo-space-xs);
            background: rgba(0, 0, 0, 0.7);
            color: white;
            font-size: var(--lumo-font-size-xxs);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            opacity: 0;
            transition: opacity 0.2s ease;
        }

        .image-item:hover .image-name {
            opacity: 1;
        }

        .empty-state {
            padding: var(--lumo-space-l);
            text-align: center;
            color: var(--lumo-contrast-60pct);
            font-size: var(--lumo-font-size-s);
        }

        .upload-section {
            margin-top: var(--lumo-space-m);
            padding-top: var(--lumo-space-m);
            border-top: 1px solid var(--lumo-contrast-20pct);
        }

        .upload-section h4 {
            margin: 0 0 var(--lumo-space-s) 0;
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-contrast-70pct);
        }

        .upload-area {
            border: 2px dashed var(--lumo-contrast-30pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-m);
            text-align: center;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .upload-area:hover {
            border-color: var(--lumo-primary-color);
            background: var(--lumo-primary-color-10pct);
        }

        .upload-area.dragover {
            border-color: var(--lumo-primary-color);
            background: var(--lumo-primary-color-10pct);
        }

        .upload-icon {
            font-size: 24px;
            margin-bottom: var(--lumo-space-xs);
            color: var(--lumo-contrast-50pct);
        }

        .upload-text {
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-contrast-60pct);
        }

        .upload-hint {
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-contrast-50pct);
            margin-top: var(--lumo-space-xs);
        }

        .error-message {
            padding: var(--lumo-space-s);
            background: var(--lumo-error-color-10pct);
            color: var(--lumo-error-text-color);
            border-radius: var(--lumo-border-radius-s);
            font-size: var(--lumo-font-size-s);
            margin-bottom: var(--lumo-space-m);
        }

        .loading-state {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: var(--lumo-space-l);
            color: var(--lumo-contrast-60pct);
        }

        input[type="file"] {
            display: none;
        }
    `;

  constructor() {
    super();
    this.pageImages = [];
    this.publicImages = [];
    this.pagePath = '';
    this.loading = false;
    this.uploading = false;
    this.uploadError = null;
    this._selectedTab = 0;
    this._error = null;
  }

  render() {
    const errorToShow = this.uploadError || this._error;
    return html`
            <div class="picker-container">
                ${errorToShow ? html`
                    <div class="error-message">${errorToShow}</div>
                ` : ''}

                <div class="tabs-container">
                    <vaadin-tabs .selected="${this._selectedTab}" @selected-changed="${this._onTabChange}">
                        <vaadin-tab>Page Images</vaadin-tab>
                        <vaadin-tab>Site Images</vaadin-tab>
                    </vaadin-tabs>
                </div>

                ${this.loading ? html`
                    <div class="loading-state">
                        <span>Loading images...</span>
                    </div>
                ` : this._renderImagesGrid()}

                ${this._renderUploadSection()}
            </div>
        `;
  }

  _renderImagesGrid() {
    const images = this._selectedTab === 0 ? this.pageImages : this.publicImages;

    if (!images || images.length === 0) {
      return html`
                <div class="images-grid">
                    <div class="empty-state">
                        No images found. Upload one below.
                    </div>
                </div>
            `;
    }

    return html`
            <div class="images-grid">
                ${images.map(image => this._renderImageItem(image))}
            </div>
        `;
  }

  _renderImageItem(image) {
    return html`
            <div class="image-item" @click="${() => this._selectImage(image)}" title="${image.name}">
                <img src="${image.path}" alt="${image.name}" loading="lazy" @error="${this._onImageError}">
                <span class="image-name">${image.name}</span>
            </div>
        `;
  }

  _renderUploadSection() {
    const location = this._selectedTab === 0 ? 'page' : 'public';
    const locationLabel = this._selectedTab === 0 ? 'Page Directory' : 'Site Images';

    return html`
            <div class="upload-section">
                <h4>Upload to ${locationLabel}</h4>
                <div 
                    class="upload-area"
                    @click="${this._triggerFileInput}"
                    @dragover="${this._onDragOver}"
                    @dragleave="${this._onDragLeave}"
                    @drop="${this._onDrop}"
                >
                    ${this.uploading ? html`
                        <vaadin-progress-bar indeterminate></vaadin-progress-bar>
                        <div class="upload-text">Uploading...</div>
                    ` : html`
                        <div class="upload-icon">
                            <vaadin-icon icon="font-awesome-solid:cloud-arrow-up"></vaadin-icon>
                        </div>
                        <div class="upload-text">Click or drag image here</div>
                        <div class="upload-hint">JPG, PNG, GIF, WebP, SVG (max 5MB)</div>
                    `}
                </div>
                <input 
                    type="file" 
                    id="file-input-${location}"
                    accept="image/jpeg,image/png,image/gif,image/webp,image/svg+xml"
                    @change="${this._onFileSelected}"
                >
            </div>
        `;
  }

  _onTabChange(e) {
    this._selectedTab = e.detail.value;
    this._error = null;
  }

  _selectImage(image) {
    this.dispatchEvent(new CustomEvent('image-selected', {
      detail: { image, location: this._selectedTab === 0 ? 'page' : 'public' },
      bubbles: true,
      composed: true
    }));
  }

  _triggerFileInput() {
    if (this.uploading) return;
    const location = this._selectedTab === 0 ? 'page' : 'public';
    const input = this.shadowRoot.querySelector(`#file-input-${location}`);
    if (input) {
      input.click();
    }
  }

  _onFileSelected(e) {
    const file = e.target.files[0];
    if (file) {
      this._uploadFile(file);
    }
    e.target.value = '';
  }

  _onDragOver(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.add('dragover');
  }

  _onDragLeave(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('dragover');
  }

  _onDrop(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('dragover');

    const files = e.dataTransfer.files;
    if (files.length > 0) {
      this._uploadFile(files[0]);
    }
  }

  _uploadFile(file) {
    if (!file.type.startsWith('image/')) {
      this._error = 'Please select an image file';
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this._error = 'File size exceeds 10MB limit';
      return;
    }

    this._error = null;
    const location = this._selectedTab === 0 ? 'page' : 'public';

    this.dispatchEvent(new CustomEvent('image-upload', {
      detail: {
        file: file,
        location: location
      },
      bubbles: true,
      composed: true
    }));
  }

  _onImageError(e) {
    e.target.src = 'assets/placeholder-image.svg';
  }
}

customElements.define('qwc-image-picker', ImagePicker);
