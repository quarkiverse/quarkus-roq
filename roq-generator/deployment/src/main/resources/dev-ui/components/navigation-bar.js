import { LitElement, html, css } from 'lit';
import '@vaadin/tabs';

export class NavigationBar extends LitElement {
    
    static properties = {
        activeTab: { type: Number }
    };

    static styles = css`
        .navigation {
            border-bottom: 1px solid var(--lumo-contrast-20pct);
        }
    `;

    render() {
        return html`
            <div class="navigation">
                <vaadin-tabs 
                    .selected="${this.activeTab}" 
                    @selected-changed="${this._onTabChanged}">
                    <vaadin-tab>Posts</vaadin-tab>
                    <vaadin-tab>Pages</vaadin-tab>
                    <vaadin-tab>Tags</vaadin-tab>
                </vaadin-tabs>
            </div>
        `;
    }

    _onTabChanged(e) {
        // Dispatch event to parent component
        this.dispatchEvent(new CustomEvent('tab-changed', {
            detail: { selectedTab: e.detail.value },
            bubbles: true,
            composed: true
        }));
    }
}

customElements.define('qwc-navigation-bar', NavigationBar);

