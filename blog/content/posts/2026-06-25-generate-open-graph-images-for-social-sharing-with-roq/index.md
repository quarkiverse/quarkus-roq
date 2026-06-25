---
title: "Generate Open Graph Images for Social Sharing with Roq"
description: "Create 1200×630 PNG social preview cards from Qute SVG templates and inject og:image metadata automatically."
tags: plugin, new-feature, cool-stuff, seo
image: https://images.unsplash.com/photo-1611162617474-5b21e939e113?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.0.3
author: ia3andy
---

Social networks and chat apps use Open Graph metadata to build link previews. Roq already renders `\{#seo /}` tags from frontmatter — the OG Image plugin closes the loop by generating 1200×630 PNG cards at build time and injecting `og:image` metadata for pages you choose.

No external Playwright scripts required: cards are rendered from Qute SVG templates via Apache Batik, served on `/og/*.png` routes in dev mode, and written to `target/roq/og/` during generator batch.

## Installation

Add the plugin dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-og-image</artifactId>
    <version>${quarkus-roq.version}</version>
</dependency>
```

Or use the Roq CLI:

```shell
roq add plugin:og-image
```

## Configuration

Enable the plugin and choose which pages get generated cards:

```properties
quarkus.roq.plugin.og-image.enabled=true
quarkus.roq.plugin.og-image.collections=posts
quarkus.roq.plugin.og-image.include-paths=/about/
quarkus.roq.plugin.og-image.template=og-image/default-card.svg
quarkus.roq.plugin.og-image.site-name=My Site
quarkus.roq.plugin.og-image.output-prefix=/og
```

- `collections` — generate cards for posts in named collections
- `include-paths` — generate cards for standalone pages (e.g. `/about/`)
- `template` — Qute SVG template under `templates/og-image/`
- `site-name` — branding text on the card

Pages with an existing `image:`, `img:`, or `picture:` frontmatter are skipped by default (`skip-if-image-set=true`).

## Custom card template

Create `templates/og-image/my-card.svg` with a fixed 1200×630 viewBox. The plugin passes a `card` data object:

```svg
<text x="72" y="170">{card.title}</text>
<text x="72" y="250">{card.description}</text>
<text x="72" y="582">{card.siteName}</text>
```

The Roq blog dogfoods a branded `roq-card.svg` that inlines the Roq mascot SVG paths (Batik cannot resolve external image URLs during render).

## Viewing your cards

In dev mode, browse to a generated PNG directly:

```
http://localhost:8080/og/about.png
```

With generator batch enabled, PNGs land on disk:

```shell
QUARKUS_ROQ_GENERATOR_BATCH=true mvn package quarkus:run
ls target/roq/og/
```

For the full configuration reference, [check out the doc](/plugin/og-image/).
