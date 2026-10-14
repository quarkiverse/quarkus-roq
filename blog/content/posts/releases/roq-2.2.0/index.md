---
title: "Roq 2.2.0"
layout: release
description: The Code theme, no-index-all key, HTML sitemap, configurable share links, and AsciiDoc improvements.
author: ia3andy
tags: release, minor, breaking
date: 2026-10-14 12:00:00 +0000
released-version: 2.2.0
---

**Highlights:**

- **[The Code]({=site.url('/theme/the-code-theme/')}) theme**: a minimal, developer-focused theme with Tailwind v4, dark mode, syntax highlighting, TOC, and share links
- **[Releases page]({=site.url('/releases/')})**: release notes are now tag-based blog posts with their own layout, badges, and a dedicated listing page
- **`no-index-all` frontmatter key**: opt a page out of sitemap, llms.txt, search index, and robots in one go
- **HTML sitemap** for visitors alongside the XML sitemap
- **Configurable sitemap `lastmod`** source with faster git lookup
- **Share links rewritten**: configurable per-page with `share-xxx` keys, Twitter migrated to X, LinkedIn added
- **`sort` virtual method** for sorting collections in templates
- **Dots preserved in URL placeholders** (`:name`, `:slug`) for versioned URLs like `/releases/roq-2.1.0/`
- **`<html lang>` from frontmatter** instead of hardcoded "en"
- **Boolean AsciiDoc attributes** with `=true` syntax (e.g. `sectanchors=true`)
- **AsciiDoc base dir and safe mode** now configurable
- **`asciidoc-l10n`** plugin renamed from `asciidoc-jruby-l10n` (relocation POMs provided)
- **Nested AsciiDoc includes** processing fixed
- **Editor fixes**: branch tracking for git sync, refresh race prevention, absolute image path handling

## Releases page

Release notes have moved from a single static page to individual blog posts, each tagged with `release` and using a dedicated `release` layout. The [releases page]({=site.url('/releases/')}) provides a browsable listing with version badges (major, minor, patch) and links to the corresponding GitHub releases.

Each release post lives in `content/posts/releases/roq-X.Y.Z/index.md` with a `released-version` frontmatter key. The whole flow is automated: when a release is published, the CI generates a release post (with the correct version, date, and GitHub link) if one doesn't already exist. Draft release notes written ahead of time are preserved and updated with the final release metadata.

## Migration guide

| Applies to | Before | After | Action |
|---|---|---|---|
| Theme users | `brand` template global (`brand.get(...)`, `brand.entrySet()`) | `social:share(site.data)` and `social:contact(site.data)` | Update share link templates |
| Theme users | Share links always shown | Configurable with `share-xxx: false` to disable per platform | Good to know |
| Theme users | `social-twitter` | Still works (mapped to X) | No action needed |
| Theme developers | Web resources in `web/` | Moved to `web/app/` | Update resource paths |
| Plugin users | `quarkus-roq-plugin-asciidoc-jruby-l10n` | `quarkus-roq-plugin-asciidoc-l10n` | Update dependency (relocation POM handles the transition) |
