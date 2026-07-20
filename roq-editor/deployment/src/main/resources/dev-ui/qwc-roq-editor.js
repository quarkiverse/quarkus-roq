import {LitElement, html, css} from 'lit';
import {JsonRpc} from 'jsonrpc';
import {connectionState} from 'connection-state';
import './components/navigation-bar.js';
import './components/pages-list.js';
import './components/tags-list.js';
import './components/visual-editor/visual-editor.js';
import './components/simple-editor.js';
import './components/loading-dialog.js';
import './components/ai-prompt-widget.js';
import {showPrompt} from './components/prompt-dialog.js';
import {showConfirm} from './components/confirm-dialog.js';
import {showNotification} from './components/notification-toast.js';
import {showConflictDialog} from './components/conflict-dialog.js';
import {showFileSelector} from './components/file-selector-dialog.js';
import {SyncManager} from './utils/sync-manager.js';
import {markups, config} from 'build-time-data';
import {containsDataRawTag, containsPotentialHtml, containsQuteSection} from "./utils/utils.js";

export class QwcRoqEditor extends LitElement {

    jsonRpc = new JsonRpc(this);
    gitJsonRpc = {
        getSyncStatus: (params) => this.jsonRpc.getSyncStatus(params),
        syncContent: (params) => this.jsonRpc.syncContent(params),
        publishContent: (params) => this.jsonRpc.publishContent(params),
        publishAndSync: (params) => this.jsonRpc.publishAndSync(params)
    };

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
        "_dateFormat": {state: true},
        "_syncStatus": {state: true},
        "_syncing": {state: true},
        "_publishing": {state: true}
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
        this._syncStatus = null;
        this._syncing = false;
        this._publishing = false;
        this._syncManager = null;
        this._fromHistoryNavigation = false;
        this._historyEntryForEditor = false;
    }

    // Components callbacks

    /**
     * Called when displayed
     */
    async connectedCallback() {
        super.connectedCallback();
        const showSidebar = location.search.includes('showSidebar');
        if (!showSidebar) {
            document.querySelector('qwc-menu')._smaller();
        }

        this._popStateHandler = (e) => this._onPopState(e);
        window.addEventListener('popstate', this._popStateHandler);

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
            await this._restorePageFromUrl();
        } catch (error) {
            console.error('Error loading initial data:', error);
            showNotification('Error loading initial data: ' + error.message);
        }


        this._previousConnectionState = connectionState.current?.isConnected ?? false;
        this._connectionStateObserver = () => this._onConnectionStateChange();
        connectionState.addObserver(this._connectionStateObserver);

        // Subscribe to connection state changes for preview URL refresh after hot reload
        if (config.sync?.enabled) {
            if (!this._syncManager) {
                this._syncManager = new SyncManager(
                    this.gitJsonRpc,
                    (status) => { this._syncStatus = status; },
                    config.sync,
                    {
                        onNotification: (msg, type) => showNotification(msg, type),
                        onConflict: (files) => showConflictDialog(files),
                        onBusy: (isBusy) => { this._syncing = isBusy; }
                    }
                );
            } else {
                this._syncManager.updateJsonRpc(this.gitJsonRpc);
            }
            this._syncManager.start();
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._popStateHandler) {
            window.removeEventListener('popstate', this._popStateHandler);
        }
        if (this._connectionStateObserver) {
            connectionState.removeObserver(this._connectionStateObserver);
        }
        if (this._syncManager) {
            this._syncManager.stop();
        }
    }

    _buildEditorUrl(pagePath) {
        const url = new URL(window.location.href);
        if (pagePath) {
            url.searchParams.set('page', pagePath);
        } else {
            url.searchParams.delete('page');
        }
        return url;
    }

    _getPagePathFromUrl() {
        return new URL(window.location.href).searchParams.get('page');
    }

    _findPageByPath(path) {
        return this._posts?.find(p => p.path === path)
            || this._pages?.find(p => p.path === path);
    }

    _updateHistoryForPage(page, { push = true } = {}) {
        const url = this._buildEditorUrl(page.path);
        const state = { roqEditor: { view: 'page', path: page.path } };
        if (push) {
            history.pushState(state, '', url);
            this._historyEntryForEditor = true;
        } else {
            history.replaceState(state, '', url);
            this._historyEntryForEditor = false;
        }
    }

    _replaceListHistory() {
        const url = this._buildEditorUrl(null);
        history.replaceState({ roqEditor: { view: 'list' } }, '', url);
        this._historyEntryForEditor = false;
    }

    async _restorePageFromUrl() {
        const pagePath = this._getPagePathFromUrl();
        if (!pagePath) {
            return;
        }

        const page = this._findPageByPath(pagePath);
        if (!page) {
            this._replaceListHistory();
            return;
        }

        this._fromHistoryNavigation = true;
        try {
            await this._onPageOpen({ detail: { page } });
            this._updateHistoryForPage(page, { push: false });
        } finally {
            this._fromHistoryNavigation = false;
        }
    }

    _onPopState(e) {
        this._historyEntryForEditor = false;
        const pagePath = e.state?.roqEditor?.path ?? this._getPagePathFromUrl();
        if (pagePath) {
            const page = this._findPageByPath(pagePath);
            if (page) {
                this._fromHistoryNavigation = true;
                this._onPageOpen({ detail: { page } })
                    .finally(() => {
                        this._fromHistoryNavigation = false;
                    });
                return;
            }
        }
        this._closeViewerInternal();
    }

    _onConnectionStateChange() {
        const currentConnected = connectionState.current?.isConnected ?? false;
        const wasConnected = this._previousConnectionState;

        // Detect reconnection: was not connected, now connected
        if (!wasConnected && currentConnected) {
            if (this._pendingRefreshPages) {
                this._refreshPageInfo();
            }
            if (this._syncManager) {
                this._syncManager.updateJsonRpc(this.gitJsonRpc);
                this._syncManager.start();
                this._syncManager.refreshStatus(false);
            }
        } else if (wasConnected && !currentConnected) {
            if (this._syncManager) {
                this._syncManager.stop();
            }
        }

        // ALWAYS reset syncing/publishing states if the connection state changes
        if (wasConnected !== currentConnected) {
            this._syncing = false;
            this._publishing = false;
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
        const syncProps = {
            syncStatus: this._syncStatus,
            syncing: this._syncing,
            publishing: this._publishing
        };

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
                @generate-ai-content="${this._onGenerateAiContent}"
                .syncStatus="${syncProps.syncStatus}"
                .syncing="${syncProps.syncing}"
                .publishing="${syncProps.publishing}"
                @sync-requested="${this._onSyncRequested}"
                @publish-requested="${this._onPublishRequested}"
                @show-conflicts="${(e) => showConflictDialog(e.detail.files)}"
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
            .syncStatus="${syncProps.syncStatus}"
            .syncing="${syncProps.syncing}"
            .publishing="${syncProps.publishing}"
            @sync-requested="${this._onSyncRequested}"
            @publish-requested="${this._onPublishRequested}"
            @show-conflicts="${(e) => showConflictDialog(e.detail.files)}"
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
            .syncStatus="${this._syncStatus}"
            .syncing="${this._syncing}"
            .publishing="${this._publishing}"
            @add-new-page="${this._addNewPage}"
            @page-open="${this._onPageOpen}"
            @page-delete="${this._onPageDelete}"
            @page-sync-path="${this._onPageSyncPath}"
            @sync-requested="${this._onSyncRequested}"
            @publish-requested="${this._onPublishRequested}"
            @show-conflicts="${(e) => showConflictDialog(e.detail.files)}"
          >
          </qwc-pages-list>
        `;
    }

    _renderPages() {
        return html`
          <qwc-pages-list
            .pages="${this._pages}"
            .syncStatus="${this._syncStatus}"
            .syncing="${this._syncing}"
            .publishing="${this._publishing}"
            @page-open="${this._onPageOpen}"
            @sync-requested="${this._onSyncRequested}"
            @publish-requested="${this._onPublishRequested}"
            @show-conflicts="${(e) => showConflictDialog(e.detail.files)}">
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
                this._writeCall('createPage', {collectionId, title, markup}).then(jsonRpcResponse => {
                    const result = jsonRpcResponse.result;
                    this._pendingRefreshPages = true;
                    if (collectionId) {
                        this._posts = [result.page].concat(this._posts);
                    } else {
                        this._pages = [result.page].concat(this._pages);
                    }
                    this._syncManager?.markAsDirty();
                    this._syncManager?.refreshStatus(true);
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

        this._writeCall('syncPath', {path: page.path}).then(jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            this._applyPagePath(page, result.newPath);
        }).catch(error => {
            showNotification('Error syncing page path: ' + error.message);
            console.error(error);
        });
    }

    _applyPagePath(page, newPath) {
        const updated = {...page, path: newPath, suggestedPath: null};
        if (page.collectionId) {
            this._posts = this._posts.map(p => p.path === page.path ? updated : p);
        } else {
            this._pages = this._pages.map(p => p.path === page.path ? updated : p);
        }
        if (this._selectedPage?.path === page.path) {
            this._selectedPage = updated;
            if (this._historyEntryForEditor || history.state?.roqEditor?.view === 'page') {
                const url = this._buildEditorUrl(newPath);
                history.replaceState({ roqEditor: { view: 'page', path: newPath } }, '', url);
            }
        }
        this._syncManager?.markAsDirty();
        this._syncManager?.refreshStatus(true);
        this._pendingRefreshPages = true;
    }

    async _writeCall(method, params) {
        const response = await this.jsonRpc[method](params);
        const wrapper = response.result;
        if (wrapper?.error) {
            throw new Error(wrapper.error);
        }
        if (!wrapper?.async) {
            return { result: wrapper?.result };
        }
        const timeout = 15000;
        const interval = 500;
        const start = Date.now();
        while (Date.now() - start < timeout) {
            await new Promise(r => setTimeout(r, interval));
            try {
                const statusResponse = await this.jsonRpc.writeStatus();
                const status = statusResponse.result;
                if (status.status === 'DONE') return { result: wrapper.result };
                if (status.status === 'ERROR') {
                    const err = new Error(status.error || 'File operation failed');
                    err._writeOpError = true;
                    throw err;
                }
            } catch (e) {
                if (e._writeOpError) throw e;
            }
        }
        throw new Error('File operation timed out');
    }

    async _onPageOpen(e) {
        const page = e.detail.page;
        const content = e.detail.content;

        if (this._selectedPage?.path === page.path && this._fileContent !== null && !content) {
            return;
        }

        if (!this._fromHistoryNavigation) {
            this._updateHistoryForPage(page);
        }

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
        if (this._historyEntryForEditor) {
            this._historyEntryForEditor = false;
            history.back();
            return;
        }
        if (this._getPagePathFromUrl()) {
            this._replaceListHistory();
        }
        this._closeViewerInternal();
    }

    _closeViewerInternal() {
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

    _onGenerateAiContent(e) {
        const { editor } = e.detail;
        if (!editor || editor.isDestroyed) return;

        const coords = editor.view.coordsAtPos(editor.view.state.selection.from);

        const widget = document.createElement('qwc-ai-prompt');
        widget.style.left = `${coords.left}px`;
        widget.style.top = `${coords.bottom + 4}px`;
        document.body.appendChild(widget);

        widget.addEventListener('close', () => editor.commands.focus());

        widget.addEventListener('generate', async (e) => {
            const { prompt } = e.detail;
            try {
                const pos = editor.state.selection.from;
                const textBefore = editor.state.doc.textBetween(0, pos, '\n');
                const textAfter = editor.state.doc.textBetween(pos, editor.state.doc.content.size, '\n');
                const trimBefore = textBefore.length > 2000 ? '...' + textBefore.slice(-2000) : textBefore;
                const trimAfter = textAfter.length > 1000 ? textAfter.slice(0, 1000) + '...' : textAfter;
                const context = `Article content before cursor:\n${trimBefore}\n\n---CURSOR IS HERE---\n\n`
                    + (trimAfter.trim() ? `Article content after cursor:\n${trimAfter}` : '(end of article)');
                const response = await this.jsonRpc.generateContent({ message: prompt, context });
                const raw = response.result;

                let markdown;
                try {
                    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
                    markdown = parsed.body || parsed.answer || parsed.markdown || parsed.content || raw;
                } catch (err) {
                    markdown = raw;
                }

                editor.chain().focus().insertContent(markdown, { contentType: 'markdown' }).run();
                widget.close();
            } catch (error) {
                console.error('Error generating content:', error);
                widget.showError();
            }
        });
    }

    _onSaveContent(e) {
        const {content, path, date, title} = e.detail;
        const detail = e.detail;
        const target = e.target;

        this._writeCall('savePageContent', {path, content, date, title}).then(jsonRpcResponse => {
            const result = jsonRpcResponse.result;
            this._fileContent = content;

            if (result?.newPath && this._selectedPage) {
                this._applyPagePath(this._selectedPage, result.newPath);
            } else if (result?.suggestedPath && this._selectedPage) {
                this._selectedPage = {
                    ...this._selectedPage,
                    suggestedPath: result.suggestedPath
                };
            }

            this._pendingRefreshPages = "background";
            this._syncManager?.markAsDirty();
            this._syncManager?.refreshStatus(true);

            if (target && target.markSaved) {
                target.markSaved();
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

        this._writeCall('deletePage', {path: page.path}).then(() => {
            const source = page.collectionId ? this._posts : this._pages;
            const updated = source.filter(p => p.path !== page.path);
            if (page.collectionId) {
                this._posts = updated;
            } else {
                this._pages = updated;
            }
            this._syncManager?.markAsDirty();
            this._syncManager?.refreshStatus(true);
        }).catch(error => {
            showNotification('Error deleting page: ' + error.message);
            console.error(error);
        });
    }

    async _onSyncRequested() {
        this._syncing = true;
        try {
            const result = await this._syncManager.manualSync();
            if (result?.success) {
                this._pendingRefreshPages = true;
                this._refreshPageInfo();
            }
        } catch (error) {
            showNotification('Sync failed: ' + error.message, 'error');
        } finally {
            this._syncing = false;
        }
    }

    async _onPublishRequested(event) {
        const freshStatus = await this._syncManager?.refreshStatus(false);
        const files = freshStatus?.pendingFiles ?? [];

        let selectedFiles = files;
        if (files.length > 1) {
            selectedFiles = await showFileSelector(files);
            if (selectedFiles === null) return;
        }

        const needsCommit = selectedFiles.length > 0 || freshStatus?.repositoryState === 'MERGING';
        let message = null;
        if (needsCommit) {
            message = await showPrompt('Commit message', config.sync?.commitMessage?.template || 'Update content via Roq Editor');
            if (!message) return;
        }

        let branchName = null;
        if (needsCommit && this._isStartingPrCycle(freshStatus)) {
            const suggested = this._suggestContentBranchName();
            branchName = await showPrompt('Branch name for the content PR', suggested);
            if (!branchName) return;
        }

        this._publishing = true;
        try {
            await this._syncManager.manualPublish(message, selectedFiles, branchName);
        } catch (error) {
            showNotification('Publish failed: ' + error.message, 'error');
        } finally {
            this._publishing = false;
        }
    }

    /**
     * Returns true when the next publish will open a fresh content-branch cycle and the user
     * should be prompted for a branch name: PR mode is active and the current branch is not
     * already a content branch (heuristically, doesn't start with the configured prefix).
     */
    _isStartingPrCycle(status) {
        if (config.sync?.mode !== 'PR') return false;
        const prefix = config.sync?.prFlow?.contentBranchPrefix ?? 'content/';
        const branch = status?.branch ?? '';
        return !branch.startsWith(prefix);
    }

    _suggestContentBranchName() {
        const prefix = config.sync?.prFlow?.contentBranchPrefix ?? 'content/';
        const now = new Date();
        const pad = (n) => String(n).padStart(2, '0');
        const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`;
        return `${prefix}update-${stamp}`;
    }

}

customElements.define('qwc-roq-editor', QwcRoqEditor);
