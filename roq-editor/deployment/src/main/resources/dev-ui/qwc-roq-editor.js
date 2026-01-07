import { LitElement, html, css} from 'lit';
import { JsonRpc } from 'jsonrpc';
import './components/navigation-bar.js';
import './components/posts-list.js';
import './components/pages-list.js';
import './components/tags-list.js';
import './components/editor.js';
import { showPrompt } from './components/prompt-dialog.js';

export class QwcRoqEditor extends LitElement {

    jsonRpc = new JsonRpc(this);

    // Component style
    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            height: 100%;
        }
        .content-area {
            flex: 1;
            overflow: auto;
            padding: var(--lumo-space-m);
        }
        `;

    // Component properties
    static properties = {
        "_posts": {state: true},
        "_activeTab": {state: true},
        "_selectedPost": {state: true},
        "_fileContent": {state: true},
        "_loadingContent": {state: true}
    }

    constructor() {
        super();
        this._activeTab = 0;
        this._selectedPost = null;
        this._fileContent = null;
        this._loadingContent = false;
    }

    // Components callbacks

    /**
     * Called when displayed
     */
    connectedCallback() {
        super.connectedCallback();
        const showSidebar = location.search.includes('showSidebar');
        if (!showSidebar) {
            document.querySelector('qwc-menu').style.display = 'none';
        }

        this.jsonRpc.getPosts().then(jsonRpcResponse => {
          this._posts = [];
          jsonRpcResponse.result.forEach(c => {
              this._posts.push(c);
          });

          this.jsonRpc.getPages().then(jsonRpcResponse => {
            this._pages = [];
            jsonRpcResponse.result.forEach(c => {
                this._pages.push(c);
            });
          });
      });
    }

    /**
     * Called when it needs to render the components
     * @returns {*}
     * <qwc-navigation-bar 
                .activeTab="${this._activeTab}"
                @tab-changed="${this._onTabChanged}">
            </qwc-navigation-bar>
     */
    render() {
        return html`
            <div class="content-area">
                ${this._selectedPost && this._fileContent !== null 
                    ? html`
                        <qwc-editor 
                            .filePath="${this._selectedPost.path}"
                            .previewUrl="${this._selectedPost.url}"
                            .loading="${this._loadingContent}"
                            @close-viewer="${this._closeViewer}"
                            .content="${this._fileContent}"
                            @save-content="${this._onSaveContent}">
                        </qwc-editor>
                    `
                    : this._renderContent()
                }
            </div>
        `;
    }

    _onTabChanged(e) {
        this._activeTab = e.detail.selectedTab;
    }

    _renderContent() {
        switch(this._activeTab) {
            case 0:
                return this._renderPosts();
            case 1:
                return this._renderPages();
            case 2:
                return this._renderTags();
            default:
                return html`<span>Unknown tab</span>`;
        }
    }

    _renderPosts() {
        return html`
            <qwc-posts-list 
                .posts="${this._posts}"
                @add-new-post="${this._addNewPost}"
                @post-clicked="${this._onPostClicked}"
                @post-delete="${this._onPostDelete}">
            </qwc-posts-list>
        `;
    }

    _renderPages() {
        return html`
            <qwc-pages-list 
                .pages="${this._pages}"
                @page-clicked="${this._onPageClicked}">
            </qwc-pages-list>
        `;
    }

    _renderTags() {
        const tags = this._getTags();
        return html`
            <qwc-tags-list .tags="${tags}"></qwc-tags-list>
        `;
    }

    _getPages() {
        if (!this._pages) return [];
        return this._pages.filter(page => {
            const path = page.path || '';
            const outputPath = page.outputPath || '';
            return path && 
                   !path.includes('/posts/') && 
                   !path.includes('/posts/tag/') &&
                   !outputPath.includes('/posts/') &&
                   !outputPath.includes('/posts/tag/');
        });
    }

    _getTags() {
        if (!this._pages) return [];
        return this._pages.filter(page => {
            const path = page.path || '';
            const outputPath = page.outputPath || '';
            return (path && path.includes('/posts/tag/')) ||
                   (outputPath && outputPath.includes('/posts/tag/'));
        });
    }

    _addNewPost() {
        showPrompt('Enter post title:', '').then(title => {
            if (title) {
                this.jsonRpc.createPost({ title: title }).then(jsonRpcResponse => {
                    const result = jsonRpcResponse.result;
                    console.log('result', result);
                    if (result && !result.startsWith("Error")) {
                        const newPost = { path: result, title: title, description: '' };
                        this._onPostClicked({ detail: { post: newPost } });
                    } else {
                        alert('Error creating post: ' + result);
                    }
                }).catch(error => {
                    alert('Error creating post: ' + error.message);
                });
            }
        });
    }

    _onPostClicked(e) {
        const post = e.detail.post;
        this._selectedPost = post;
        this._loadingContent = true;
        this._fileContent = null;
        
        // Fetch file content from backend
        this.jsonRpc.getFileContent({ path: post.path }).then(jsonRpcResponse => {
            this._fileContent = jsonRpcResponse.result;
            this._loadingContent = false;
        }).catch(error => {
            this._fileContent = "Error loading file content: " + error.message;
            this._loadingContent = false;
        });
    }

    _onPageClicked(e) {
        const page = e.detail.page;
        this._selectedPost = page;
        this._loadingContent = true;
        this._fileContent = null;
        
        // Fetch file content from backend
        this.jsonRpc.getFileContent({ path: page.path }).then(jsonRpcResponse => {
            this._fileContent = jsonRpcResponse.result;
            this._loadingContent = false;
        }).catch(error => {
            this._fileContent = "Error loading file content: " + error.message;
            this._loadingContent = false;
        });
    }

    _closeViewer() {
        this._selectedPost = null;
        this._fileContent = null;
        this._loadingContent = true;
        this.jsonRpc.getPosts().then(jsonRpcResponse => {
            this._posts = [];
            jsonRpcResponse.result.forEach(c => {
                this._posts.push(c);
            });
            this._loadingContent = false;
        });
    }

    _onSaveContent(e) {
        const { content, filePath } = e.detail;
        const editorElement = e.target;
        
        // Save file content to backend
        this.jsonRpc.saveFileContent({ path: filePath, content: content }).then(jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            // Check if result is an error (starts with "Error:")
            if (result && result.startsWith && result.startsWith('Error:')) {
                // Handle error
                if (editorElement && editorElement.markSaveError) {
                    editorElement.markSaveError();
                }
                alert('Error saving file: ' + result);
            } else {
                // Success - result contains the new preview URL
                this._fileContent = content;
                
                // Update the selected post with the new preview URL
                if (result && this._selectedPost) {
                    this._selectedPost = { ...this._selectedPost, url: result };
                }
                
                if (editorElement && editorElement.markSaved) {
                    editorElement.markSaved();
                }
            }
        }).catch(error => {
            if (editorElement && editorElement.markSaveError) {
                editorElement.markSaveError();
            }
            alert('Error saving file: ' + error.message);
        });
    }

    _onPostDelete(e) {
        e.stopPropagation();
        
        const post = e.detail.post;
        const postTitle = post.title || post.path || 'this post';
        
        if (!confirm(`Are you sure you want to delete "${postTitle}"? This action cannot be undone.`)) {
            return;
        }

        this.jsonRpc.deletePost({ path: post.path }).then(jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            if (result === 'success' || result === true) {
                const postIndex = this._posts.findIndex(p => p.path === post.path);
                this._posts = [
                    ...this._posts.slice(0, postIndex),
                    ...this._posts.slice(postIndex + 1)
                ];
            } else {
                alert('Error deleting post: ' + (result || 'Unknown error'));
            }
        }).catch(error => {
            alert('Error deleting post: ' + error.message);
        });
    }

}
customElements.define('qwc-roq-editor', QwcRoqEditor);