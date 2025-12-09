import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import './post-card.js';

export class PostsList extends LitElement {
    
    static properties = {
        posts: { type: Array }
    };

    static styles = css`
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: var(--lumo-space-m);
        }
        .posts-list {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-l);
        }
    `;

    render() {
        const posts = this.posts || [];
        
        return html`
            <div class="toolbar">
                <h2>Posts</h2>
                <vaadin-button 
                    theme="primary" 
                    @click="${this._addNewPost}">
                    <vaadin-icon icon="font-awesome-solid:plus" slot="prefix"></vaadin-icon>
                    Add New Post
                </vaadin-button>
            </div>
            ${posts.length > 0 
                ? html`
                    <div class="posts-list">
                        ${posts.map(post => html`
                            <qwc-post-card 
                                .post="${post}"
                                @post-clicked="${this._onPostClicked}">
                            </qwc-post-card>
                        `)}
                    </div>
                `
                : html`<p>No posts found. Click "Add New Post" to create your first post.</p>`
            }
        `;
    }

    _addNewPost() {
        // Dispatch event to parent component
        this.dispatchEvent(new CustomEvent('add-new-post', {
            bubbles: true,
            composed: true
        }));
    }

    _onPostClicked(e) {
        // Forward the event to parent component
        this.dispatchEvent(new CustomEvent('post-clicked', {
            bubbles: true,
            composed: true,
            detail: e.detail
        }));
    }
}

customElements.define('qwc-posts-list', PostsList);

