---
title: Svelte
description: Build interactive components with Svelte and embed them in your Roq site
layout: marketplace-web
icon: fa-brands fa-js
install-name: svelte
tags: [components, javascript]
source: https://github.com/quarkiverse/quarkus-web-bundler/tree/main/svelte
---

Add Svelte component support to your Roq project. Build interactive UI components with Svelte's reactive framework and embed them in your static pages.

### Getting started

Create `.svelte` files in your `web/` directory:

{|
```html
<!-- web/components/Counter.svelte -->
<script>
  let count = 0;
</script>

<button on:click={() => count++}>
  Clicks: {count}
</button>
```
|}

Then mount the component in your templates using Web Bundler's script injection.