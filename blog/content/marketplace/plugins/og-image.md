---
title: OG Image
description: Generate 1200×630 social preview PNGs from Qute SVG templates
layout: marketplace-plugin
icon: fa-solid fa-image
install-name: og-image
tags: [seo]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/og-image
---

Generate 1200×630 Open Graph preview cards from Qute SVG templates and inject `og:image` metadata automatically.

## Installation

```shell
roq add plugin:og-image
```

Or add the Maven dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-og-image</artifactId>
    <version>${quarkus-roq.version}</version>
</dependency>
```

## Configuration

```properties
quarkus.roq.plugin.og-image.enabled=true
quarkus.roq.plugin.og-image.include-paths=/about/
quarkus.roq.plugin.og-image.collections=posts
quarkus.roq.plugin.og-image.template=og-image/default-card.svg
quarkus.roq.plugin.og-image.site-name=My Site
```

Place custom card templates under `templates/og-image/`. See the [plugin documentation](/plugin/og-image/) for the full configuration reference.

## Viewing your cards

In dev mode, open `http://localhost:8080/og/about.png`. With generator batch enabled, PNGs are written to `target/roq/og/`.
