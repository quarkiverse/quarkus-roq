---
title: Prism
description: Self-hosted syntax highlighting for code blocks, powered by Prism.js
layout: marketplace-plugin
icon: fa-solid fa-code
install-name: prism
tags: [content]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/prism
---

Bundles [Prism.js](https://prismjs.com/) syntax highlighting into your site at
build time. The plugin reads Prism's source files from the `org.mvnpm:prismjs`
JAR, picks only the languages you ask for (resolving transitive `require`
prereqs from `components.json`), and emits a single self-hosted bundle at
`/static/bundle/prism.{js,css}` — no Node toolchain, no third-party CDN.

### Setup

1. Configure the languages you want bundled (and optionally a theme):

{|
```properties
# application.properties
quarkus.roq.prism.languages=java,bash,properties,yaml,json
quarkus.roq.prism.theme=tomorrow
```
|}

2. Insert the `{#prism /}` Qute tag in your layout's `<head>`:

{|
```html
<!-- templates/layouts/default.html -->
{#head}
{#prism /}
{/}
```
|}

The bundle's script auto-runs `Prism.highlightAll()` once the DOM is parsed, so
any `<pre><code class="language-X">…</code></pre>` produced by your
Markdown/AsciiDoc renderer gets highlighted automatically.

### Configuration

| Property                      | Description                                                                                                                                                                        | Default    |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| `quarkus.roq.prism.languages` | Canonical names from Prism's [components.json](https://github.com/PrismJS/prism/blob/master/components.json). See [Supported languages](https://prismjs.com/#supported-languages). | *required* |
| `quarkus.roq.prism.theme`     | One of `default`, `coy`, `dark`, `funky`, `okaidia`, `solarizedlight`, `tomorrow`, `twilight`.                                                                                     | `default`  |
