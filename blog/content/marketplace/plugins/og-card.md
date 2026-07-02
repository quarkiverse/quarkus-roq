---
title: OG Card
description: Generate 1200×630 social preview PNGs from Qute SVG templates
layout: marketplace-plugin
icon: fa-solid fa-image
install-name: og-card
tags: [seo]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/og-card
---

Generate 1200×630 Open Graph preview cards from Qute SVG templates and inject `og:image` metadata automatically.

## Installation

```shell
roq add plugin:og-card
```

Or add the Maven dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-og-card</artifactId>
    <version>${quarkus-roq.version}</version>
</dependency>
```

## Configuration

```properties
quarkus.roq.plugin.og-card.include-paths=/about/
quarkus.roq.plugin.og-card.collections=posts
quarkus.roq.plugin.og-card.template=og-card/default-card.svg
quarkus.roq.plugin.og-card.site-name=My Site
```

Place custom card templates under `templates/og-card/`. See the [plugin documentation](/plugin/og-card/) for the full configuration reference.

## Viewing your cards

In dev mode, open `http://localhost:8080/og/about.png`. With generator batch enabled, PNGs are written to `target/roq/og/`.
