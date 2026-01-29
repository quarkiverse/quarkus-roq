import { LitElement, html, css, render } from 'lit';
import '@vaadin/dialog';
import '@vaadin/text-field';
import '@vaadin/button';
import '@vaadin/icon';

/**
 * A reusable prompt/confirm dialog component based on Vaadin Dialog
 * Usage:
 *   // For prompt (with text input):
 *   showPrompt('Enter URL:', 'https://example.com').then(value => {
 *     if (value !== null) { /* User confirmed with value * / }
 *   });
 *    
 *   // For confirm (yes/no):
 *   showConfirm('Are you sure?').then(confirmed => {
 *     if (confirmed) { /* User confirmed * / }
 *   });
 */
export class PromptDialog extends LitElement {

  static properties = {
    _open: { state: true },
    _message: { state: true },
    _defaultValue: { state: true },
    _resolve: { state: true },
    _inputValue: { state: true },
    _tempInputValue: { state: true },
    _mode: { state: true },  // 'prompt' or 'confirm'
    _confirmButtonText: { state: true },
    _cancelButtonText: { state: true },
    _confirmTheme: { state: true }
  };

  static styles = css`
        vaadin-dialog {
            --vaadin-dialog-content-padding: var(--lumo-space-m);
        }
        .dialog-content {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-m);
            min-width: 300px;
        }
        .dialog-message {
            font-size: var(--lumo-font-size-m);
            color: var(--lumo-body-text-color);
            margin: 0;
        }
        .dialog-actions {
            display: flex;
            justify-content: flex-end;
            gap: var(--lumo-space-s);
            margin-top: var(--lumo-space-s);
        }
        vaadin-text-field {
            width: 100%;
        }
    `;

  constructor() {
    super();
    this._open = false;
    this._message = '';
    this._defaultValue = '';
    this._inputValue = '';
    this._tempInputValue = '';
    this._resolve = null;
    this._mode = 'prompt';
    this._confirmButtonText = 'OK';
    this._cancelButtonText = 'Cancel';
    this._confirmTheme = 'primary';
  }

  /**
   * Shows a prompt dialog and returns a Promise that resolves with the user's input
   * @param {string} message - The message to display
   * @param {string} defaultValue - The default value for the input field
   * @returns {Promise<string|null>} - Resolves with the input value if confirmed, null if cancelled
   */
  prompt(message, defaultValue = '') {
    return new Promise((resolve) => {
      this._mode = 'prompt';
      this._message = message;
      this._defaultValue = defaultValue;
      this._inputValue = defaultValue;
      this._tempInputValue = defaultValue;
      this._confirmButtonText = 'OK';
      this._cancelButtonText = 'Cancel';
      this._confirmTheme = 'primary';
      this._resolve = resolve;
      this._open = true;
      this.requestUpdate();
    });
  }

  /**
   * Shows a confirm dialog and returns a Promise that resolves with true/false
   * @param {string} message - The message to display
   * @param {Object} options - Optional configuration
   * @param {string} options.confirmText - Text for confirm button (default: 'Confirm')
   * @param {string} options.cancelText - Text for cancel button (default: 'Cancel')
   * @param {string} options.theme - Theme for confirm button: 'primary', 'error', 'success' (default: 'primary')
   * @returns {Promise<boolean>} - Resolves with true if confirmed, false if cancelled
   */
  confirm(message, options = {}) {
    return new Promise((resolve) => {
      this._mode = 'confirm';
      this._message = message;
      this._confirmButtonText = options.confirmText || 'Confirm';
      this._cancelButtonText = options.cancelText || 'Cancel';
      this._confirmTheme = options.theme || 'primary';
      this._resolve = resolve;
      this._open = true;
      this.requestUpdate();
    });
  }

  _closeDialog(confirmed) {
    if (this._resolve) {
      if (this._mode === 'confirm') {
        this._resolve(confirmed);
      } else {
        if (confirmed) {
          // Use the temporary input value (what user typed)
          this._resolve(this._tempInputValue || null);
          // Commit the value to _inputValue
          this._inputValue = this._tempInputValue;
        } else {
          // Resolve with null to indicate cancellation
          this._resolve(null);
          // Restore to original default value, discarding any changes
          this._tempInputValue = this._defaultValue;
          this._inputValue = this._defaultValue;
        }
      }
      this._resolve = null;
    }
    this._open = false;
  }

  _handleConfirm() {
    this._closeDialog(true);
  }

  _handleCancel() {
    this._closeDialog(false);
  }


