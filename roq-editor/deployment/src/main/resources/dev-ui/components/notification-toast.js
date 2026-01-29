import '@vaadin/notification';
import '@vaadin/icon';
import { html, render } from 'lit';

/**
 * Show a notification toast using Vaadin Notification
 * 
 * Usage:
 *   import { showNotification } from './components/notification-toast.js';
 *   showNotification('Error message here', 'error');
 *   showNotification('Success!', 'success');
 * 
 * @param {string} message - The message to display
 * @param {'error' | 'success' | 'warning' | 'primary' | 'contrast'} theme - The theme/type of notification
 * @param {number} duration - Auto-dismiss duration in ms (0 for no auto-dismiss)
 */
export function showNotification(message, theme = 'error', duration = 5000) {
  const notification = document.createElement('vaadin-notification');
  notification.position = 'bottom-end';
  notification.duration = duration;
  notification.setAttribute('theme', theme);

  notification.renderer = (root) => {
    render(html`
            <div style="display: flex; align-items: center; gap: var(--lumo-space-s);">
                <span>${message}</span>
                <vaadin-button 
                    theme="tertiary-inline" 
                    @click="${() => notification.close()}"
                    style="margin-left: auto;">
                    <vaadin-icon icon="lumo:cross"></vaadin-icon>
                </vaadin-button>
            </div>
        `, root);
  };

  document.body.appendChild(notification);
  notification.opened = true;

  // Clean up after closing
  notification.addEventListener('opened-changed', (e) => {
    if (!e.detail.value) {
      notification.remove();
    }
  });
}
