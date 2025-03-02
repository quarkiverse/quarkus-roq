import lunr from 'lunr';
import hljs from 'highlight.js';
import 'highlight.js/scss/a11y-dark.scss';

hljs.highlightAll();

var idx = null;
var documents = null;
const searchInput = document.getElementById('search')
const searchResultContainer = document.createElement('div')
const snippetLength = 100
searchResultContainer.classList.add('search-result-dropdown-menu')
searchInput.parentNode.appendChild(searchResultContainer)
const facetFilterInput = document.querySelector('#search-field input[type=checkbox][data-facet-filter]')


var feedLoaded = function (e) {
    idx = lunr(function () {
        this.ref('id')
        this.field('title', {boost: 10})
        this.field('summary')
        this.field('tags', {boost: 50})
        this.field('content', {boost: 100})
        documents = JSON.parse(e.target.response)

        for (const [key, entry] of Object.entries(documents)) {
            entry.id = key
            this.add(entry)
        }
    })
    console.log('idx ready')
    initSearch()
}

var xhr = new XMLHttpRequest

xhr.open('get', '/feed.json')
xhr.addEventListener('load', feedLoaded)
xhr.send()

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
    const documentHitLink = document.createElement('a')
    documentHitLink.href = doc.url
    documentHit.appendChild(documentHitLink)
    highlightingResult.pageContentNodes.forEach((node) => createHighlightedText(node, documentHitLink))
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
        documentHitLink.appendChild(documentKeywords)
    }

    const searchResultItem = document.createElement('div')
    searchResultItem.classList.add('search-result-item')
    searchResultItem.appendChild(documentTitle)
    searchResultItem.appendChild(documentHit)
    searchResultItem.addEventListener('mousedown', function (e) {
        e.preventDefault()
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
    if (reset === true) searchInput.value = ''
    searchResultContainer.innerHTML = ''
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
    searchResultContainer.appendChild(searchResultDataset)
    if (result.length > 0) {
        createSearchResult(result, store, searchResultDataset)
    } else {
        searchResultDataset.appendChild(createNoResult(text))
    }
}

function confineEvent(e) {
    e.stopPropagation()
}

function debounce(func, wait) {
    let timeout
    return function () {
        const context = this
        const args = arguments
        const later = function () {
            timeout = null
            func.apply(context, args)
        }
        timeout = setTimeout(later, wait)
    }
}

function enableSearchInput(enabled) {
    if (facetFilterInput) {
        facetFilterInput.disabled = !enabled
    }
    searchInput.disabled = !enabled
    searchInput.title = enabled ? '' : 'Loading index...'
}

function isClosed() {
    return searchResultContainer.childElementCount === 0
}

function executeSearch(index) {
    const query = searchInput.value
    try {
        if (!query) return clearSearchResults()
        searchIndex(index.index, index.store, query)
        searchResultContainer.style.display = 'block';
    } catch (err) {
        if (err instanceof lunr.QueryParseError) {
        } else {
            console.error('Something went wrong while searching', err)
        }
    }
}

function toggleFilter(e, index) {
    searchInput.focus()
    if (!isClosed()) {
        executeSearch(index)
    }
}

function initSearch(lunr, data) {
    const start = performance.now()
    const index = {index: idx, store: {documents: documents}}
    enableSearchInput(true)
    searchInput.dispatchEvent(
        new CustomEvent('loadedindex', {
            detail: {
                took: performance.now() - start,
            },
        })
    )
    searchInput.addEventListener(
        'keydown',
        debounce(function (e) {
            if (e.key === 'Escape' || e.key === 'Esc') return clearSearchResults(true)
            if (e.which <= 90 && e.which >= 48) {
                executeSearch(index)
            }

        }, 300)
    )
    searchInput.addEventListener('click', confineEvent)
    searchResultContainer.addEventListener('click', confineEvent)
    if (facetFilterInput) {
        facetFilterInput.parentElement.addEventListener('click', confineEvent)
        facetFilterInput.addEventListener('change', (e) => toggleFilter(e, index))
    }
    document.documentElement.addEventListener('click', clearSearchResults)
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


// disable the search input until the index is loaded
enableSearchInput(false)

