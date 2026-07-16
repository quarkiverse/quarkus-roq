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

### Collapsible sections

Use standard HTML `<details>` and `<summary>` tags in your Markdown files for collapsible content. The default theme styles them automatically.

```markdown
<details>
<summary>Click to reveal</summary>

Hidden content with **Markdown formatting**.

</details>
```

See the [Markdown markup test](/markups/markdown/#collapsible-sections) for a live example.
