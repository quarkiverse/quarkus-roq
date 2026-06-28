---
title: Lunr Search
description: Add instant full-text search to your site using Lunr.
layout: marketplace-plugin
icon: fa-solid fa-magnifying-glass
install-name: lunr
tags: [search]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/lunr
search-boost: 20
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

You can also boost specific pages or layouts in the results:

```yaml
---
title: I want to be first in the result
search-boost: 30
---
```