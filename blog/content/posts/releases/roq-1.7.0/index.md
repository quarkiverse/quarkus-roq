---
title: "Roq 1.7.0"
layout: release
description: AsciiDoc becomes a first-class citizen with full header support, includes, xref, dynamic TOC, and fragment-based search indexing.
author: ia3andy
tags: release, minor, breaking
date: 2025-08-04 19:30:00 +0000
released-version: 1.7.0
---

The AsciiDoc support was already available, but with this release we made it a top-level citizen:

- **AsciiDoc headers** to control Roq data
- **Includes** support
- **Roq page and site attributes** (URLs, ...)
- **`xref`** working out of the box for structured content such as docs
- **Fine-grained AsciiDoc attributes** (config, layout, page)
- **Harmonization** between Ruby and Java implementations
- **Dynamic TOC** support

The [Roq AsciiDoc plugin doc]({=site.url('/plugin/asciidoc/')}) has all the details.

We also improved the [search plugin]({=site.url('/plugin/lunr-search/')}), which now indexes content by fragment for more precise results, with support for both AsciiDoc and Markdown output.

## Migration guide

| Applies to | Before | After | Action |
|---|---|---|---|
| AsciiDoc users | Qute parsing enabled by default | Disabled by default | Use `:qute:` per page or `quarkus.asciidoc.qute=false` |
| AsciiDoc users | `quarkus.asciidoctorj` | `quarkus.asciidoc` | Rename config |
| AsciiDoc users | `quarkus.asciidoctorj.templates-dir` | Removed (TOC handled by script) | Remove config |
| Search users | Previous search result DOM | Updated DOM structure | Verify custom search styles |
| All users | `site.ignored-files` replaces defaults | Now extends `site.default-ignored-files` | Check ignore config |
