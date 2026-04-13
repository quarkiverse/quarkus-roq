---
title: Lunr Search
description: Add instant full-text search to your site using Lunr.
layout: marketplace-plugin
icon: fa-solid fa-magnifying-glass
install-name: lunr
tags: [search]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/lunr
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