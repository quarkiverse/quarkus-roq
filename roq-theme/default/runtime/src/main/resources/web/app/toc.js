(function () {
    'use strict'

    var sidebar = document.querySelector('.content-toc')
    if (!sidebar) return
    if (document.querySelector('body.-toc')) return sidebar.parentNode.removeChild(sidebar)
    var levels = parseInt(sidebar.dataset.levels || 2, 10)
    if (levels < 0) return

    var articleSelector = '.asciidoc-prose'
    var article = document.querySelector(articleSelector)
    var isAsciidoc = !!article

    if (!article) {
        articleSelector = '.content-prose'
        article = document.querySelector(articleSelector)
    }
    if (!article) return

    var headingsSelector = []
    if (isAsciidoc) {
        // AsciiDoc-specific heading selectors
        for (var level = 0; level <= levels; level++) {
            var headingSelector = [articleSelector]
            if (level) {
                for (var l = 1; l <= level; l++) headingSelector.push((l === 2 ? '.sectionbody>' : '') + '.sect' + l)
                headingSelector.push('h' + (level + 1) + '[id]' + (level > 1 ? ':not(.discrete)' : ''))
            } else {
                headingSelector.push('h1[id].sect0')
            }
            headingsSelector.push(headingSelector.join('>'))
        }
    } else {
        // Markdown/generic heading selectors (h2 through h[levels+1])
        for (var level = 2; level <= Math.min(levels + 1, 6); level++) {
            headingsSelector.push(articleSelector + ' h' + level + '[id]')
        }
    }
    var headings = find(headingsSelector.join(','), article.parentNode)
    if (!headings.length) return sidebar.parentNode.removeChild(sidebar)

    var lastActiveFragment
    var clickedAt = 0
    var links = {}
    var list = headings.reduce(function (accum, heading) {
        var link = document.createElement('a')
        link.textContent = heading.textContent
        links[(link.href = '#' + heading.id)] = link
        var listItem = document.createElement('li')
        listItem.dataset.level = parseInt(heading.nodeName.slice(1), 10) - 1
        listItem.appendChild(link)
        accum.appendChild(listItem)
        return accum
    }, document.createElement('ul'))

    var menu = sidebar.querySelector('.toc-menu')
    if (!menu) (menu = document.createElement('div')).className = 'toc-menu'

    var title = document.createElement('h3')
    title.textContent = sidebar.dataset.title || 'Contents'
    menu.appendChild(title)
    menu.appendChild(list)

    var startOfContent = !document.getElementById('toc') && article.querySelector('h1.page ~ :not(.is-before-toc)')
    if (startOfContent) {
        var embeddedToc = document.createElement('aside')
        embeddedToc.className = 'toc embedded'
        embeddedToc.appendChild(menu.cloneNode(true))
        startOfContent.parentNode.insertBefore(embeddedToc, startOfContent)
    }

    window.addEventListener('load', function () {
        onScroll()
        window.addEventListener('scroll', onScroll)
    })

    function setActive (fragment) {
        if (fragment === lastActiveFragment) return
        if (lastActiveFragment) links[lastActiveFragment].classList.remove('is-active')
        if (fragment && links[fragment]) {
            links[fragment].classList.add('is-active')
            if (list.scrollHeight > list.offsetHeight) {
                list.scrollTop = Math.max(0, links[fragment].offsetTop + links[fragment].offsetHeight - list.offsetHeight)
            }
        }
        lastActiveFragment = fragment
    }

    function onScroll () {
        if (Date.now() - clickedAt < 500) return
        var buffer = getNumericStyleVal(document.documentElement, 'fontSize') * 1.15
        var ceil = article.offsetTop
        var activeFragment
        headings.some(function (heading) {
            if (heading.getBoundingClientRect().top + getNumericStyleVal(heading, 'paddingTop') - buffer > ceil) return true
            activeFragment = '#' + heading.id
        })
        if (!activeFragment && headings.length) {
            activeFragment = '#' + headings[headings.length - 1].id
        }
        setActive(activeFragment)
    }

    list.addEventListener('click', function (e) {
        var link = e.target.closest('a')
        if (!link) return
        var fragment = link.getAttribute('href')
        if (fragment) {
            clickedAt = Date.now()
            setActive(fragment)
        }
    })

    function find (selector, from) {
        return [].slice.call((from || document).querySelectorAll(selector))
    }

    function getNumericStyleVal (el, prop) {
        return parseFloat(window.getComputedStyle(el)[prop])
    }
})()