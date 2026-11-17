import lunr from 'lunr';
import './search.css';
import debounce from 'lodash/debounce';

const setupSearch = function (options = {}) {

    const {
        searchButton = '#search-button',
        searchTriggerInput = '.search-trigger-input',
        searchField = '#search-field',
        resultContainer = '#search-results',
        url = '/search-index.json',
        indexes = {}
    } = options;

    // index name -> url of its JSON file; "default" falls back to url
    const indexUrls = {default: url, ...indexes};

    const triggerEls = Array.from(new Set(
        document.querySelectorAll(`${searchButton}, ${searchTriggerInput}, [data-search-indexes]`)
    ));

    // url -> Promise of the fetched documents
    const fetchedDocuments = new Map();
    // sorted urls -> Promise of {index, store}
    const builtIndexes = new Map();
    let activeIndex = null;
    let openRequest = 0;
    let searchQuery = null;
    const searchInputEl = document.querySelector(`${searchField} input[type=search]`)
    let searchResultContainerEl = document.querySelector(resultContainer);
    const snippetLength = 100
    const facetFilterInput = document.querySelector(`${searchField} input[type=checkbox][data-facet-filter]`);

    const searchOverlay = document.getElementById("search-overlay");
    const closeSearch = document.getElementById("search-close");

    let lastFocusedElement = null;

    function getFocusableElements() {
        return Array.from(
            searchOverlay.querySelectorAll('a[href], button, input, [tabindex]:not([tabindex="-1"])')
        ).filter((el) => !el.disabled);
    }

    function trapFocus(e) {
        if (e.key !== "Tab") {
            return;
        }
        const focusable = getFocusableElements();
        if (focusable.length === 0) {
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
            e.preventDefault();
            last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
            e.preventDefault();
            first.focus();
        }
    }

    function triggerUrls(triggerEl) {
        const names = (triggerEl?.dataset.searchIndexes ?? '').split(/\s+/).filter(Boolean);
        const urls = [];
        for (const name of names.length > 0 ? names : ['default']) {
            if (name in indexUrls) {
                urls.push(indexUrls[name]);
            } else {
                console.warn(`Unknown search index "${name}"`);
            }
        }
        return urls.length > 0 ? urls : [indexUrls.default];
    }

    function fetchDocuments(indexUrl) {
        if (!fetchedDocuments.has(indexUrl)) {
            fetchedDocuments.set(indexUrl, fetch(indexUrl).then(response => response.json()));
        }
        return fetchedDocuments.get(indexUrl);
    }

    function loadIndex(urls) {
        const key = [...new Set(urls)].sort().join(' ');
        if (!builtIndexes.has(key)) {
            builtIndexes.set(key, Promise.all(urls.map(fetchDocuments))
                // documents are keyed by page id, so a page present in several indexes is kept once
                .then(all => buildIndex(Object.assign({}, ...all))));
        }
        return builtIndexes.get(key);
    }

    function activateIndex(triggerEl) {
        const request = ++openRequest;
        activeIndex = null;
        enableSearchInput(false);
        loadIndex(triggerUrls(triggerEl))
            .then(index => {
                if (request !== openRequest) {
                    return;
                }
                activeIndex = index;
                enableSearchInput(true);
                searchInputEl.focus();
            })
            .catch(error => console.error('Error loading search index:', error));
    }

    function openSearch(e, triggerEl) {
        e.preventDefault();
        lastFocusedElement = document.activeElement;
        searchOverlay.classList.add("active");
        searchOverlay.setAttribute("aria-hidden", "false");
        searchOverlay.addEventListener("keydown", trapFocus);
        clearSearchResults(true);
        activateIndex(triggerEl);
        setTimeout(() => {
            searchInputEl.value = '';
            searchInputEl.focus();
        }, 50);
    }

    function closeSearchOverlay(e) {
        searchOverlay.classList.remove("active");
        searchOverlay.setAttribute("aria-hidden", "true");
        searchOverlay.removeEventListener("keydown", trapFocus);
        searchInputEl.value = '';
        searchQuery = null;
        if (lastFocusedElement) {
            lastFocusedElement.focus();
            lastFocusedElement = null;
        }
    }

    for (const triggerEl of triggerEls) {
        triggerEl.addEventListener("click", (e) => openSearch(e, triggerEl));
    }
    closeSearch.addEventListener("click", closeSearchOverlay);

    // Close search when clicking on the overlay background
    searchOverlay.addEventListener("click", function(e) {
        if (e.target === searchOverlay) {
            closeSearchOverlay();
        }
    });

    document.addEventListener("keydown", function (e) {
        const isSearchActive = searchOverlay.classList.contains("active");

        // Escape: close search only if active
        if (e.key === "Escape" && isSearchActive) {
            closeSearchOverlay();
        }

        // Cmd+K or Ctrl+K: open search only if not active
        if ((e.metaKey || e.ctrlKey) && e.key === "k" && !isSearchActive) {
            e.preventDefault();
            openSearch(e, triggerEls[0]);
        }
    });

    function decodeHtml(html) {
        var txt = document.createElement("textarea");
        txt.innerHTML = html;
        return txt.value;
    }


    function buildIndex(data) {
        // copy the entries: fetched documents are cached and shared between combinations
        const documents = {};
        for (const [key, entry] of Object.entries(data)) {
            documents[key] = {...entry, id: key, content: decodeHtml(entry.content)};
        }
        const start = performance.now()
        const idx = lunr(function () {
            this.ref('id')
            this.field('title', {boost: 2})
            this.field('summary')
            this.field('tags', {boost: 1.5})
            this.field('content')

            for (const entry of Object.values(documents)) {
                const boost = entry.boost ?? 1
                this.add(entry, {boost})
            }
        })
        searchInputEl.dispatchEvent(
            new CustomEvent('loadedindex', {
                detail: {
                    took: performance.now() - start,
                },
            })
        )
        return {index: idx, store: {documents}}
    }

    function createSearchResult(result, store, searchResultDataset) {
        let currentComponent
        result.forEach(function (item) {
            const doc = store.documents[item.ref]
            const metadata = item.matchData.metadata
            searchResultDataset.appendChild(createSearchResultItem(doc, item, highlightHit(metadata, doc)))
        })
    }

    function highlightHit(searchMetadata, doc) {
        const terms = {}
        for (const term in searchMetadata) {
            const fields = searchMetadata[term]
            for (const field in fields) {
                terms[field] = [...(terms[field] || []), term]
            }
        }
        return {
            pageTitleNodes: highlightPageTitle(doc.title, terms.title || []),
            tagsNodes: highlightTags(doc, terms.tags || []),
            pageContentNodes: highlightText(doc, terms.content || []),
            //pageKeywordNodes: highlightTags(doc, terms.keyword || []),
        }
    }


    function createSearchResultItem(doc, item, highlightingResult) {
        const documentTitle = document.createElement('div')
        documentTitle.classList.add('search-result-document-title')
        highlightingResult.pageTitleNodes.forEach(function (node) {
            let element
            if (node.type === 'text') {
                element = document.createTextNode(node.text)
            } else {
                element = document.createElement('span')
                element.classList.add('search-result-highlight')
                element.innerText = node.text
            }
            documentTitle.appendChild(element)
        })
        const documentHit = document.createElement('div')
        documentHit.classList.add('search-result-document-hit')
        const documentHitContent = document.createElement('div')
        documentHit.appendChild(documentHitContent)
        highlightingResult.pageContentNodes.forEach((node) => createHighlightedText(node, documentHitContent))
        // only show keyword when we got a hit on them
        if (doc.tags && highlightingResult.tagsNodes.length > 1) {
            const documentKeywords = document.createElement('div')
            documentKeywords.classList.add('search-result-keywords')
            const documentKeywordsFieldLabel = document.createElement('span')
            documentKeywordsFieldLabel.classList.add('search-result-keywords-field-label')
            documentKeywordsFieldLabel.innerText = 'keywords: '
            const documentKeywordsList = document.createElement('span')
            documentKeywordsList.classList.add('search-result-keywords-list')
            highlightingResult.tagsNodes.forEach((node) => createHighlightedText(node, documentKeywordsList))
            documentKeywords.appendChild(documentKeywordsFieldLabel)
            documentKeywords.appendChild(documentKeywordsList)
            documentHitContent.appendChild(documentKeywords)
        }

        const documentUrl = document.createElement('div')
        documentUrl.classList.add('search-result-url')
        try {
            documentUrl.textContent = new URL(doc.url).pathname
        } catch (e) {
            documentUrl.textContent = doc.url
        }

        const searchResultItem = document.createElement('a')
        searchResultItem.href = doc.url
        searchResultItem.classList.add('search-result-item')
        searchResultItem.appendChild(documentTitle)
        searchResultItem.appendChild(documentUrl)
        searchResultItem.appendChild(documentHit)
        searchResultItem.addEventListener('mousedown', function (e) {
            e.preventDefault();
        })
        searchResultItem.addEventListener('click', function (e) {
            closeSearchOverlay();
        })
        return searchResultItem
    }


    function createNoResult(text) {
        const searchResultItem = document.createElement('div')
        searchResultItem.classList.add('search-result-item')
        const documentHit = document.createElement('div')
        documentHit.classList.add('search-result-document-hit')
        const message = document.createElement('strong')
        message.innerText = 'No results found for query "' + text + '"'
        documentHit.appendChild(message)
        searchResultItem.appendChild(documentHit)
        return searchResultItem
    }

    function clearSearchResults(reset) {
        if (reset === true) searchInputEl.value = ''
        searchResultContainerEl.innerHTML = ''
        searchQuery = null;
    }

    function filter(result, documents) {
        const facetFilter = facetFilterInput?.checked && facetFilterInput.dataset.facetFilter
        if (facetFilter) {
            const [field, value] = facetFilter.split(':')
            return result.filter((item) => {
                const ids = item.ref.split('-')
                const docId = ids[0]
                const doc = documents[docId]
                return field in doc && doc[field] === value
            })
        }
        return result
    }

    function search(index, documents, queryString) {
        // execute an exact match search
        let query
        let result = filter(
            index.query(function (lunrQuery) {
                const parser = new lunr.QueryParser(queryString, lunrQuery)
                parser.parse()
                query = lunrQuery
            }),
            documents
        )
        if (result.length > 0) {
            return result
        }
        // no result, use a begins with search
        result = filter(
            index.query(function (lunrQuery) {
                lunrQuery.clauses = query.clauses.map((clause) => {
                    if (clause.presence !== lunr.Query.presence.PROHIBITED) {
                        clause.term = clause.term + '*'
                        clause.wildcard = lunr.Query.wildcard.TRAILING
                        clause.usePipeline = false
                    }
                    return clause
                })
            }),
            documents
        )
        if (result.length > 0) {
            return result
        }
        // no result, use a contains search
        result = filter(
            index.query(function (lunrQuery) {
                lunrQuery.clauses = query.clauses.map((clause) => {
                    if (clause.presence !== lunr.Query.presence.PROHIBITED) {
                        clause.term = '*' + clause.term + '*'
                        clause.wildcard = lunr.Query.wildcard.LEADING | lunr.Query.wildcard.TRAILING
                        clause.usePipeline = false
                    }
                    return clause
                })
            }),
            documents
        )
        return result
    }

    function searchIndex(index, store, text) {
        clearSearchResults(false)
        if (text.trim() === '') {
            return
        }
        const result = search(index, store.documents, text)
        const searchResultDataset = document.createElement('div')
        searchResultDataset.classList.add('search-result-dataset')
        searchResultContainerEl.appendChild(searchResultDataset)
        if (result.length > 0) {
            createSearchResult(result, store, searchResultDataset)
        } else {
            searchResultDataset.appendChild(createNoResult(text))
        }
    }

    function confineEvent(e) {
        e.stopPropagation()
    }

    function enableSearchInput(enabled) {
        if (facetFilterInput) {
            facetFilterInput.disabled = !enabled
        }
        searchInputEl.disabled = !enabled
        searchInputEl.title = enabled ? '' : 'Loading index...'
    }

    function isClosed() {
        return searchResultContainerEl.childElementCount === 0
    }

    function executeSearch(query) {
        if (query === searchQuery || !activeIndex) {
            return;
        }
        searchQuery = query;
        try {
            if (!query) return clearSearchResults()
            searchIndex(activeIndex.index, activeIndex.store, query)
            searchResultContainerEl.style.display = 'block';
        } catch (err) {
            if (err instanceof lunr.QueryParseError) {
            } else {
                console.error('Something went wrong while searching', err)
            }
        }
    }

    function toggleFilter(e) {
        searchInputEl.focus()
        if (!isClosed()) {
            executeSearch()
        }
    }

    function initSearch() {
        searchInputEl.addEventListener(
            'keydown',
            (e) => {
                if (e.key === 'Escape' || e.key === 'Esc') clearSearchResults(true);
            }
        )
        searchInputEl.addEventListener(
            'input',
            debounce(function (e) {
                executeSearch(e.target.value.trim());
            }, 300, {'leading': true, 'trailing': true})
        )
        searchInputEl.addEventListener('click', confineEvent)
        searchResultContainerEl.addEventListener('click', confineEvent)
        if (facetFilterInput) {
            facetFilterInput.parentElement.addEventListener('click', confineEvent)
            facetFilterInput.addEventListener('change', toggleFilter)
        }
        document.documentElement.addEventListener('click', (e) => {
            clearSearchResults(true);
        });
    }


    function highlightPageTitle(title, terms) {
        const positions = getTermPosition(title, terms)
        return buildHighlightedText(title, positions, snippetLength)
    }

    function highlightText(doc, terms) {
        const text = doc.content
        const positions = getTermPosition(text, terms)
        return buildHighlightedText(text, positions, snippetLength)
    }

    function highlightTags(doc, terms) {
        const tags = doc.tags
        if (tags) {
            const positions = getTermPosition(tags.toString(), terms)
            return buildHighlightedText(tags.toString(), positions, snippetLength)
        }
        return []
    }

    function getTermPosition(text, terms) {
        const positions = terms
            .map((term) => findTermPosition(lunr, term, text))
            .filter((position) => position.length > 0)
            .sort((p1, p2) => p1.start - p2.start)

        if (positions.length === 0) {
            return []
        }
        return positions
    }

    /**
     * Taken and adapted from: https://github.com/olivernn/lunr.js/blob/aa5a878f62a6bba1e8e5b95714899e17e8150b38/lib/tokenizer.js#L24-L67
     * @param lunr
     * @param text
     * @param term
     * @return {{start: number, length: number}}
     */
    function findTermPosition(lunr, term, text) {
        const str = text.toLowerCase()
        const index = str.indexOf(term)

        if (index >= 0) {
            // extend term until word boundary to return the entire word
            const boundaries = str.substr(index).match(/^[\p{Alpha}]+/u)
            if (boundaries !== null && boundaries.length >= 0) {
                return {
                    start: index,
                    length: boundaries[0].length,
                }
            }
        }

        // Not found
        return {
            start: 0,
            length: 0,
        }
    }

    /**
     * Splitting the text by the given positions.
     * The text within the positions getting the type "mark", all other text gets the type "text".
     * @param {string} text
     * @param {Object[]} positions
     * @param {number} positions.start
     * @param {number} positions.length
     * @param {number} snippetLength Maximum text length for text in the result.
     * @returns {[{text: string, type: string}]}
     */
    function buildHighlightedText(text, positions, snippetLength) {
        const textLength = text.length
        const validPositions = positions.filter(
            (position) => position.length > 0 && position.start + position.length <= textLength
        )

        if (validPositions.length === 0) {
            return [
                {
                    type: 'text',
                    text:
                        text.slice(0, snippetLength >= textLength ? textLength : snippetLength) +
                        (snippetLength < textLength ? '...' : ''),
                },
            ]
        }

        const orderedPositions = validPositions.sort((p1, p2) => p1.start - p2.start)
        const range = {
            start: 0,
            end: textLength,
        }
        const firstPosition = orderedPositions[0]
        if (snippetLength && text.length > snippetLength) {
            const firstPositionStart = firstPosition.start
            const firstPositionLength = firstPosition.length
            const firstPositionEnd = firstPositionStart + firstPositionLength

            range.start = firstPositionStart - snippetLength < 0 ? 0 : firstPositionStart - snippetLength
            range.end = firstPositionEnd + snippetLength > textLength ? textLength : firstPositionEnd + snippetLength
        }
        const nodes = []
        if (firstPosition.start > 0) {
            nodes.push({
                type: 'text',
                text: (range.start > 0 ? '...' : '') + text.slice(range.start, firstPosition.start),
            })
        }
        let lastEndPosition = 0
        const positionsWithinRange = orderedPositions.filter(
            (position) => position.start >= range.start && position.start + position.length <= range.end
        )

        for (const position of positionsWithinRange) {
            const start = position.start
            const length = position.length
            const end = start + length
            if (lastEndPosition > 0) {
                // create text Node from the last end position to the start of the current position
                nodes.push({
                    type: 'text',
                    text: text.slice(lastEndPosition, start),
                })
            }
            nodes.push({
                type: 'mark',
                text: text.slice(start, end),
            })
            lastEndPosition = end
        }
        if (lastEndPosition < range.end) {
            nodes.push({
                type: 'text',
                text: text.slice(lastEndPosition, range.end) + (range.end < textLength ? '...' : ''),
            })
        }

        return nodes
    }

    /**
     * Creates an element from a highlightingResultNode and add it to the targetNode.
     * @param {Object} highlightingResultNode
     * @param {String} highlightingResultNode.type - type of the node
     * @param {String} highlightingResultNode.text
     * @param {Node} targetNode
     */
    function createHighlightedText(highlightingResultNode, targetNode) {
        let element
        if (highlightingResultNode.type === 'text') {
            element = document.createTextNode(highlightingResultNode.text)
        } else {
            element = document.createElement('span')
            element.classList.add('search-result-highlight')
            element.innerText = highlightingResultNode.text
        }
        targetNode.appendChild(element)
    }


    // the search input stays disabled until a trigger opens the search and its index is loaded
    enableSearchInput(false);
    initSearch();
}

window.setupSearch = setupSearch;
