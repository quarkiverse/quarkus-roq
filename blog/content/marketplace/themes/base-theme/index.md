---
title: Base Theme
description: Minimal built-in theme with SEO, favicon, and Web Bundler. The ideal starting point to build a fully custom site from scratch.
layout: marketplace-theme
icon: fa-solid fa-cube
install-name: base
tags: [minimal, starter]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-frontmatter/runtime/src/main/resources/templates/theme-layouts/roq-base
---

The base theme is a minimal starting point included with Roq. It provides the essential HTML structure with SEO, favicon, and Web Bundler support, giving you full control over your site's design. This is the ideal choice when you want to build a fully custom site from scratch.

### Layouts

```
default                 // Base HTML structure (SEO, favicon, bundle)
├── page                // Simple page with title
└── post                // Post with title and date
```


{|
The `default` layout provides the HTML skeleton with:
- `{#seo /}` for meta tags, Open Graph, and Twitter cards
- `{#favicon /}` for automatic favicon discovery
- `{#bundle /}` for CSS and JS bundling via Web Bundler
|}

The `page` and `post` layouts extend `default` with minimal markup (title, content, and date for posts).

### Customization

Since the base theme provides only the HTML structure, you style everything through your own CSS in `web/app.css`. The starter CSS includes basic variables for colors, typography, and a simple card layout that you can replace entirely.

See the [Favicon](/docs/basics/#favicon), [SEO](/docs/basics/#seo), and [Analytics](/docs/basics/#analytics) documentation for configuring the built-in tags.
