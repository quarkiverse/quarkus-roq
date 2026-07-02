---
title: "Generate Open Graph Images for Social Sharing with Roq"
description: "Create 1200×630 PNG social preview cards from Qute SVG templates and inject og:image metadata automatically."
tags: plugin, new-feature, cool-stuff, seo
image: https://images.unsplash.com/photo-1611162617474-5b21e939e113?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.0.3
author: ia3andy
---

Social networks and chat apps use Open Graph metadata to build link previews. Roq already renders `\{#seo /}` tags from frontmatter — the OG Card plugin closes the loop by generating 1200×630 PNG cards at build time and injecting `og:image` metadata for pages you choose.

Cards are rendered from Qute SVG templates via Apache Batik at build time and published as static PNG files under `/og/`.

## Installation

Add the plugin dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-og-card</artifactId>
    <version>${quarkus-roq.version}</version>
</dependency>
```

Or use the Roq CLI:

```shell
roq add plugin:og-card
```

## Configuration

Choose which pages get generated cards:

```properties
quarkus.roq.plugin.og-card.collections=posts
quarkus.roq.plugin.og-card.include-paths=/about/
quarkus.roq.plugin.og-card.template=og-card/default-card.svg
quarkus.roq.plugin.og-card.site-name=My Site
quarkus.roq.plugin.og-card.output-prefix=/og
```

- `collections` — generate cards for posts in named collections
- `include-paths` — generate cards for standalone pages (e.g. `/about/`)
- `template` — Qute SVG template under `templates/og-card/`
- `site-name` — branding text on the card

Pages with an existing `image:`, `img:`, or `picture:` frontmatter are skipped by default (`skip-if-image-set=true`).

## Custom card template

Create `templates/og-card/my-card.svg` with a fixed 1200×630 viewBox. The plugin passes a `card` data object with pre-wrapped line arrays for multi-line rendering:

```svg
<text x="72" y="170" font-size="52">
  \{#for line in card.titleLines}
  <tspan x="72" dy="\{line_isFirst ? '0' : '62'}">\{line}</tspan>
  \{/for}
</text>
<text x="72" y="260" font-size="28">
  \{#for line in card.descriptionLines}
  <tspan x="72" dy="\{line_isFirst ? '0' : '34'}">\{line}</tspan>
  \{/for}
</text>
<text x="72" y="582">\{card.siteName}</text>
```

Set `max-text-width` to limit how wide text can flow before wrapping — useful when graphics occupy part of the card. The Roq blog dogfoods a branded `roq-card.svg` that inlines the Roq mascot SVG paths and sets `max-text-width=700` to keep text clear of the mascot (Batik cannot resolve external image URLs during render).

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

For the full configuration reference, [check out the doc](/plugin/og-card/).
