/**
 * Bubble menu component for TipTap editor
 * Lit component that appears when text is selected
 */

import { LitElement, css, html } from 'lit';
import { editorContext } from './editor-context.js';
import { ContextConsumer } from '../bundle.js';
import { showPrompt } from './prompt-dialog.js';
import './heading-dropdown.js';

export class BubbleMenu extends LitElement {
    static properties = {
        _isInList: { state: true },
    };

    constructor() {
        super();
        this._isInList = false;
        this._editorContext = null;
        this._updateFrame = null;
        this._lastUpdateTime = 0;
        this._updateThrottle = 50; // Update at most every 50ms
        
        this._editorConsumer = new ContextConsumer(this, {
            context: editorContext,
            subscribe: true,
            callback: () => {
                // When editor context changes, set up update mechanism
                this._setupEditorUpdates();
            }
        });
    }

    static styles = css`
        :host {
            display: block;
        }
        .tiptap-menu {
            display: flex;
            gap: var(--lumo-space-xs);
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-xs);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
            margin: 0;
        }
        .tiptap-menu-button {
            font-size: var(--lumo-font-size-xxs);
        }
        .tiptap-menu-button:hover {
            background: var(--lumo-contrast-10pct);
        }
        .tiptap-menu-button.is-active {
            background: var(--lumo-primary-color-10pct);
            color: var(--lumo-body-text-color);
        }
        .tiptap-menu-button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .tiptap-menu-separator {
            width: 1px;
            background: var(--lumo-contrast-20pct);
            margin: var(--lumo-space-xs) 0;
        }
        .tiptap-menu-separator[data-show-on-list] {
            display: none;
        }
        .tiptap-menu-separator[data-show-on-list].show {
            display: block;
        }
        .tiptap-menu-button[data-show-on-list] {
            display: none;
        }
        .tiptap-menu-button[data-show-on-list].show {
            display: flex;
        }
    `;

    get editor() {
        return this._editorConsumer.value?.editor || null;
    }

    get editorElement() {
        return this._editorConsumer.value?.editorElement || null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._setupEditorUpdates();
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this._cleanupEditorUpdates();
    }

    firstUpdated() {
        // Attach click listener
        this.addEventListener('click', this._handleClick.bind(this));
        this._setupEditorUpdates();
    }

    updated(changedProperties) {
        super.updated(changedProperties);
        // Note: Button states are updated via the animation frame loop in _setupEditorUpdates()
        // to avoid infinite update loops
    }

    _setupEditorUpdates() {
        this._cleanupEditorUpdates();
        
        if (!this.editor) return;
        
        // Set up throttled updates to check editor state when component is visible
        // This ensures the menu stays in sync with editor state
        const updateLoop = () => {
            if (this.editor && this.isConnected) {
                const now = Date.now();
                // Only update if component is visible and throttle time has passed
                const isVisible = this.offsetParent !== null && 
                                 this.style.display !== 'none' &&
                                 window.getComputedStyle(this).display !== 'none';
                
                if (isVisible && (now - this._lastUpdateTime) >= this._updateThrottle) {
                    this._updateButtonStates();
                    this._lastUpdateTime = now;
                }
                this._updateFrame = requestAnimationFrame(updateLoop);
            } else {
                this._cleanupEditorUpdates();
            }
        };
        
        this._updateFrame = requestAnimationFrame(updateLoop);
    }

    _cleanupEditorUpdates() {
        if (this._updateFrame) {
            cancelAnimationFrame(this._updateFrame);
            this._updateFrame = null;
        }
    }

