---
title: Sitemap
description: Generate an XML sitemap for search engines and an HTML one for visitors
layout: marketplace-plugin
icon: fa-solid fa-sitemap
install-name: sitemap
tags: [seo]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/sitemap
search-boost: 1.2
---

Easily create a sitemap for your site: `sitemap.xml` for search engines, and an HTML page for visitors.

## XML sitemap

Create a new sitemap file:

{|
```html
{#include fm/sitemap.xml /}
```
|}

Browse `http://localhost:8080/sitemap.xml` to verify.

## HTML sitemap

Create a new sitemap page, e.g. `sitemap.html`, with a layout:

{|
```html
---
title: Sitemap
layout: page
---
{#include fm/sitemap.html /}
```
|}

This produces a `<nav class="roq-sitemap">` with a `Pages` group for your loose pages, followed by one group per
collection (documents in the collection's natural order, newest first). Groups with nothing to show are left out,
and the page holding the include never links to itself.

```html
<nav class="roq-sitemap">
  <section class="roq-sitemap-group roq-sitemap-pages">
    <h2>Pages</h2>
    <ul>
      <li><a href="/">Home</a></li>
      <li><a href="/about/">About</a></li>
    </ul>
  </section>
  <section class="roq-sitemap-group roq-sitemap-collection" data-collection="posts">
    <h2>Posts</h2>
    <ul>
      <li><a href="/posts/hello-roq/">Hello Roq</a></li>
    </ul>
  </section>
</nav>
```

Style it through the `.roq-sitemap` class, or target a group with `.roq-sitemap-pages`, `.roq-sitemap-collection`
or `[data-collection="posts"]`.

## Excluding pages

Use `sitemap: false` in the FM data to keep a page or a document out of both sitemaps. The page holding the HTML
include is left out of its own listing, but still appears in `sitemap.xml` so search engines can index it.
