import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { connectionState } from 'connection-state';
import './components/navigation-bar.js';
import './components/pages-list.js';
import './components/tags-list.js';
import './components/visual-editor/visual-editor.js';
import './components/simple-editor.js';
import { showPrompt, showConfirm } from './components/prompt-dialog.js';
import { showNotification } from './components/notification-toast.js';

// Tab constants
const TABS = {
  POSTS: 'posts',
  PAGES: 'pages',
  TAGS: 'tags'
};

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
    "_posts": { state: true },
    "_pages": { state: true },
    "_activeTab": { state: true },
    "_selectedPage": { state: true },
    "_fileContent": { state: true },
    "_loadingContent": { state: true },
    "_dateFormat": { state: true }
  }

  constructor() {
    super();
    this._posts = [];
    this._pages = [];
    this._activeTab = TABS.POSTS;
    this._selectedPage = null;
    this._fileContent = null;
    this._loadingContent = false;
    this._dateFormat = 'yyyy-MM-dd'; // Default, will be fetched from server
  }

  // Components callbacks

  /**
   * Called when displayed
   */
  async connectedCallback() {
    super.connectedCallback();
    const showSidebar = location.search.includes('showSidebar');
    if (!showSidebar) {
      document.querySelector('qwc-menu').style.display = 'none';
    }

    // Subscribe to connection state changes for preview URL refresh after hot reload
    this._previousConnectionState = connectionState.current?.isConnected ?? false;
    this._connectionStateObserver = () => this._onConnectionStateChange();
    connectionState.addObserver(this._connectionStateObserver);

    try {
      const [postsResponse, pagesResponse, dateFormatResponse] = await Promise.all([
        this.jsonRpc.getPosts(),
        this.jsonRpc.getPages(),
        this.jsonRpc.getDateFormat()
      ]);
      this._posts = postsResponse.result || [];
      this._pages = pagesResponse.result || [];
      this._dateFormat = dateFormatResponse.result;
    } catch (error) {
      console.error('Error loading initial data:', error);
      showNotification('Error loading initial data: ' + error.message);
    }
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
    if (!wasConnected && currentConnected && this._pendingPreviewRefresh && this._selectedPage) {
      this._refreshPreviewUrl();
    }

    this._previousConnectionState = currentConnected;
  }

  async _refreshPreviewUrl() {
    if (!this._selectedPage) return;

    const currentPath = this._selectedPage.path;

    try {
      // Fetch fresh posts and pages to get the updated URL
      const [postsResponse, pagesResponse] = await Promise.all([
        this.jsonRpc.getPosts(),
        this.jsonRpc.getPages()
      ]);

      const posts = postsResponse.result || [];
      const pages = pagesResponse.result || [];

      // Look in both posts and pages for the updated URL
      let updatedItem = posts.find(p => p.path === currentPath);
      if (!updatedItem) {
        updatedItem = pages.find(p => p.path === currentPath);
      }

      if (updatedItem && updatedItem.url) {
        // Update selected post/page with fresh URL
        this._selectedPage = { ...this._selectedPage, url: updatedItem.url };
        this._pendingPreviewRefresh = false;
        console.log('new url detected for preview', updatedItem.url);
      }

      // Also update the lists
      this._posts = posts;
      this._pages = pages;
    } catch (error) {
      console.error('Error refreshing preview URL:', error);
    }
  }

  /**
   * Called when it needs to render the components
   * @returns {*}
   */
  render() {
    return html`
      <div class="content-area">
        ${this._selectedPage && this._fileContent !== null
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
    if (this._selectedPage.markup === 'markdown') {
      return html`
              <qwc-visual-editor
                .filePath="${this._selectedPage.path}"
                .fileExtension="${this._selectedPage.extension}"
                .suggestedPath="${this._selectedPage.suggestedPath}"
                .markup="${this._selectedPage.markup}"
                .previewUrl="${this._selectedPage.url}"
                .date="${this._selectedPage.date}"
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
            .filePath="${this._selectedPage.path}"
            .fileExtension="${this._selectedPage.extension}"
            .suggestedPath="${this._selectedPage.suggestedPath}"
            .previewUrl="${this._selectedPage.url}"
            .loading="${this._loadingContent}"
            @close-viewer="${this._closeViewer}"
            .content="${this._fileContent}"
            @save-content="${this._onSaveContent}">
          </qwc-simple-editor>
        `;
  }

  _renderContent() {
    switch (this._activeTab) {
      case TABS.POSTS:
        return this._renderPosts();
      case TABS.PAGES:
        return this._renderPages();
      case TABS.TAGS:
        return this._renderTags();
      default:
        return html`<span>Unknown tab</span>`;
    }
  }

  _renderPosts() {
    return html`
          <qwc-pages-list
            collectionId="posts"
            .pages="${this._posts}"
            @add-new-page="${this._addNewPage}"
            @page-clicked="${this._onPageClicked}"
            @page-delete="${this._onPageDelete}"
            @page-sync-path="${this._onPageSyncPath}"
          >
          </qwc-pages-list>
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
    return this._pages;
  }

  async _addNewPage(e) {
    const collectionId = e.target.collectionId;
    const title = await showPrompt('Enter title:', '');

    if (!title) return;

    try {
      const jsonRpcResponse = await this.jsonRpc.createPage({ collectionId, title });
      const result = jsonRpcResponse.result;

      // Check if result contains an error
      if (result?.error) {
        showNotification('Error creating page: ' + result.errorMessage);
        return;
      }

      console.log('result', result);
      this._pendingPreviewRefresh = true;

      if (collectionId) {
        this._posts = [result.page, ...this._posts];
      } else {
        this._pages = [result.page, ...this._pages];
      }

      this._onPageClicked({ detail: { page: result.page, content: result.content } });
    } catch (error) {
      showNotification('Error creating page: ' + error.message);
    }
  }

  async _onPageSyncPath(e) {
    e.stopPropagation();
    const page = e.detail.page;

    const confirmed = await showConfirm(
      `Are you sure you want to change the page path to '${page.suggestedPath}'?`,
      { confirmText: 'Change Path', theme: 'primary' }
    );
    if (!confirmed) return;

    try {
      const jsonRpcResponse = await this.jsonRpc.syncPath({ path: page.path });
      const result = jsonRpcResponse.result;

      // Check if result contains an error
      if (result?.error) {
        showNotification('Error syncing page path: ' + result.errorMessage);
        return;
      }

      const updated = { ...page, path: result.newPath, suggestedPath: null };

      if (page.collectionId) {
        this._posts = this._posts.map(p => p.path === page.path ? updated : p);
      } else {
        this._pages = this._pages.map(p => p.path === page.path ? updated : p);
      }

      if (this._selectedPage?.path === page.path) {
        this._selectedPage = updated;
      }
      this._pendingPreviewRefresh = true;
    } catch (error) {
      showNotification('Error syncing page path: ' + error.message);
    }
  }

  async _onPageClicked(e) {
    const page = e.detail.page;
    const content = e.detail.content;
    this._selectedPage = page;

    if (content) {
      this._fileContent = content;
      return;
    }

    this._loadingContent = true;
    this._fileContent = null;

    try {
      const jsonRpcResponse = await this.jsonRpc.getPageContent({ path: page.path });
      const result = jsonRpcResponse.result;

      // Check if result contains an error
      if (result?.error) {
        showNotification('Error loading page content: ' + result.errorMessage);
        return;
      }

      this._fileContent = result.content;
    } catch (error) {
      this._fileContent = "Error loading file content: " + error.message;
    } finally {
      this._loadingContent = false;
    }
  }

  async _closeViewer() {
    this._selectedPage = null;
    this._fileContent = null;
    this._loadingContent = true;

    try {
      const jsonRpcResponse = await this.jsonRpc.getPosts();
      this._posts = jsonRpcResponse.result || [];
    } catch (error) {
      console.error('Error refreshing posts:', error);
    } finally {
      this._loadingContent = false;
    }
  }

  async _onSaveContent(e) {
    const { content, filePath, date, title } = e.detail;
    const target = e.target;

    try {
      const jsonRpcResponse = await this.jsonRpc.savePageContent({ path: filePath, content, date, title });
      const result = jsonRpcResponse.result;

      // Check if result contains an error
      if (result?.error) {
        target?.markSaveError?.();
        showNotification('Error saving file: ' + result.errorMessage);
        return;
      }

      // Success - update the file path if it changed (e.g., due to date/title change)
      this._fileContent = content;

      if (result?.path && this._selectedPage) {
        this._selectedPage = {
          ...this._selectedPage,
          path: result.path,
          suggestedPath: result.suggestedPath
        };
        // Also update the editor's filePath property
        if (target) {
          target.filePath = result.path;
        }
      }

      // Mark that we need to refresh preview URL after connection restores
      // The URL will be fetched fresh after indexing completes
      this._pendingPreviewRefresh = true;

      target?.markSaved?.();
    } catch (error) {
      target?.markSaveError?.();
      showNotification('Error saving file: ' + error.message);
    }
  }

  async _onPageDelete(e) {
    e.stopPropagation();

    const page = e.detail.page;
    const title = page.title || page.path || 'this';

    const confirmed = await showConfirm(
      `Are you sure you want to delete "${title}"? This action cannot be undone in this editor.`,
      { confirmText: 'Delete', theme: 'error' }
    );
    if (!confirmed) return;

    try {
      const jsonRpcResponse = await this.jsonRpc.deletePage({ path: page.path });
      const result = jsonRpcResponse.result;

      if (result === 'success' || result === true) {
        const source = page.collectionId ? this._posts : this._pages;
        const updated = source.filter(p => p.path !== page.path);

        if (page.collectionId) {
          this._posts = updated;
        } else {
          this._pages = updated;
        }
      } else {
        showNotification('Error deleting page: ' + (result || 'Unknown error'));
      }
    } catch (error) {
      showNotification('Error deleting page: ' + error.message);
    }
  }

}

customElements.define('qwc-roq-editor', QwcRoqEditor);
