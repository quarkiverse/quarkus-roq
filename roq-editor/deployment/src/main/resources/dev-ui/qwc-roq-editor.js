import {LitElement, html, css} from 'lit';
import {JsonRpc} from 'jsonrpc';
import {connectionState} from 'connection-state';
import './components/navigation-bar.js';
import './components/pages-list.js';
import './components/tags-list.js';
import './components/visual-editor/visual-editor.js';
import './components/simple-editor.js';
import './components/loading-dialog.js';
import {showPrompt} from './components/prompt-dialog.js';
import {showConfirm} from './components/confirm-dialog.js';
import {showNotification} from './components/notification-toast.js';
import {markups, config} from 'build-time-data';
import {containsDataRawTag, containsPotentialHtml, containsQuteSection} from "./utils/utils.js";

export class QwcRoqEditor extends LitElement {

    jsonRpc = new JsonRpc(this);

    // Track connection state for refreshing preview URL after hot reload
    _previousConnectionState = null;
    _pendingRefreshPages = false;

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
        "_selectedPage": {state: true},
        "_fileContent": {state: true},
        "_visualEditorEnabled": {state: true},
        "_loadingContent": {state: true},
        "_pendingRefreshPages": {state: true},
        "_dateFormat": {state: true}
    }

    constructor() {
        super();
        this._visualEditorEnabled = false;
        this._activeTab = 0;
        this._selectedPage = null;
        this._fileContent = null;
        this._loadingContent = false;
        this._pendingRefreshPages = false;
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

        try {
            const [postsResponse, pagesResponse, dateFormatResponse] = await Promise.all([
                this.jsonRpc.getPosts(),
                this.jsonRpc.getPages(),
                this.jsonRpc.getDateFormat()
            ]);
            this._posts = postsResponse.result || [];
            this._pages = pagesResponse.result || [];
            this._dateFormat = dateFormatResponse.result;
            this._pendingRefreshPages = false;
        } catch (error) {
            console.error('Error loading initial data:', error);
            showNotification('Error loading initial data: ' + error.message);
        }


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
        if (!wasConnected && currentConnected && this._pendingRefreshPages) {
            this._refreshPageInfo();
        }

        this._previousConnectionState = currentConnected;
    }

    _refreshPageInfo() {

       // Fetch fresh posts and pages to get the updated URL
        Promise.all([
            this.jsonRpc.getPosts(),
            this.jsonRpc.getPages()
        ]).then(([postsResponse, pagesResponse]) => {
            const posts = postsResponse.result || [];
            const pages = pagesResponse.result || [];

            if(this._selectedPage) {
                // Look in both posts and pages for the updated URL
                let updatedItem = posts.find(p => p.path === this._selectedPage.path);
                if (!updatedItem) {
                    updatedItem = pages.find(p => p.path === this._selectedPage.path);
                }

                if (updatedItem && JSON.stringify(this._selectedPage) !== JSON.stringify(updatedItem)) {
                    // Update selected post/page with fresh URL
                    this._selectedPage = updatedItem;
                    console.log('new url detected for preview', updatedItem.url);
                }
            }

            // Also update the lists
            this._posts = posts;
            this._pages = pages;

            this._pendingRefreshPages = false;

        }).catch(error => {
            console.error('Error refreshing preview URL:', error);
        });
    }

    render() {
        return html`
          <div class="content-area">
            <qwc-loading-dialog .open="${this._pendingRefreshPages === true}"></qwc-loading-dialog>
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
        if (this._visualEditorEnabled) {
            return html`
              <qwc-visual-editor
                .page="${this._selectedPage}"
                .dateFormat="${this._dateFormat}"
                .loading="${this._loadingContent}"
                @close-viewer="${this._closeViewer}"
                .content="${this._fileContent}"
                @save-content="${this._onSaveContent}"
                @page-sync-path="${this._onPageSyncPath}"
              >
              </qwc-visual-editor>
            `;
        }
        return html`
          <qwc-simple-editor
            .page="${this._selectedPage}"
            .loading="${this._loadingContent}"
            @close-viewer="${this._closeViewer}"
            .content="${this._fileContent}"
            @save-content="${this._onSaveContent}"
            @page-sync-path="${this._onPageSyncPath}"
          >
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
          <qwc-pages-list
            collectionId="posts"
            .pages="${this._posts}"
            @add-new-page="${this._addNewPage}"
            @page-open="${this._onPageOpen}"
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
            @page-open="${this._onPageOpen}">
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

    _addNewPage(e) {
        const collectionId = e.target.collectionId;
        const defaultMarkup = (collectionId ? config.docMarkup : config.pageMarkup).toLowerCase()
        showPrompt(`Add new ${collectionId ? 'document' : 'page'}` , { title: '', markup: defaultMarkup }, this._renderCreatePageForm).then(({title, markup}) => {
            if (title) {
                this.jsonRpc.createPage({collectionId, title, markup}).then(jsonRpcResponse => {
                    const result = jsonRpcResponse.result;
                    // Check if result contains an error
                    if (result?.error) {
                        showNotification('Error creating page: ' + result.errorMessage);
                        console.error(result.errorMessage);
                        return;
                    }
                    console.log('result', result);
                    this._pendingRefreshPages = true;
                    if (collectionId) {
                        this._posts = [result.page].concat(this._posts);
                    } else {
                        this._pages = [result.page].concat(this._pages);
                    }
                    this._onPageOpen({detail: {page: result.page, content: result.content}});
                }).catch(error => {
                    showNotification('Error creating page: ' + error.message);
                    console.error(error.message);
                });
            }
        });
    }

    _renderCreatePageForm(ctx) {
        return html`
          <vaadin-select
            label="Markup"
            .items=${markups.map(m => ({ label: m, value: m}))}
            .value=${ctx.values.markup ?? 'markdown'}
            @value-changed=${(e) => ctx.update('markup', e.detail.value)}>
          </vaadin-select>
          <vaadin-text-field
            label="Title"
            autofocus
            id="prompt-title"
            .value=${ctx.values.title ?? ''}
            @value-changed=${(e) => ctx.update('title', e.detail.value)}
          >
          </vaadin-text-field>
          <qui-alert permanent size="small"><span>Don’t worry, the title and date can be updated at any time and synced with the filename.</span></qui-alert>
        `;
    }

    async _onPageSyncPath(e) {
        e.stopPropagation();
        const page = e.detail.page;

        const confirmed = await showConfirm(
            `new path: '${page.suggestedPath}'`,
            { title: 'Do you really want to change the page path?', confirmText: 'Change Path', theme: 'primary' }
        );
        if (!confirmed) return;

        // Save file content to backend
        this.jsonRpc.syncPath({path: page.path}).then(jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            // Check if result contains an error
            if (result && result.error) {
                showNotification('Error syncing page path: ' + result.errorMessage);
                console.error(result.errorMessage);
                return;
            }
            const updated = {...page, path: result.newPath, suggestedPath: null}

            if (page.collectionId) {
                this._posts = this._posts.map(p => p.path === page.path ? updated : p);
            } else {
                this._pages = this._pages.map(p => p.path === page.path ? updated : p);
            }

            if (this._selectedPage?.path === page.path) {
                this._selectedPage = updated;
            }
            this._pendingRefreshPages = true;
        }).catch(error => {
            showNotification('Error syncing page path: ' + error.message);
            console.error(error);
        });

    }

    async _onPageOpen(e) {
        const page = e.detail.page;
        const content = e.detail.content;
        this._selectedPage = page;
        if (content) {
            this._visualEditorEnabled = await this._shouldEnableVisualEditor(content);
           this._fileContent = content;
           return
        }
        this._loadingContent = true;
        this._fileContent = null;



        // Fetch file content from backend
        this.jsonRpc.getPageContent({path: page.path}).then(async jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            // Check if result contains an error
            if (result && result.error) {
                showNotification('Error reading file: ' + result.errorMessage);
                console.error(result.errorMessage);
                return;
            }
            this._visualEditorEnabled = await this._shouldEnableVisualEditor(result.content);
            this._fileContent = result.content;
            this._loadingContent = false;
        }).catch(error => {
            showNotification('Error reading file: ' + error.message);
            console.error(error);
            this._fileContent = null;
            this._visualEditorEnabled = false;
            this._loadingContent = false;
        });
    }

    async _shouldEnableVisualEditor(content) {
        if (this._selectedPage.markup === 'markdown') {
            if (config.visualEditor.enabled) {
                const hasDataRaw = containsDataRawTag(content);
                const hasQuteSection = containsQuteSection(content);
                const hasHtml = containsPotentialHtml(content);
                if (config.visualEditor.safe && !hasDataRaw && (hasQuteSection || hasHtml)) {
                    const codeEditor = await showConfirm(
                        'HTML and/or Qute sections were detected. Wrap them in <div data-raw></div> to ensure compatibility.',
                        {
                            title: 'Visual Editor Compatibility Warning',
                            confirmText: 'Use Code Editor',
                            cancelText: 'Continue with Visual Editor'
                        }
                    );
                    return  !codeEditor;
                } else {
                    return true;
                }

            } else {
                showNotification('Visual Editor has been disabled in the configuration. Falling back to Simple Markdown editor.', 'warning');
            }
           return false;
        }
    }

    _closeViewer() {
        this._selectedPage = null;
        this._fileContent = null;
        this._visualEditorEnabled = false;
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
        const {content, path, date, title} = e.detail;
        const detail = e.detail;
        const target = e.target;

        // Save file content to backend
        this.jsonRpc.savePageContent({path, content, date, title}).then(jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            // Check if result contains an error
            if (result?.error) {
                // Handle error
                if (target && target.markSaveError) {
                    target.markSaveError();
                }
                showNotification('Error saving file: ' + result.errorMessage);
                console.error(result.errorMessage);
                return;
            } else {
                // Success - update the file path if it changed (e.g., due to date/title change)
                this._fileContent = content;

                if (result && result.suggestedPath && this._selectedPage) {
                    this._selectedPage = {
                        ...this._selectedPage,
                        suggestedPath: result.suggestedPath
                    };
                }

                this._pendingRefreshPages = "background";

                if (target && target.markSaved) {
                    target.markSaved();
                }
            }
        }).catch(error => {
            if (target && target.markSaveError) {
                target.markSaveError();
            }
            showNotification('Error saving file: ' + error.message);
            console.error(error);
        });
    }

    async _onPageDelete(e) {
        e.stopPropagation();

        const page = e.detail.page;
        const title = page.title || page.path || 'this';

        const confirmed = await showConfirm(
            `"${title}" (This action cannot be undone in this editor).`,
            { title: 'Are you sure you want to delete this page?', confirmText: 'Delete', theme: 'error' }
        );
        if (!confirmed) return;

        this.jsonRpc.deletePage({path: page.path}).then(jsonRpcResponse => {
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
        }).catch(error => {
            showNotification('Error deleting page: ' + error.message);
            console.error(error);
        });
    }

}

customElements.define('qwc-roq-editor', QwcRoqEditor);