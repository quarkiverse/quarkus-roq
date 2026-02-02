/**
 * Bubble menu component for TipTap editor
 * Lit component that appears when text is selected
 */

import { LitElement, css, html } from 'lit';
import { editorContext } from './editor-context.js';
import { ContextConsumer } from '../../bundle.js';
import { showPrompt } from '../prompt-dialog.js';
import './heading-dropdown.js';
import {renderImageForm} from "./extensions/image.js";

export class BubbleMenu extends LitElement {
    static properties = {
    };

    constructor() {
        super();

        this._editorConsumer = new ContextConsumer(this, {
            context: editorContext,
            subscribe: true,
            callback: () => {
                this._bindEditor(this.editor);
                // When editor context changes, set up update mechanism
            }
        });
    }


    disconnectedCallback() {
        super.disconnectedCallback();
        this._unsubscribeEditor?.();
        this._unsubscribeEditor = undefined;
    }



    _unsubscribeEditor = () => {}

    _bindEditor(editor) {
        // cleanup previous subscriptions
        this._unsubscribeEditor?.();
        this._unsubscribeEditor = undefined;

        if (!editor) {
            this.requestUpdate();
            return;
        }

        const onChange = () => this.requestUpdate();

        // Mirror what useEditorState does: rerender on transactions [1](https://tiptap.dev/docs/examples/advanced/react-performance)
        editor.on('transaction', onChange);
        // Optional but usually needed for active marks based on selection movement
        editor.on('selectionUpdate', onChange);

        this._unsubscribeEditor = () => {
            editor.off('transaction', onChange);
            editor.off('selectionUpdate', onChange);
        };

        // initial sync
        this.requestUpdate();
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


    render() {
        return html`
            <div class="tiptap-menu" @click=${this._handleClick}>
               ${this._isCommandActive('image') ? this._renderImageMenu() : this._renderTextMenu()}
            </div>
        `;
    }

    _renderImageMenu() {
        return html`
          <vaadin-button class="tiptap-menu-button" theme="icon" data-command="image" title="Image">
            <vaadin-icon icon="font-awesome-solid:pen-to-square"></vaadin-icon>
          </vaadin-button>
        `;
    }


    _renderTextMenu() {
        return html`
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
          <vaadin-button class="tiptap-menu-button" theme="icon" data-command="image" title="Image">
            <vaadin-icon icon="font-awesome-solid:image"></vaadin-icon>
          </vaadin-button>
          <div class="tiptap-menu-separator ${this._isInList() ? 'show' : ''}" data-show-on-list></div>
          <vaadin-button
            class="tiptap-menu-button ${this._isCommandActive('bulletList') ? 'is-active' : ''} ${this._isInList() ? 'show' : ''}"
            data-command="bulletList"
            data-show-on-list
            theme="icon"
            title="Bullet List">
            <vaadin-icon icon="font-awesome-solid:list"></vaadin-icon>
          </vaadin-button>
          <vaadin-button
            class="tiptap-menu-button ${this._isCommandActive('orderedList') ? 'is-active' : ''} ${this._isInList() ? 'show' : ''}"
            data-command="orderedList"
            data-show-on-list
            theme="icon"
            title="Ordered List">
            <vaadin-icon icon="font-awesome-solid:list-ol"></vaadin-icon>
          </vaadin-button>
        `;
    }

    _isCommandActive(command) {
        if (!this.editor) return false;
        return this.editor.isActive(command);
    }


    _isInList() {
        if (!this.editor) return false;
        return this.editor.isActive('bulletList') || this.editor.isActive('orderedList');
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
            // Get current link URL if selection is inside a link
            const currentImageAttrs = this.editor.getAttributes('image');
            const src = currentImageAttrs.src || '';
            const title = currentImageAttrs.title || '';
            const alt = currentImageAttrs.alt || '';
            showPrompt('Image', { alt, title, src}, renderImageForm).then(({ src, title, alt}) => {
                if (src) {
                    this.editor.chain().focus().setImage({ alt, src, title }).run();
                }
            });
        } else if (command === 'bulletList') {
            this.editor.chain().focus().toggleBulletList().run();
        } else if (command === 'orderedList') {
            this.editor.chain().focus().toggleOrderedList().run();
        }
        
        // Update button states after command and force re-render
        this.requestUpdate();
    }



}

customElements.define('qwc-bubble-menu', BubbleMenu);

