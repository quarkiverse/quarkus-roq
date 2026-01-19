import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';

export class PostCard extends LitElement {
    
    static properties = {
        post: { type: Object }
    };

    static styles = css`
        .post-card {
            display: flex;
            background: var(--lumo-base-color);
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            overflow: hidden;
            transition: all 0.2s ease;
            cursor: pointer;
            position: relative;
        }
        .post-card:hover {
            border-color: var(--lumo-primary-color);
            box-shadow: 0 2px 8px var(--lumo-primary-color-10pct);
            transform: translateY(-1px);
        }
        .delete-button {
            position: absolute;
            top: var(--lumo-space-s);
            right: var(--lumo-space-s);
            opacity: 0;
            transition: opacity 0.2s ease;
        }
        .post-card:hover .delete-button {
            opacity: 1;
        }
        vaadin-button.delete-button {
            --vaadin-button-background: var(--lumo-error-color-10pct);
            --vaadin-button-color: var(--lumo-error-color);
        }
        vaadin-button.delete-button:hover {
            --vaadin-button-background: var(--lumo-error-color);
            --vaadin-button-color: var(--lumo-error-contrast-color);
        }
        .post-content {
            flex: 1;
            padding: var(--lumo-space-m);
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            min-width: 0;
        }
        .post-header {
            margin-bottom: var(--lumo-space-s);
        }
        .post-title {
            font-size: var(--lumo-font-size-l);
            font-weight: 600;
            color: var(--lumo-body-text-color);
            margin: 0 0 var(--lumo-space-xs) 0;
            line-height: 1.4;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .post-title a {
            color: inherit;
            text-decoration: none;
        }
        .post-title a:hover {
            color: var(--lumo-primary-color);
        }
        .post-footer {
            display: flex;
            flex-wrap: wrap;
            gap: var(--lumo-space-s);
            align-items: center;
            margin-top: auto;
            padding-top: var(--lumo-space-s);
            border-top: 1px solid var(--lumo-contrast-10pct);
        }
        .post-path {
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-contrast-50pct);
            font-family: var(--lumo-font-family-mono);
            margin: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            flex: 1;
            min-width: 0;
        }
        .post-markup {
            display: inline-flex;
            align-items: center;
            padding: 2px var(--lumo-space-xs);
            background: var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-s);
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-contrast-70pct);
            font-weight: 500;
            text-transform: uppercase;
        }
        .post-meta {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
            font-size: var(--lumo-font-size-xs);
            color: var(--lumo-contrast-60pct);
            margin-top: var(--lumo-space-xs);
        }
        .post-filename {
            font-family: var(--lumo-font-family-mono);
            color: var(--lumo-contrast-50pct);
        }
        .post-date {
            color: var(--lumo-contrast-60pct);
        }
        .meta-separator {
            color: var(--lumo-contrast-30pct);
        }
    `;

    render() {
        if (!this.post) {
            return html``;
        }

        const title = this.post.title || '';
        const postUrl = this.post.path || '#';
        const markup = this.post.markup;
        const description = this.post.description || '';
        const filename = this.post.filename || '';
        const date = this.post.date || '';

        return html`
            <article class="post-card" @click="${this._onCardClick}">
                <vaadin-button 
                    class="delete-button" 
                    theme="tertiary error icon"
                    @click="${this._onDeleteClick}"
                    title="Delete post">
                    <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                </vaadin-button>
                <div class="post-content">
                    <div class="post-header">
                        <h2 class="post-title">
                            <a href="${postUrl}" @click="${this._onPostClick}">${title}</a>
                        </h2>
                        <div class="post-meta">
                            ${filename 
                                ? html`<span class="post-filename">${filename}</span>`
                                : html``
                            }
                            ${filename && date 
                                ? html`<span class="meta-separator">•</span>`
                                : html``
                            }
                            ${date 
                                ? html`<span class="post-date">${date}</span>`
                                : html``
                            }
                        </div>
                    </div>
                    <div class="post-footer">
                        ${markup 
                            ? html`<span class="post-markup">${markup.toUpperCase()}</span>`
                            : html``
                        }
                        ${description 
                            ? html`<p class="post-path" title="${description}">${description}</p>`
                            : html``
                        }
                    </div>
                </div>
            </article>
        `;
    }

    _onCardClick(e) {
        // Don't trigger if clicking on a link
        if (e.target.tagName === 'A') {
            return;
        }
        this._onPostClick(e);
    }

    _onPostClick(e) {
        e.preventDefault();
        this.dispatchEvent(new CustomEvent('post-clicked', {
            bubbles: true,
            composed: true,
            detail: { post: this.post }
        }));
    }

    _onDeleteClick(e) {
        e.stopPropagation(); // Prevent triggering the card click
        e.preventDefault(); // Prevent any default behavior
        this.dispatchEvent(new CustomEvent('post-delete', {
            bubbles: true, // Need to bubble to reach posts-list
            composed: true,
            detail: { post: this.post }
        }));
    }
}

customElements.define('qwc-post-card', PostCard);