    render() {
        return html`
            <div class="tiptap-menu">
                <qwc-heading-dropdown mode="toggle"></qwc-heading-dropdown>
                <div class="tiptap-menu-separator"></div>
                <vaadin-button theme="icon" class="tiptap-menu-button ${this._isCommandActive('bold') ? 'is-active' : ''}" 
                    data-command="bold" 
                    title="Bold">
                       <vaadin-icon icon="font-awesome-solid:bold"></vaadin-icon>
                </vaadin-button>
                <vaadin-button theme="icon"
                    class="tiptap-menu-button  ${this._isCommandActive('italic') ? 'is-active' : ''}" 
                    data-command="italic" 
                    title="Italic">
                       <vaadin-icon icon="font-awesome-solid:italic"></vaadin-icon>
                </vaadin-button>
                <div class="tiptap-menu-separator"></div>
                <vaadin-button class="tiptap-menu-button" theme="icon" data-command="link" title="Link">
                       <vaadin-icon icon="font-awesome-solid:link"></vaadin-icon>
                </vaadin-button>
                <vaadin-button class="tiptap-menu-button" theme="icon"data-command="image" title="Image">
                       <vaadin-icon icon="font-awesome-solid:image"></vaadin-icon>
                </vaadin-button>
                <div class="tiptap-menu-separator ${this._isInList ? 'show' : ''}" data-show-on-list></div>
                <vaadin-button 
                    class="tiptap-menu-button ${this._isCommandActive('bulletList') ? 'is-active' : ''} ${this._isInList ? 'show' : ''}" 
                    data-command="bulletList" 
                    data-show-on-list 
                    theme="icon"
                    title="Bullet List">
                       <vaadin-icon icon="font-awesome-solid:list"></vaadin-icon>
                </vaadin-button>
                <vaadin-button 
                    class="tiptap-menu-button ${this._isCommandActive('orderedList') ? 'is-active' : ''} ${this._isInList ? 'show' : ''}" 
                    data-command="orderedList" 
                    data-show-on-list 
                    theme="icon"
                    title="Ordered List">
                       <vaadin-icon icon="font-awesome-solid:list-ol"></vaadin-icon>
                </vaadin-button>
            </div>
        `;
    }

    _isCommandActive(command) {
        if (!this.editor) return false;
        
        switch (command) {
            case 'bold':
                return this.editor.isActive('bold');
            case 'italic':
                return this.editor.isActive('italic');
            case 'bulletList':
                return this.editor.isActive('bulletList');
            case 'orderedList':
                return this.editor.isActive('orderedList');
            default:
                return false;
        }
    }

    _updateButtonStates() {
        if (!this.editor) return;
        
        // Update list visibility state
        // Changing _isInList (a reactive state property) will automatically trigger a re-render
        this._isInList = this.editor.isActive('bulletList') || this.editor.isActive('orderedList');
    }

    _handleClick(e) {
        // Use composedPath to find the button in Shadow DOM
        const path = e.composedPath();
        const button = path.find(el => el.classList && el.classList.contains('tiptap-menu-button'));
        if (!button || !this.editor) return;
        
        const command = button.dataset.command;
        
        if (command === 'bold') {
            this.editor.chain().focus().toggleBold().run();
        } else if (command === 'italic') {
            this.editor.chain().focus().toggleItalic().run();
        } else if (command === 'link') {
            // Get current link URL if selection is inside a link
            const currentLinkAttrs = this.editor.getAttributes('link');
            const currentUrl = currentLinkAttrs.href || '';
            
            showPrompt('Enter URL:', currentUrl).then(url => {
                if (url) {
                    this.editor.chain().focus().setLink({ href: url }).run();
                } else if (currentUrl) {
                    // If user cleared the URL, remove the link
                    this.editor.chain().focus().unsetLink().run();
                }
            });
        } else if (command === 'image') {
            showPrompt('Enter image URL:', '').then(url => {
                if (url) {
                    this.editor.chain().focus().setImage({ src: url }).run();
                }
            });
        } else if (command === 'bulletList') {
            this.editor.chain().focus().toggleBulletList().run();
        } else if (command === 'orderedList') {
            this.editor.chain().focus().toggleOrderedList().run();
        }
        
        // Update button states after command and force re-render
        this._updateButtonStates();
        this.requestUpdate();
    }
}

customElements.define('qwc-bubble-menu', BubbleMenu);

