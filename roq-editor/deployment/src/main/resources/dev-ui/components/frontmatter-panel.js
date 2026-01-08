import { LitElement, html, css } from 'lit';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/button';
import '@vaadin/icon';

export class FrontmatterPanel extends LitElement {
    
    static properties = {
        frontmatter: { type: Object },
        date: { type: String },
        _fields: { state: true }
    };

    static styles = css`
        :host {
            display: block;
            height: 100%;
            overflow: hidden;
        }
        .panel-container {
            display: flex;
            flex-direction: column;
            height: 100%;
            padding: var(--lumo-space-m);
            background: var(--lumo-base-color);
            border-left: 1px solid var(--lumo-contrast-20pct);
            overflow: hidden;
        }
        .panel-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--lumo-space-m);
            padding-bottom: var(--lumo-space-s);
            border-bottom: 1px solid var(--lumo-contrast-20pct);
        }
        .panel-title {
            font-size: var(--lumo-font-size-l);
            font-weight: 600;
            margin: 0;
            color: var(--lumo-body-text-color);
        }
        .fields-container {
            flex: 1;
            overflow-y: auto;
        }
        .field-group {
            margin-bottom: var(--lumo-space-m);
        }
        .field-label {
            display: block;
            font-size: var(--lumo-font-size-s);
            font-weight: 500;
            color: var(--lumo-body-text-color);
            margin-bottom: var(--lumo-space-xs);
        }
        vaadin-text-field,
        vaadin-text-area {
            width: 100%;
        }
        .array-field {
            margin-bottom: var(--lumo-space-s);
        }
        .array-item {
            display: flex;
            gap: var(--lumo-space-xs);
            margin-bottom: var(--lumo-space-xs);
            align-items: center;
        }
        .array-item vaadin-text-field {
            flex: 1;
        }
        .add-button {
            margin-top: var(--lumo-space-xs);
        }
        .empty-state {
            padding: var(--lumo-space-xl);
            text-align: center;
            color: var(--lumo-contrast-60pct);
            font-size: var(--lumo-font-size-s);
        }
        .add-field-section {
            margin-top: var(--lumo-space-l);
            padding-top: var(--lumo-space-m);
            border-top: 1px solid var(--lumo-contrast-20pct);
        }
        .add-field-input {
            display: flex;
            gap: var(--lumo-space-xs);
            margin-bottom: var(--lumo-space-xs);
            align-items: stretch;
        }
        .add-field-input vaadin-text-field {
            flex: 1;
            min-width: 0;
        }
        .add-field-type-select {
            width: 100px;
            flex-shrink: 0;
        }
        select {
            width: 100%;
            padding: var(--lumo-space-xs) var(--lumo-space-s);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            background: var(--lumo-base-color);
            color: var(--lumo-body-text-color);
            font-size: var(--lumo-font-size-s);
            font-family: var(--lumo-font-family);
            box-sizing: border-box;
        }
        select:focus {
            outline: none;
            border-color: var(--lumo-primary-color);
            box-shadow: 0 0 0 2px var(--lumo-primary-color-10pct);
        }
        .date-field {
            margin-bottom: var(--lumo-space-m);
            padding-bottom: var(--lumo-space-m);
            border-bottom: 1px solid var(--lumo-contrast-10pct);
        }
        .date-field vaadin-text-field {
            width: 100%;
        }
    `;

    constructor() {
        super();
        this.frontmatter = {};
        this.date = '';  // Date from file path, displayed but not stored in _fields
        this._fields = {};
        this._fieldTypes = {}; // Store field types: { fieldName: 'textarea' | 'text' | 'number' | 'boolean' | 'array' }
        this._newFieldKey = '';
        this._newFieldType = 'text';
    }

    updated(changedProperties) {
        if (changedProperties.has('frontmatter')) {
            this._fields = { ...this.frontmatter };
            // Infer field types from values if not already set
            for (const [key, value] of Object.entries(this._fields)) {
                if (!this._fieldTypes[key]) {
                    if (Array.isArray(value)) {
                        this._fieldTypes[key] = 'array';
                    } else if (typeof value === 'boolean') {
                        this._fieldTypes[key] = 'boolean';
                    } else if (typeof value === 'number') {
                        this._fieldTypes[key] = 'number';
                    } else if (typeof value === 'string') {
                        // Default to text, but could be textarea if long
                        this._fieldTypes[key] = value.length > 100 ? 'textarea' : 'text';
                    }
                }
            }
        }
    }

