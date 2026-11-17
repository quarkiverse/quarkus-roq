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

The search index is generated at build time and served at `/search-index.json`, no index file is needed. To take it over, for example to add front matter, add `content/search-index.json`; it replaces the generated one:

{|
```html
<!-- content/search-index.json (optional) -->
{#include fm/search-index.json}
```
|}

1. Inject the search script in the `<head>` of your layout. For example with the default theme:

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

2. Inject the search overlay in the `<body>` and search button in the navigation:

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

The search overlay also responds to the **Cmd+K** (macOS) / **Ctrl+K** (Windows/Linux) keyboard shortcut out of the box. It searches with the first search trigger on the page.

### Multiple indexes

By default every searchable page goes into one index. A site with several content areas, such as blog posts and recipes, can give each area its own index, so that someone browsing recipes only finds recipes.

1. Declare the indexes a page appears in with `search-indexes`. Set it on a layout to apply it to every page using that layout, for example a whole collection:

```yaml
# templates/layouts/post.html
---
search-indexes: [posts, default]
---
```

```yaml
# templates/layouts/recipe.html
---
search-indexes: recipes
---
```

A page without `search-indexes` belongs to the `default` index, so a site that never sets the key keeps a single index. `default` is an index like the others, not a superset: in this example recipes are not in `default`, while posts are in both. `search: false` and `no-index-all` still keep a page out of every index. A value set on a page replaces the one from its layout.

The build generates one index per name in use: `default` at `/search-index.json` and every other name at `/search-index/<name>.json`, here `/search-index/posts.json` and `/search-index/recipes.json`. Index names may contain letters, digits, `-` and `_`; any other name is skipped with a warning in the build log. A typo in `search-indexes` is not reported: `search-indexes: recipe` creates an extra `recipe` index instead of adding the page to `recipes`, so check the names passed to `setupSearch` in the page source when a page is missing from an index.

To take over an index, add a content file naming it with `search-index-name`. That name is no longer generated and the file's own URL is used:

{|
```
<!-- content/recipes-search-index.json -->
---
search-index-name: recipes
---
{#include fm/search-index.json /}
```
|}

2. Give each search trigger the indexes it searches with the `indexes` parameter. Without it, a trigger searches `default`:

{|
```html
<!-- recipe pages search recipes -->
{#search-button-input indexes="recipes" /}

<!-- a combination of indexes, searched together -->
{#search-button-input indexes="posts,recipes" /}

<!-- every index -->
{#search-button-input indexes="all" /}
```
|}

`all` is reserved and covers every index, so a global search keeps working when you add new ones. The `indexes` value can also come from front matter, for example from a custom key set on a layout:

{|
```html
{#search-button-input indexes=page.data('search-scope') /}
```
|}

The trigger renders the names as `data-search-indexes="posts recipes"`. A hand-written trigger can use the same attribute; it is bound to the search overlay without needing `id="search-button"`.

An index is downloaded the first time a trigger using it opens the search, and is reused for later searches on the page. When a trigger names several indexes, they are combined into one, and a page that belongs to several of them shows up once. A name that matches no index is ignored with a warning in the build log.

### Controlling indexing

You can prevent content from being indexed:

```yaml
---
title: I don't want to be indexed
search: false
---
```

`no-index-all: true` keeps a page out of the search index and of every other index at once (sitemap, llms.txt, and it
defaults the `robots` meta tag to `noindex`). Set `search: true` on the same page to index it anyway.

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

Full pages receive a 10% boost (×1.1) so they always rank above their own sections. Sections are slightly demoted (h2: ×0.96, h3: ×0.95, down to h6: ×0.92), keeping them ordered by heading level within the same page.
