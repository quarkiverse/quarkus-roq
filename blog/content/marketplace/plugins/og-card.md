---
title: OG Card
description: Generate 1200×630 social preview PNGs from Qute SVG templates
layout: marketplace-plugin
icon: fa-solid fa-image
install-name: og-card
tags: [seo]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/og-card
doc: https://docs.quarkiverse.io/quarkus-roq/dev/quarkus-roq-plugin-og-card.html
---

Generate 1200×630 Open Graph preview cards from Qute SVG templates at build time. Injects `og-image` frontmatter so `{#seo /}` emits `og:image` and `twitter:card=summary_large_image`.

## Installation

```shell
roq add plugin:og-card
```

Or add the Maven dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-og-card</artifactId>
    <version>${quarkus.roq.version}</version>
</dependency>
```

## Configuration

At least one of `collections` or `include-paths` is required:

```properties
quarkus.roq.plugin.og-card.collections=posts
quarkus.roq.plugin.og-card.exclude-paths=/posts/tag/
quarkus.roq.plugin.og-card.include-paths=/about/
quarkus.roq.plugin.og-card.template=og-card/default-card.svg
quarkus.roq.plugin.og-card.site-name=My Site
quarkus.roq.plugin.og-card.output-prefix=/og
quarkus.roq.plugin.og-card.max-text-width=-1
```

- `collections` — generate cards for all documents in named collections (e.g. `posts` → `/og/posts/{slug}.png`)
- `include-paths` — explicit normal pages (e.g. `/about/` → `/og/about.png`)
- `exclude-paths` — when using `collections`, skip path prefixes such as `/posts/tag/` (not applied to `include-paths`)
- `max-text-width` — horizontal pixels for text wrapping (`-1` auto-computes from card width); reduce when graphics occupy part of the card

Pages with `image`, `img`, or `picture` frontmatter are skipped by default (`skip-if-image-set=true`). Map `title`, `description`, and optional `kicker` / `eyebrow` from page frontmatter into the card.

## Card template

Place custom templates under `templates/og-card/`. The plugin passes a `card` object with pre-wrapped `titleLines` and `descriptionLines` for multi-line SVG text:

```svg
<text x="72" y="170" font-size="52">
  \{#for line in card.titleLines}
  <tspan x="72" dy="\{line_isFirst ? '0' : '62'}">\{line}</tspan>
  \{/for}
</text>
```

Bundled default: `og-card/default-card.svg`.

## Viewing your cards

In dev mode, open `http://localhost:8080/og/about.png`.

With generator batch enabled, PNGs are written to `target/roq/og/`:

```shell
QUARKUS_ROQ_GENERATOR_BATCH=true mvn package quarkus:run
```

For CI and containers, set `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` before the build (Batik uses Java2D/AWT).
