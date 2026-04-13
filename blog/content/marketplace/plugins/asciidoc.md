---
title: AsciiDoc
description: Write content in AsciiDoc with a fast, pure-Java processor
layout: marketplace-plugin
icon: fa-solid fa-file-lines
install-name: asciidoc
tags: [content, markup]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/asciidoc
---

Fast Java-based AsciiDoc processor (based on [Yupiik asciidoc-java](https://github.com/yupiik/tools-maven-plugin/tree/master/asciidoc-java)). Provides fast startup but does not support all AsciiDoc options yet. For the full feature set, see [AsciiDoc JRuby](/plugin/asciidoc-jruby/).

Add the `.adoc` or `.asciidoc` file extension to pages and they will be processed.

### Use Qute in AsciiDoc files

Qute parsing is disabled by default on AsciiDoc files, to enable it:

```properties
quarkus.asciidoc.qute=true
```

> You can also use the `:qute:` AsciiDoc header attribute to enable Qute parsing (or not `:qute: false`) per page.

### AsciiDoc includes

You may use includes from anywhere in the site directory. Make sure the included file is ignored by Roq by prefixing the file or directory with `_`.

```asciidoc
include::_includes/attributes.adoc[]
```

### Headers

AsciiDoc headers are parsed by Roq and used as page data:

- `= Title` is used as page title
- author is available through `page.data.author` and `page.data.author-email`
- revision is available through `page.data.revision.number`, `page.data.revision.date` and `page.data.revision.remark`
- attribute `:description:` is used as page description
- attributes starting with `page-` will be used as page data (`:page-image:` becomes `image` in the data)
- all other header attributes are also available in `page.data.attributes`

> You can also use FrontMatter headers to set the page data like any other page.

### Roq attributes

| Name | Description |
|---|---|
{|
| `{site-url}` | The full site url (e.g. `https://my-site.com/blog/`) |
| `{site-path}` | The site path (e.g. `/blog/`) |
| `{page-url}` | The full page url (e.g. `https://my-site.com/blog/about/`) |
| `{page-path}` | The page path (e.g. `/blog/about`) |
|}

### AsciiDoc attributes configuration

Attributes can be configured globally:

```properties
quarkus.asciidoc.attributes.source-highlighter=highlight.js
quarkus.asciidoc.attributes.icons=font
```

Or as an include file in the AsciiDoc headers, or as part of the Frontmatter data `asciidoc-attributes` in a page or layout:

```yaml
---
asciidoc-attributes:
  notitle: true
---
```

### Table of Contents (TOC)

To add a Table of Contents, use the `page-content-toc` attribute in your AsciiDoc header:

```asciidoc
:page-content-toc: true
:page-content-toc-title: Contents
:page-content-toc-levels: 2
```

This works with the default Roq theme and creates a dynamic sidebar TOC that highlights the current section as you scroll.

### AsciiDoc Data Conversion

Convert data containing AsciiDoc into HTML using the `asciidocToHtml` template extension:

{|
```html
---
bar: |
    == Hello
    * that's nice
    * I can use asciidoc in the data
---

{page.data.bar.asciidocToHtml}
```
|}