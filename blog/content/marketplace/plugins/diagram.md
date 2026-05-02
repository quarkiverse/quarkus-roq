---
title: Diagram
description: Render diagrams from code blocks using Kroki (Mermaid, PlantUML, and more)
layout: marketplace-plugin
icon: fa-solid fa-diagram-project
install-name: diagram
tags: [content, media]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/diagram
---

Diagram-as-code support by leveraging [Kroki.io](https://kroki.io/). It delegates image rendering to Kroki either by using a provided server or by popping a dev service.

Please take a look at [the full Kroki reference documentation](https://kroki.io/).

Use it in your content:

{|
```html
{#diagram asciidoc=true language="pikchr" alt="Impossible trident" width=500 height=500 diagramOutputFormat="svg"}
scale = 1.0
eh = 0.5cm
ew = 0.2cm
...
{/}
```
|}

You can either use a deployed server or let the dev services provide one for you, but in this case you won't have [all languages available](https://hub.docker.com/r/yuzutech/kroki).