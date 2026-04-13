---
title: Tailwind CSS
description: Use Tailwind CSS utility classes in your Roq site with automatic purging and optimized builds
layout: marketplace-web
icon: fa-solid fa-wind
install-name: tailwind
tags: [css, styling, tailwind]
source: https://github.com/quarkiverse/quarkus-web-bundler/tree/main/tailwind
---

Add Tailwind CSS support to your Roq project. Write utility-first CSS classes directly in your templates, with automatic purging of unused styles for optimized production builds.

### Configuration

Tailwind configuration is automatic (detecting site content and templates) via the Quarkus Web Bundler.

### Getting started

After installing, create a CSS file that imports Tailwind:

{|
```css
@import "tailwindcss";
@plugin "@tailwindcss/typography";
```
|}

The `@tailwindcss/typography` plugin provides the `prose` class for beautifully styled content rendering (used by Roq for markdown and AsciiDoc output).

Then use Tailwind classes in your templates:

{|
```html
<div class="flex items-center gap-4 p-6 bg-white dark:bg-gray-800 rounded-lg shadow">
  <h2 class="text-xl font-bold text-gray-900 dark:text-white">Hello Tailwind</h2>
</div>
```
|}