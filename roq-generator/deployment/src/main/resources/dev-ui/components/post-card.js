import { LitElement, html, css } from 'lit';
import { PostUtils } from './post-utils.js';

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
        }
        .post-card:hover {
            border-color: var(--lumo-primary-color);
            box-shadow: 0 2px 8px var(--lumo-primary-color-10pct);
            transform: translateY(-1px);
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
        .post-file-type {
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
    `;

    render() {
        if (!this.post) {
            return html``;
        }

        const title = PostUtils.extractPostTitle(this.post);
        const postUrl = this.post.path || '#';
        const fileType = PostUtils.extractFileType(this.post);
        const path = this.post.path || '';

        return html`
            <article class="post-card" @click="${this._onCardClick}">
                <div class="post-content">
                    <div class="post-header">
                        <h2 class="post-title">
                            <a href="${postUrl}" @click="${this._onPostClick}">${title}</a>
                        </h2>
                    </div>
                    <div class="post-footer">
                        ${fileType 
                            ? html`<span class="post-file-type">${fileType}</span>`
                            : html``
                        }
                        ${path 
                            ? html`<p class="post-path" title="${path}">${path}</p>`
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
}

customElements.define('qwc-post-card', PostCard);

