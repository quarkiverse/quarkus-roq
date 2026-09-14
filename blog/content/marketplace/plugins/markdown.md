---
title: Markdown
description: Write content in Markdown with CommonMark support, included by default
layout: marketplace-plugin
icon: fa-brands fa-markdown
install-name: markdown
tags: [content, markup]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/markdown
search-boost: 1.2
---

Process `.md` and `.markdown` files using [CommonMark Java](https://github.com/commonmark/commonmark-java).

> Markdown plugin is already included in Quarkus Roq extension. No separate installation needed unless you removed it.

Every file with `.md` or `.markdown` extension will be processed.

### Extensions

Rendering is powered by [Quarkus Qute Web Markdown](https://docs.quarkiverse.io/quarkus-qute-web/dev/markdown.html), which bundles the following commonmark-java extensions, all enabled by default:

| Extension | Property | Description |
|-----------|----------|-------------|
| Tables | `quarkus.qute.web.markdown.plugin.tables.enabled` | GitHub Flavored Markdown tables |
| Autolink | `quarkus.qute.web.markdown.plugin.autolink.enabled` | Bare URLs and email addresses become links |
| Heading anchor | `quarkus.qute.web.markdown.plugin.heading-anchor.enabled` | Adds an `id` to every heading so it can be linked with `#my-title` |
| Alerts | `quarkus.qute.web.markdown.plugin.alerts.enabled` | GitHub Flavored Markdown alert blocks (`> [!NOTE]`, `> [!TIP]`, ...) |

Set a property to `false` in `application.properties` to disable the corresponding extension:

```properties
quarkus.qute.web.markdown.plugin.autolink.enabled=false
```

Custom alert types can be registered with `quarkus.qute.web.markdown.plugin.alerts.custom-types.<TYPE>=<Title>` (the type must be uppercase):

```properties
quarkus.qute.web.markdown.plugin.alerts.custom-types.INFO=Information
quarkus.qute.web.markdown.plugin.alerts.custom-types.BUG=Known Bug
```

> The heading anchor extension only adds `id` attributes; it does not render a visible link icon. Add your own CSS/JS if you want clickable anchors.

See the [extension configuration reference](https://docs.quarkiverse.io/quarkus-qute-web/dev/markdown.html#extension-configuration-reference) for details.

### Collapsible sections

Use standard HTML `<details>` and `<summary>` tags in your Markdown files for collapsible content. The default theme styles them automatically.

```markdown
<details>
<summary>Click to reveal</summary>

Hidden content with **Markdown formatting**.

</details>
```

See the [Markdown markup test](/markups/markdown/#collapsible-sections) for a live example.
