import { LitElement, html, css } from 'lit';
import { PostUtils } from './post-utils.js';

export class PostCard extends LitElement {
    
    static properties = {
        post: { type: Object }
    };

    static styles = css`
        .post-card {
            display: flex;
            background: white;
            border-radius: var(--lumo-border-radius-m);
            overflow: hidden;
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
            transition: box-shadow 0.2s;
        }
        .post-card:hover {
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
        }
        .post-image {
            width: 200px;
            min-width: 200px;
            height: 150px;
            background: var(--lumo-contrast-10pct);
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--lumo-contrast-50pct);
            font-size: var(--lumo-font-size-s);
            overflow: hidden;
        }
        .post-image img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
        .post-content {
            flex: 1;
            padding: var(--lumo-space-m);
            display: flex;
            flex-direction: column;
            justify-content: space-between;
        }
        .post-title {
            font-size: 1.5rem;
            font-weight: 600;
            color: #1e3a8a;
            margin: 0 0 var(--lumo-space-xs) 0;
            line-height: 1.3;
        }
        .post-title a {
            color: inherit;
            text-decoration: none;
        }
        .post-title a:hover {
            text-decoration: underline;
        }
        .post-description {
            font-size: var(--lumo-font-size-m);
            color: var(--lumo-contrast-70pct);
            margin: 0 0 var(--lumo-space-m) 0;
            line-height: 1.5;
        }
        .post-meta {
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-contrast-60pct);
            margin: 0;
            font-weight: 400;
        }
    `;

    render() {
        if (!this.post) {
            return html``;
        }

        const title = PostUtils.extractPostTitle(this.post);
        const description = PostUtils.extractPostDescription(this.post);
        const date = PostUtils.extractPostDate(this.post);
        const readTime = PostUtils.extractReadTime(this.post);
        const imageUrl = PostUtils.extractImageUrl(this.post);
        const postUrl = this.post.path || '#';

        return html`
            <article class="post-card">
                <div class="post-image">
                    ${imageUrl 
                        ? html`<img src="${imageUrl}" alt="${title}" />`
                        : html`<span>No image</span>`
                    }
                </div>
                <div class="post-content">
                    <div>
                        <h2 class="post-title">
                            <a href="${postUrl}" @click="${this._onPostClick}">${title}</a>
                        </h2>
                        ${description 
                            ? html`<p class="post-description">${description}</p>`
                            : html`<p class="post-description">No description available</p>`
                        }
                    </div>
                    <p class="post-meta">
                        ${date ? html`${date} — ` : ''}${readTime}
                    </p>
                </div>
            </article>
        `;
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

