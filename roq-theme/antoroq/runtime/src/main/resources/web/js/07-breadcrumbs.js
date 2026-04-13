;(function () {
  'use strict'

  function truncate (toolbar, breadcrumbs, truncationLength) {
    if (toolbar.scrollHeight === toolbar.getBoundingClientRect().height) return
    breadcrumbs = breadcrumbs.slice(1, -1).reverse()
    while (breadcrumbs.length && toolbar.scrollHeight > toolbar.getBoundingClientRect().height) {
      var breadcrumb = breadcrumbs.pop()
      var text = (breadcrumb = breadcrumb.querySelector('a') || breadcrumb).innerText.trim()
      if (text.length > truncationLength) {
        breadcrumb.setAttribute('title', text)
        var offset = truncationLength - 3
        var truncatedText = text.slice(0, offset) + (text.charAt(offset) === ' ' ? ' ' : '') + '...'
        breadcrumb.innerText = truncatedText
      }
    }
  }

  function onResize (toolbar, breadcrumbs, truncationLength) {
    breadcrumbs.forEach(function (breadcrumb) {
      if (!(breadcrumb = breadcrumb.querySelector('a') || breadcrumb).hasAttribute('title')) return
      breadcrumb.innerText = breadcrumb.getAttribute('title')
      breadcrumb.removeAttribute('title')
    })
    truncate(toolbar, breadcrumbs, truncationLength)
  }

  ;(function () {
    var toolbar = document.querySelector('.toolbar')
    if (!toolbar) return
    var breadcrumbs = Array.prototype.slice.call(toolbar.querySelectorAll('.breadcrumbs li') || [])
    if (breadcrumbs.length < 3) return
    var truncationLength = parseInt(toolbar.querySelector('.breadcrumbs').dataset.truncationLength || 15, 10)
    if (truncationLength < 0) return
    truncate(toolbar, breadcrumbs, truncationLength)
    window.addEventListener('resize', onResize.bind(null, toolbar, breadcrumbs, truncationLength))
  })()
})()
