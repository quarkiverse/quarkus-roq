---
title: TOC
description: Generate a table of contents from page headings at build time, with no JavaScript required
layout: marketplace-plugin
icon: fa-solid fa-list-ol
install-name: toc
tags: [navigation, seo]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/toc
search-boost: 1.2
---

Generate a table of contents from the headings of your Markdown and AsciiDoc pages. The plugin reads the rendered HTML of each page, collects the headings (`h1` to `h6`) that carry an `id`, nests them by level, and exposes the result to your Qute templates as structured data or as a ready-made `<nav>` block. Everything is rendered on the server, so search engines and AI crawlers see the outline without running JavaScript. All output is HTML-escaped.

The plugin is not part of the `quarkus-roq` aggregate extension, so add it explicitly.

## Installation

```shell
roq add plugin:toc
```

Or add the Maven dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-toc</artifactId>
    <version>${quarkus.roq.version}</version>
</dependency>
```

## Render the default markup

Add {|`{page.tocHtml}`|} to a layout:

{|
```html
<!-- templates/layouts/post.html -->
---
layout: default
---
{page.tocHtml}

{#insert /}
```
|}

This produces a `<nav class="roq-toc" aria-label="Table of contents">` element with nested `<ul>` lists and anchor links. Each `<li>` carries a `data-level` attribute with the 0-indexed depth (`h1` is `0`, `h2` is `1`, and so on), the same convention as the default theme's `toc.js`:

```html
<nav class="roq-toc" aria-label="Table of contents">
<ul>
<li data-level="1"><a href="#introduction">Introduction</a></li>
<li data-level="1"><a href="#getting-started">Getting Started</a>
<ul>
<li data-level="2"><a href="#prerequisites">Prerequisites</a></li>
<li data-level="2"><a href="#installation">Installation</a></li>
</ul>
</li>
</ul>
</nav>
```

Style it through the `.roq-toc` class, or target a depth with `[data-level="N"]`.

## Render custom markup

For full control over the markup, iterate over {|`{page.toc}`|}. Bind it once with {|`{#let}`|}, because every evaluation parses the page again:

{|
```html
{#let toc=page.toc}
{#if toc.size > 0}
<aside class="my-toc">
  <h2>On this page</h2>
  <ul>
  {#for entry in toc}
    <li>
      <a href="#{entry.id}">{entry.title}</a>
      {#if entry.children.size > 0}
      <ul>
        {#for child in entry.children}
        <li><a href="#{child.id}">{child.title}</a></li>
        {/for}
      </ul>
      {/if}
    </li>
  {/for}
  </ul>
</aside>
{/if}
{/let}
```
|}

This example renders two heading levels. Nest more {|`{#for}`|} loops for deeper levels, or use {|`{page.tocHtml}`|}, which renders the whole hierarchy.

Put the expressions in a layout. They also work inside the page content itself, but then the plugin renders that content one more time to find the headings.

## Configuration

Tune the TOC per page with these front matter keys. They are the keys the default theme's client-side TOC reads, so both implementations share one configuration.

| Key | Default | Description |
|-----|---------|-------------|
| `content-toc` | `true` | Set to `false` to suppress the TOC on a page. Applies to both `\{page.toc}` and `\{page.tocHtml}`. |
| `content-toc-levels` | `6`, or `toclevels` + 1 on AsciiDoc pages | Maximum heading level to include (1 to 6). For example, `3` keeps `h1`, `h2`, and `h3`, matching the default theme's JavaScript TOC. |
| `content-toc-title` | `Table of contents` | The `aria-label` of the rendered `<nav>` element. |

When `content-toc-levels` is absent on an AsciiDoc page, the plugin uses the document's `toclevels` attribute plus one, because `toclevels` counts section depth while `sect1` renders as `h2`. It looks in the `asciidoc-attributes` front matter map, then in the attributes Roq parsed from the document header, then at a `:toclevels:` entry in the header itself. With neither set, AsciiDoc pages show two section levels, which is the Asciidoctor default; other pages include all six levels.

```yaml
---
title: My Page
content-toc: false
---
```

## Combine with the default theme's JavaScript TOC

Because {|`{page.tocHtml}`|} emits the same `data-level` convention as the default theme's `toc.js`, you can place the server-rendered TOC inside the theme's `<aside class="content-toc">` wrapper. Crawlers get the full outline; visitors with JavaScript get scroll tracking and active-section highlighting on top:

{|
```html
{#if page.data.content-toc??}
<aside class="content-toc" data-title="Contents" data-levels="2">
  {page.tocHtml}
</aside>
{/if}
```
|}

## Template API

| Expression | Returns | Description |
|------------|---------|-------------|
| `\{page.toc}` | `List<TocEntry>` | The nested TOC entries of the page. Empty when the page has no headings with an `id`, or when `content-toc` is `false`. |
| `\{page.tocHtml}` | `RawString` | The `<nav class="roq-toc">` block described above. Empty under the same conditions. |

Each `TocEntry` has these properties:

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | The heading's `id`, used as the anchor fragment. |
| `title` | `String` | The heading text. |
| `level` | `int` | The heading level, 1 to 6. |
| `children` | `List<TocEntry>` | The nested entries. |
