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
     */
    render() {
        return html`
            <qwc-navigation-bar 
                .activeTab="${this._activeTab}"
                @tab-changed="${this._onTabChanged}">
            </qwc-navigation-bar>
            <div class="content-area">
                ${this._selectedPost && this._fileContent !== null 
                    ? html`
                        <qwc-file-content-viewer 
                            .content="${this._fileContent}"
                            .filePath="${this._selectedPost.path}"
                            .loading="${this._loadingContent}"
                            @close-viewer="${this._closeViewer}"
                            @save-content="${this._onSaveContent}">
                        </qwc-file-content-viewer>
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
                @post-clicked="${this._onPostClicked}">
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
                // TODO: Implement actual post creation with the title
                // For now, just show an alert with the title
                alert(`Creating new post with title: ${title}`);
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
        this._loadingContent = false;
    }

    _onSaveContent(e) {
        const { content, filePath } = e.detail;
        const editorElement = e.target;
        
        // Save file content to backend
        this.jsonRpc.saveFileContent({ path: filePath, content: content }).then(jsonRpcResponse => {
            if (jsonRpcResponse.result === 'success' || jsonRpcResponse.result === true) {
                // Update the content and mark as saved
                this._fileContent = content;
                if (editorElement && editorElement.markSaved) {
                    editorElement.markSaved();
                }
            } else {
                // Handle error
                if (editorElement && editorElement.markSaveError) {
                    editorElement.markSaveError();
                }
                alert('Error saving file: ' + (jsonRpcResponse.result || 'Unknown error'));
            }
        }).catch(error => {
            if (editorElement && editorElement.markSaveError) {
                editorElement.markSaveError();
            }
            alert('Error saving file: ' + error.message);
        });
    }

}
customElements.define('qwc-roq-editor', QwcRoqEditor);