---
title: Sitemap
description: Generate an XML sitemap automatically so search engines index every page
layout: marketplace-plugin
icon: fa-solid fa-sitemap
install-name: sitemap
tags: [seo]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/sitemap
---

Easily create a `sitemap.xml` for your site.

Create a new sitemap file:

{|
```html
<!-- content/sitemap.xml -->
{#include fm/sitemap.xml}
```
|}

To remove pages from the sitemap, use `sitemap: false` in the FM data.

Browse `http://localhost:8080/sitemap.xml` to verify.