    render() {
        return html`
            <div class="panel-container">
                <div class="panel-header">
                    <h3 class="panel-title">Frontmatter</h3>
                </div>
                <div class="fields-container">
                    <div class="date-field">
                        <label class="field-label">Date</label>
                        <vaadin-text-field
                            type="text"
                            value="${this.date || ''}"
                            placeholder="YYYY, MMM D"
                            @input="${this._onDateInput}">
                        </vaadin-text-field>
                    </div>
                    ${Object.keys(this._fields).length === 0
                        ? html`
                            <div class="empty-state">
                                No Frontmatter fields. Add fields below.
                            </div>
                        `
                        : Object.entries(this._fields).map(([key, value]) => 
                            this._renderField(key, value)
                        )
                    }
                    <div class="add-field-section">
                        <div class="field-label">Add Field</div>
                        <div class="add-field-input">
                            <vaadin-text-field
                                placeholder="Field name"
                                value="${this._newFieldKey}"
                                @input="${this._onNewFieldKeyInput}"
                                @keydown="${this._onNewFieldKeyDown}">
                            </vaadin-text-field>
                            <select 
                                class="add-field-type-select"
                                .value="${this._newFieldType}"
                                @change="${this._onNewFieldTypeChange}">
                                <option value="text">Text</option>
                                <option value="number">Number</option>
                                <option value="boolean">Boolean</option>
                                <option value="array">Array</option>
                                <option value="textarea">Textarea</option>
                            </select>
                            <vaadin-button
                                theme="tertiary"
                                @click="${this._addField}"
                                style="flex-shrink: 0;">
                                <vaadin-icon icon="font-awesome-solid:plus" slot="prefix"></vaadin-icon>
                            </vaadin-button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    _renderField(key, value) {
        if (Array.isArray(value)) {
            return html`
                <div class="field-group">
                    <label class="field-label">${key}</label>
                    ${value.map((item, index) => html`
                        <div class="array-item">
                            <vaadin-text-field
                                value="${String(item)}"
                                @input="${(e) => this._updateArrayItem(key, index, e.target.value)}">
                            </vaadin-text-field>
                            <vaadin-button
                                theme="tertiary error"
                                @click="${() => this._removeArrayItem(key, index)}">
                                <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                            </vaadin-button>
                        </div>
                    `)}
                    <vaadin-button
                        theme="tertiary small add-button"
                        @click="${() => this._addArrayItem(key)}">
                        <vaadin-icon icon="font-awesome-solid:plus" slot="prefix"></vaadin-icon>
                        Add Item
                    </vaadin-button>
                    <vaadin-button
                        theme="tertiary error small"
                        @click="${() => this._removeField(key)}"
                        style="margin-top: var(--lumo-space-xs);">
                        <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                        Remove Field
                    </vaadin-button>
                </div>
            `;
        } else if (typeof value === 'object' && value !== null) {
            // For nested objects, render as textarea with YAML-like format
            return html`
                <div class="field-group">
                    <label class="field-label">${key}</label>
                    <vaadin-text-area
                        value="${JSON.stringify(value, null, 2)}"
                        rows="4"
                        @input="${(e) => this._updateField(key, e.target.value, true)}">
                    </vaadin-text-area>
                    <vaadin-button
                        theme="tertiary error small"
                        @click="${() => this._removeField(key)}"
                        style="margin-top: var(--lumo-space-xs);">
                        <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                        Remove Field
                    </vaadin-button>
                </div>
            `;
        } else if (typeof value === 'boolean') {
            // Boolean field - use checkbox
            return html`
                <div class="field-group">
                    <label class="field-label" style="display: flex; align-items: center; gap: var(--lumo-space-xs); cursor: pointer;">
                        <input 
                            type="checkbox"
                            ?checked="${value}"
                            @change="${(e) => this._updateField(key, e.target.checked)}"
                            style="width: auto; margin: 0; cursor: pointer;">
                        <span>${key}</span>
                    </label>
                    <div style="font-size: var(--lumo-font-size-xs); color: var(--lumo-contrast-60pct); margin-top: var(--lumo-space-xs); margin-left: calc(var(--lumo-space-m) + 4px);">
                        Value: ${value ? 'true' : 'false'}
                    </div>
                    <vaadin-button
                        theme="tertiary error small"
                        @click="${() => this._removeField(key)}"
                        style="margin-top: var(--lumo-space-xs);">
                        <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                        Remove Field
                    </vaadin-button>
                </div>
            `;
        } else {
            // String, number
            const fieldType = this._fieldTypes[key];
            const isTextarea = fieldType === 'textarea' || (typeof value === 'string' && value.length > 100);
            return html`
                <div class="field-group">
                    <label class="field-label">${key}</label>
                    ${isTextarea
                        ? html`
                            <vaadin-text-area
                                value="${String(value)}"
                                rows="4"
                                @input="${(e) => this._updateField(key, e.target.value)}">
                            </vaadin-text-area>
                        `
                        : html`
                            <vaadin-text-field
                                type="${typeof value === 'number' ? 'number' : 'text'}"
                                value="${String(value)}"
                                @input="${(e) => this._updateField(key, e.target.value)}">
                            </vaadin-text-field>
                        `
                    }
                    <vaadin-button
                        theme="tertiary error small"
                        @click="${() => this._removeField(key)}"
                        style="margin-top: var(--lumo-space-xs);">
                        <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                        Remove Field
                    </vaadin-button>
                </div>
            `;
        }
    }

    _updateField(key, value, isJSON = false) {
        if (isJSON) {
            try {
                this._fields[key] = JSON.parse(value);
            } catch (e) {
                // Invalid JSON, keep as string
                this._fields[key] = value;
            }
        } else if (typeof value === 'boolean') {
            // Already a boolean, use it directly
            this._fields[key] = value;
        } else if (typeof value === 'string') {
            // Try to infer type from string
            const trimmed = value.trim();
            if (trimmed === 'true') {
                this._fields[key] = true;
            } else if (trimmed === 'false') {
                this._fields[key] = false;
            } else if (trimmed === 'null' || trimmed === '') {
                this._fields[key] = null;
            } else if (/^-?\d+$/.test(trimmed)) {
                this._fields[key] = parseInt(trimmed, 10);
            } else if (/^-?\d*\.\d+$/.test(trimmed)) {
                this._fields[key] = parseFloat(trimmed);
            } else {
                this._fields[key] = value;
            }
        } else {
            // Number or other type, use as-is
            this._fields[key] = value;
        }
        this._notifyChange();
    }

    _updateArrayItem(key, index, value) {
        if (!this._fields[key]) {
            this._fields[key] = [];
        }
        this._fields[key][index] = value;
        this._notifyChange();
    }

    _addArrayItem(key) {
        if (!this._fields[key]) {
            this._fields[key] = [];
        }
        this._fields[key].push('');
        this._notifyChange();
        this.requestUpdate();
    }

    _removeArrayItem(key, index) {
        if (this._fields[key] && Array.isArray(this._fields[key])) {
            this._fields[key].splice(index, 1);
            if (this._fields[key].length === 0) {
                delete this._fields[key];
            }
            this._notifyChange();
            this.requestUpdate();
        }
    }

    _removeField(key) {
        delete this._fields[key];
        delete this._fieldTypes[key];
        this._notifyChange();
        this.requestUpdate();
    }

    _onDateInput(e) {
        this.date = e.target.value;
        this._notifyChange();
    }

    _onNewFieldKeyInput(e) {
        this._newFieldKey = e.target.value;
    }

    _onNewFieldTypeChange(e) {
        this._newFieldType = e.target.value;
    }

    _onNewFieldKeyDown(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            this._addField();
        }
    }

    _addField() {
        const key = this._newFieldKey.trim();
        if (key && !this._fields[key]) {
            // Set default value based on field type
            let defaultValue;
            switch (this._newFieldType) {
                case 'number':
                    defaultValue = 0;
                    break;
                case 'boolean':
                    defaultValue = false;
                    break;
                case 'array':
                    defaultValue = [];
                    break;
                case 'textarea':
                    defaultValue = '';
                    break;
                default: // 'text'
                    defaultValue = '';
                    break;
            }
            this._fields[key] = defaultValue;
            // Store the field type
            this._fieldTypes[key] = this._newFieldType;
            this._newFieldKey = '';
            this._newFieldType = 'text'; // Reset to default
            this._notifyChange();
            this.requestUpdate();
        }
    }

    _notifyChange() {
        this.dispatchEvent(new CustomEvent('frontmatter-changed', {
            bubbles: true,
            composed: true,
            detail: { frontmatter: { ...this._fields } }
        }));
    }

    getFrontmatter() {
        return { ...this._fields };
    }

    getDate() {
        return this.date || '';
    }

    getFieldTypes() {
        return { ...this._fieldTypes };
    }
}

customElements.define('qwc-frontmatter-panel', FrontmatterPanel);

