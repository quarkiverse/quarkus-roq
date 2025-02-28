import lunr from 'lunr';
import hljs from 'highlight.js';
import 'highlight.js/scss/a11y-dark.scss';

hljs.highlightAll();

var idx = null;
var documents = null;
const searchInput = document.getElementById('search')
const searchResultContainer = document.createElement('dialog')
searchResultContainer.classList.add('search-result-dropdown-menu')
searchInput.parentNode.appendChild(searchResultContainer)
const facetFilterInput = document.querySelector('#search-field input[type=checkbox][data-facet-filter]')


var feedLoaded = function (e) {
    idx = lunr(function () {
        this.ref('id')
        this.field('title', {boost: 10})
        this.field('summary')
        this.field('categories', {boost: 50})
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

function createSearchResult (result, store, searchResultDataset) {
    let currentComponent
    result.forEach(function (item) {
        const doc = store.documents[item.ref]
        let sectionTitle
        searchResultDataset.appendChild(createSearchResultItem(doc, sectionTitle, item))
    })
}

function createSearchResultItem (doc, sectionTitle, item) {
    const documentHitLink = document.createElement('a')
    documentHitLink.href = doc.url
    documentHitLink.text = doc.title
    const searchResultItem = document.createElement('div')
    searchResultItem.classList.add('search-result-item')
    searchResultItem.appendChild(documentHitLink)
    searchResultItem.addEventListener('mousedown', function (e) {
        e.preventDefault()
    })
    return searchResultItem
}

function createNoResult (text) {
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

function clearSearchResults (reset) {
    if (reset === true) searchInput.value = ''
    searchResultContainer.innerHTML = ''
}

function filter (result, documents) {
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

function search (index, documents, queryString) {
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

function searchIndex (index, store, text) {
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

function confineEvent (e) {
    e.stopPropagation()
}

function debounce (func, wait, immediate) {
    let timeout
    return function () {
        const context = this
        const args = arguments
        const later = function () {
            timeout = null
            if (!immediate) func.apply(context, args)
        }
        const callNow = immediate && !timeout
        clearTimeout(timeout)
        timeout = setTimeout(later, wait)
        if (callNow) func.apply(context, args)
    }
}

function enableSearchInput (enabled) {
    if (facetFilterInput) {
        facetFilterInput.disabled = !enabled
    }
    searchInput.disabled = !enabled
    searchInput.title = enabled ? '' : 'Loading index...'
}

function isClosed () {
    return searchResultContainer.childElementCount === 0
}

function executeSearch (index) {
    const query = searchInput.value
    try {
        if (!query) return clearSearchResults()
        searchIndex(index.index, index.store, query)
        searchResultContainer.showModal()
    } catch (err) {
        if (err instanceof lunr.QueryParseError) {
        } else {
            console.error('Something went wrong while searching', err)
        }
    }
}

function toggleFilter (e, index) {
    searchInput.focus()
    if (!isClosed()) {
        executeSearch(index)
    }
}

function initSearch (lunr, data) {
    const start = performance.now()
    const index = { index: idx, store: {documents:documents} }
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
            executeSearch(index)
        }, 100)
    )
    searchInput.addEventListener('click', confineEvent)
    searchResultContainer.addEventListener('click', confineEvent)
    if (facetFilterInput) {
        facetFilterInput.parentElement.addEventListener('click', confineEvent)
        facetFilterInput.addEventListener('change', (e) => toggleFilter(e, index))
    }
    document.documentElement.addEventListener('click', clearSearchResults)
}

// disable the search input until the index is loaded
enableSearchInput(false)

