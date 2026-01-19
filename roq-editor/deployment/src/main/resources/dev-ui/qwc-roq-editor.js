import {LitElement, html, css} from 'lit';
import {JsonRpc} from 'jsonrpc';
import {connectionState} from 'connection-state';
import './components/navigation-bar.js';
import './components/posts-list.js';
import './components/pages-list.js';
import './components/tags-list.js';
import './components/visual-editor.js';
import './components/simple-editor.js';
import {showPrompt} from './components/prompt-dialog.js';

export class QwcRoqEditor extends LitElement {

    jsonRpc = new JsonRpc(this);

    // Track connection state for refreshing preview URL after hot reload
    _previousConnectionState = null;
    _pendingPreviewRefresh = false;

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
        "_loadingContent": {state: true},
        "_dateFormat": {state: true}
    }

    constructor() {
        super();
        this._activeTab = 0;
        this._selectedPost = null;
        this._fileContent = null;
        this._loadingContent = false;
        this._dateFormat = 'yyyy-MM-dd'; // Default, will be fetched from server
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

        // Fetch the configured date format from the server
        this.jsonRpc.getDateFormat().then(jsonRpcResponse => {
            this._dateFormat = jsonRpcResponse.result;
        });

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

        // Subscribe to connection state changes for preview URL refresh after hot reload
        this._previousConnectionState = connectionState.current?.isConnected ?? false;
        this._connectionStateObserver = () => this._onConnectionStateChange();
        connectionState.addObserver(this._connectionStateObserver);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._connectionStateObserver) {
            connectionState.removeObserver(this._connectionStateObserver);
        }
    }

    _onConnectionStateChange() {
        const currentConnected = connectionState.current?.isConnected ?? false;
        const wasConnected = this._previousConnectionState;

        // Detect reconnection: was not connected, now connected
        if (!wasConnected && currentConnected && this._pendingPreviewRefresh && this._selectedPost) {
            this._refreshPreviewUrl();
        }

        this._previousConnectionState = currentConnected;
    }

    _refreshPreviewUrl() {
        if (!this._selectedPost) return;

        const currentPath = this._selectedPost.path;

        // Fetch fresh posts and pages to get the updated URL
        Promise.all([
            this.jsonRpc.getPosts(),
            this.jsonRpc.getPages()
        ]).then(([postsResponse, pagesResponse]) => {
            const posts = postsResponse.result || [];
            const pages = pagesResponse.result || [];

            // Look in both posts and pages for the updated URL
            let updatedItem = posts.find(p => p.path === currentPath);
            if (!updatedItem) {
                updatedItem = pages.find(p => p.path === currentPath);
            }

            if (updatedItem && updatedItem.url) {
                // Update selected post/page with fresh URL
                this._selectedPost = {...this._selectedPost, url: updatedItem.url};
                this._pendingPreviewRefresh = false;
                console.log('new url detected for preview', updatedItem.url);
            }

            // Also update the lists
            this._posts = posts;
            this._pages = pages;
        }).catch(error => {
            console.error('Error refreshing preview URL:', error);
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
              ? this._renderEditor()
              : this._renderContent()
            }
          </div>
        `;
    }

    _onTabChanged(e) {
        this._activeTab = e.detail.selectedTab;
    }


    _renderEditor() {
        if (this._selectedPost.markup === 'markdown') {
            return html`
              <qwc-visual-editor
                .filePath="${this._selectedPost.path}"
                .fileExtension="${this._selectedPost.extension}"
                .markup="${this._selectedPost.markup}"
                .previewUrl="${this._selectedPost.url}"
                .date="${this._selectedPost.date}"
                .dateFormat="${this._dateFormat}"
                .loading="${this._loadingContent}"
                @close-viewer="${this._closeViewer}"
                .content="${this._fileContent}"
                @save-content="${this._onSaveContent}">
              </qwc-visual-editor>
            `;
        }
        return html`
          <qwc-simple-editor
            .filePath="${this._selectedPost.path}"
            .fileExtension="${this._selectedPost.extension}"
            .previewUrl="${this._selectedPost.url}"
            .loading="${this._loadingContent}"
            @close-viewer="${this._closeViewer}"
            .content="${this._fileContent}"
            @save-content="${this._onSaveContent}">
          </qwc-simple-editor>
        `;
    }

    _renderContent() {
        switch (this._activeTab) {
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
                this.jsonRpc.createPost({title: title}).then(jsonRpcResponse => {
                    const result = jsonRpcResponse.result;
                    console.log('result', result);
                    if (result && !result.startsWith("Error")) {
                        this._pendingPreviewRefresh = true;
                        const newPost = {path: result, markup: 'markdown', extension: 'md', title: title, description: ''};
                        this._onPostClicked({detail: {post: newPost}});
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
        this.jsonRpc.getFileContent({path: post.path}).then(jsonRpcResponse => {
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
        this.jsonRpc.getFileContent({path: page.path}).then(jsonRpcResponse => {
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

    _onSaveContent(e, syncPath) {
        const {content, filePath, date, title} = e.detail;
        const detail = e.detail;
        const target = e.target;

        // Save file content to backend
        this.jsonRpc.saveFileContent({path: filePath, content, date, title, syncPath}).then(jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            // Check if result contains an error
            if (result && result.error) {
                // Handle error
                if (target && target.markSaveError) {
                    target.markSaveError();
                }
                alert('Error saving file: ' + result.errorMessage);
            } else {
                if (result.syncPathRequest) {
                    let syncPath = confirm(`The file name seems to be out of sync with the title and date:\n\n-> ok to save: '${result.path}'\n\n-> cancel to keep: '${filePath}'`);
                    this._onSaveContent({target, detail}, syncPath);
                    return;
                }

                // Success - update the file path if it changed (e.g., due to date/title change)
                this._fileContent = content;

                if (result && result.path && this._selectedPost) {
                    this._selectedPost = {...this._selectedPost, path: result.path};
                    // Also update the editor's filePath property
                    if (target) {
                        target.filePath = result.path;
                    }
                }

                // Mark that we need to refresh preview URL after connection restores
                // The URL will be fetched fresh after indexing completes
                this._pendingPreviewRefresh = true;

                if (target && target.markSaved) {
                    target.markSaved();
                }
            }
        }).catch(error => {
            if (target && target.markSaveError) {
                target.markSaveError();
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

        this.jsonRpc.deletePost({path: post.path}).then(jsonRpcResponse => {
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