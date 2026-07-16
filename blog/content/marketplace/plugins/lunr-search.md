---
title: Lunr Search
description: Add instant full-text search to your site using Lunr.
layout: marketplace-plugin
icon: fa-solid fa-magnifying-glass
install-name: lunr
tags: [search]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/lunr
search-boost: 1.2
---

Enable search for your site without the need for external, server-side, search services.

### Setup

1. Add the search index JSON:

{|
```html
<!-- content/search-index.json -->
{#include fm/search-index.json}
```
|}

2. Inject the search script in the `<head>` of your layout. For example with the default theme:

{|
```html
<!-- templates/layouts/default.html -->
---
theme-layout: default
---

{#insert /}

{#head}
{#search-script /}
{/}
```
|}

3. Inject the search overlay in the `<body>` and search button in the navigation:

{|
```html
<!-- templates/layouts/main.html -->
---
theme-layout: main
---

{#search-overlay /}
{#insert /}

{#menu}
{#search-button /}
{#include partials/roq-default/sidebar-menu menu=cdi:menu.items /}
{/}
```
|}

### Custom search trigger

The `{#search-button /}` component renders a plain `<div id="search-button" class="search-button">`. You can replace it with your own HTML element as long as it has `id="search-button"` — the click handler binds to that ID.

```html
<button id="search-button" class="my-search-btn" aria-label="Search">⌘K</button>
```

The search overlay also responds to the **Cmd+K** (macOS) / **Ctrl+K** (Windows/Linux) keyboard shortcut out of the box.

### Controlling indexing

You can prevent content from being indexed:

```yaml
---
title: I don't want to be indexed
search: false
---
```

You can also boost specific pages or layouts in the results using `search-boost`:

```yaml
---
title: Important Page
search-boost: 1.2
---
```

### How boost works

Search relevance is calculated using the [BM25](https://en.wikipedia.org/wiki/Okapi_BM25) algorithm. The `search-boost` value is a **multiplier** on the BM25 score. The default is `1`.

**Use values between `0` and `2`:**

| Value | Effect |
|-------|--------|
| `0.5` | Demote a page in results |
| `1` | Default (no boost) |
| `1.2` | Gentle boost (recommended for reference pages) |
| `1.5` | Moderate boost |
| `2` | Maximum recommended boost |

BM25 term frequency saturates quickly (controlled by k1=1.2). This means the relevance advantage from having more keyword matches is bounded:

| Matches | BM25 score | Ratio vs 1 match |
|---------|-----------|-------------------|
| 1 | 1.00 | 1.00 |
| 2 | 1.38 | 1.38 |
| 3 | 1.57 | 1.57 |
| 5 | 1.77 | 1.77 |
| 9 | 1.94 | 1.94 |

A page with 9 matches scores ~1.94x higher than one with 1 match. If boost exceeds this ratio, it overrides keyword relevance. That is why **values above `2` are not recommended**: they would make boost more important than actual keyword matches.

With a boost of `1.2`, a boosted page only outranks a non-boosted page when their keyword relevance is within 20% of each other. Stronger keyword matches always win.

Section headings also receive a tiny additive boost (h2: +0.06, h3: +0.05, down to h6: +0.02). This orders sections within the same page without pushing them above full pages.
