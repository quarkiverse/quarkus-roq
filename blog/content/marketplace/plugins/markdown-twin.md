---
title: Markdown Twin
description: Publish a Markdown twin of every page for AI agents
layout: marketplace-plugin
icon: fa-solid fa-robot
install-name: markdown-twin
tags: [seo, ai]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/markdown-twin
search-boost: 1.2
---

Publish a Markdown twin of every page, built from the source at build time, so AI agents can read your content without
parsing HTML. A page at `/docs/basics/` gets its twin at `/docs/basics/index.md`; a page with a file name, such as
`/404.html`, gets `/404.html.md`.

- Markdown pages: the Markdown source, with the front matter title as its heading.
- AsciiDoc pages: converted in-process to GitHub-Flavored Markdown, includes and attributes resolved.
- HTML pages and paginated listing pages: no twin.

Every page that got a twin advertises it in its head, so an agent that fetches the HTML page finds the Markdown:

```html
<link rel="alternate" type="text/markdown" href="https://example.com/docs/basics/index.md" />
```

The link comes from the `seoAlternates` tag inside the `seo` tag. A site that overrides `tags/seo.html` needs the
line `{#seoAlternates page site /}` in its copy to show it.

Add the dependency and every page gets a twin. Use `mdtwin: false` in the FM data to keep a page out:

```yaml
---
title: My page
mdtwin: false
---
```

`no-index-all: true` keeps a page out of the twins too, as it does for the sitemap, llms.txt and search. Set
`mdtwin: true` on the same page to publish its twin anyway.

Three build-time options under `quarkus.roq.plugin.markdown-twin` scope the generation, which runs on every dev-mode
restart:

| Option | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | Generates twins. `%dev.quarkus.roq.plugin.markdown-twin.enabled=false` skips them in dev mode; no page advertises a twin then. |
| `include` | unset | Page paths to twin, such as `/guides/**`. Unset means every eligible page. |
| `exclude` | unset | Page paths to skip, such as `/version/**`. Applied after `include`. |

A pattern is a glob unless it starts with `regex:`; a leading or trailing slash is ignored, and a tree pattern such as
`/guides/**` covers the landing page `/guides/` too. The values are comma-separated lists, so a comma inside a glob,
as in `/guides/{http,rest}-*`, must be written as `\,`.

The patterns follow the `site.ignored-files` convention: a bare pattern is a glob, a `glob:` or `regex:` prefix is
honoured, and a leading slash is optional.

A page the converter cannot handle gets no twin and a warning in the build log; the build never fails because of a
twin. Twins are static files, so with the hybrid plugin a page scheduled for a later date gets its twin at the first
build after that date. The twins resolve the same `quarkus.asciidoc.attributes.*` and `asciidoc-attributes` front matter as the rendered
pages, and an `include::` may only read files outside the site directory when `quarkus.asciidoc.safe-mode` is `UNSAFE`,
as for the pages themselves.
