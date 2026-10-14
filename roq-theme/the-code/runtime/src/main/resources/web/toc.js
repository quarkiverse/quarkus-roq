(function () {
    'use strict'

    var sidebar = document.querySelector('.content-toc')
    if (!sidebar) return
    if (document.querySelector('body.-toc')) return sidebar.parentNode.removeChild(sidebar)
    var levels = parseInt(sidebar.dataset.levels || 2, 10)
    if (levels < 0) return

    var articleSelector = '.article-content'
    var article = document.querySelector(articleSelector)
    if (!article) return

    var menu = sidebar.querySelector('.toc-menu')
    if (!menu) (menu = document.createElement('div')).className = 'toc-menu'

    var lastActiveFragment
    var clickedAt = 0
    var links = {}

    if (!menu.querySelector('ul')) {
        var headingsSelector = []
        for (var level = 2; level <= Math.min(levels + 1, 6); level++) {
            headingsSelector.push(articleSelector + ' h' + level + '[id]')
        }
        var headings = find(headingsSelector.join(','), article.parentNode)
        if (!headings.length) return sidebar.parentNode.removeChild(sidebar)

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

        var title = document.createElement('h3')
        title.textContent = sidebar.dataset.title || 'Table of Contents'
        menu.appendChild(title)
        menu.appendChild(list)
    }

    find('a[href^="#"]', menu).forEach(function (link) {
        links[link.getAttribute('href')] = link
    })

    var headingsSelector = []
    for (var level = 2; level <= Math.min(levels + 1, 6); level++) {
        headingsSelector.push(articleSelector + ' h' + level + '[id]')
    }
    var headings = find(headingsSelector.join(','), article.parentNode)

    window.addEventListener('load', function () {
        onScroll()
        window.addEventListener('scroll', onScroll)
    })

    function setActive (fragment) {
        if (fragment === lastActiveFragment) return
        if (lastActiveFragment) links[lastActiveFragment].classList.remove('is-active')
        if (fragment && links[fragment]) {
            links[fragment].classList.add('is-active')
            var menuEl = sidebar.querySelector('.toc-menu')
            var listEl = menuEl && menuEl.querySelector('ul')
            if (listEl && listEl.scrollHeight > listEl.offsetHeight) {
                listEl.scrollTop = Math.max(0, links[fragment].offsetTop + links[fragment].offsetHeight - listEl.offsetHeight)
            }
        }
        lastActiveFragment = fragment
    }

    function onScroll () {
        if (Date.now() - clickedAt < 500) return
        var buffer = getNumericStyleVal(document.documentElement, 'fontSize') * 1.15
        var activeFragment
        headings.some(function (heading) {
            if (heading.getBoundingClientRect().top - buffer > 0) return true
            activeFragment = '#' + heading.id
        })
        setActive(activeFragment)
    }

    menu.addEventListener('click', function (e) {
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