  _dialogRenderer = (root) => {
    // Clear previous content and remove old listeners
    if (root) {
      // Remove any existing event listeners
      const oldContent = root.querySelector('.dialog-content');
      if (oldContent) {
        root.removeEventListener('click', this._boundHandleClick);
        root.removeEventListener('value-changed', this._boundHandleValueChanged);
        root.removeEventListener('keydown', this._boundHandleKeyDown);
      }

      // Bind handlers to preserve 'this' context
      this._boundHandleClick = this._handleClick.bind(this);
      this._boundHandleValueChanged = this._handleValueChanged.bind(this);
      this._boundHandleKeyDown = this._handleKeyDown.bind(this);

      const isPrompt = this._mode === 'prompt';

      render(html`
                <div class="dialog-content">
                    <p class="dialog-message">${this._message}</p>
                    ${isPrompt ? html`
                        <vaadin-text-field
                            id="prompt-input"
                            .value="${this._tempInputValue}"
                            placeholder="Enter value...">
                        </vaadin-text-field>
                    ` : ''}
                    <div class="dialog-actions">
                        <vaadin-button
                            id="cancel-button"
                            theme="tertiary">
                            ${this._cancelButtonText}
                        </vaadin-button>
                        <vaadin-button
                            id="ok-button"
                            theme="${this._confirmTheme}">
                            ${this._confirmButtonText}
                        </vaadin-button>
                    </div>
                </div>
            `, root);

      // Use event delegation on root
      root.addEventListener('click', this._boundHandleClick);
      root.addEventListener('value-changed', this._boundHandleValueChanged, true);
      root.addEventListener('keydown', this._boundHandleKeyDown, true);

      // Also attach direct listeners as backup
      setTimeout(() => {
        const input = root.querySelector('#prompt-input');
        const cancelButton = root.querySelector('#cancel-button');
        const okButton = root.querySelector('#ok-button');

        if (input) {
          // Direct listener for value changes - update temporary value only
          input.addEventListener('value-changed', (e) => {
            this._tempInputValue = e.detail.value || '';
          });

          // Direct listener for keyboard events on the input element
          const inputEl = input.inputElement;
          if (inputEl) {
            inputEl.addEventListener('keydown', (e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                this._handleConfirm();
              } else if (e.key === 'Escape') {
                e.preventDefault();
                this._handleCancel();
              }
            });
          }

          // Focus and select text
          input.focus();
          if (inputEl) {
            inputEl.select();
          }
        } else {
          // For confirm dialogs, focus the confirm button
          okButton?.focus();
        }

        if (cancelButton) {
          cancelButton.addEventListener('click', () => {
            this._handleCancel();
          });
        }

        if (okButton) {
          okButton.addEventListener('click', () => {
            this._handleConfirm();
          });
        }
      }, 100);
    }
  };

  _handleClick(e) {
    const target = e.target.closest('vaadin-button');
    if (!target) return;

    const id = target.id;
    if (id === 'ok-button') {
      this._handleConfirm();
    } else if (id === 'cancel-button') {
      this._handleCancel();
    }
  }

  _handleValueChanged(e) {
    // Handle value-changed events from vaadin-text-field - update temporary value only
    if (e.target && e.target.id === 'prompt-input') {
      this._tempInputValue = e.detail.value || '';
    }
  }

  _handleKeyDown(e) {
    // Only handle keyboard events from the input field
    if (e.target && (e.target.id === 'prompt-input' || e.target.closest('#prompt-input'))) {
      if (e.key === 'Enter') {
        e.preventDefault();
        this._handleConfirm();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        this._handleCancel();
      }
    }
  }

  render() {
    return html`
            <vaadin-dialog
                .opened="${this._open}"
                @opened-changed="${(e) => {
        if (!e.detail.value && this._resolve) {
          // Dialog was closed without clicking buttons
          this._handleCancel();
        }
      }}"
                .noCloseOnOutsideClick="${true}"
                .noCloseOnEsc="${false}"
                .renderer="${this._dialogRenderer}">
            </vaadin-dialog>
        `;
  }
}

customElements.define('qwc-prompt-dialog', PromptDialog);

/**
 * Helper functions to show dialogs
 * Creates a singleton dialog instance and reuses it
 */
let dialogInstance = null;

function getDialogInstance() {
  if (!dialogInstance) {
    dialogInstance = document.createElement('qwc-prompt-dialog');
    document.body.appendChild(dialogInstance);
  }
  return dialogInstance;
}

/**
 * Show a prompt dialog with text input
 * @param {string} message - The message to display
 * @param {string} defaultValue - Default value for the input
 * @returns {Promise<string|null>} - User input or null if cancelled
 */
export function showPrompt(message, defaultValue = '') {
  return getDialogInstance().prompt(message, defaultValue);
}

/**
 * Show a confirm dialog
 * @param {string} message - The message to display
 * @param {Object} options - Optional configuration
 * @param {string} options.confirmText - Text for confirm button (default: 'Confirm')
 * @param {string} options.cancelText - Text for cancel button (default: 'Cancel')
 * @param {string} options.theme - Theme for confirm button: 'primary', 'error', 'success' (default: 'primary')
 * @returns {Promise<boolean>} - true if confirmed, false if cancelled
 */
export function showConfirm(message, options = {}) {
  return getDialogInstance().confirm(message, options);
}
