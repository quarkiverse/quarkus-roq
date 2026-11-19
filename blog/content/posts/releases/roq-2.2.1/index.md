---
title: "Roq 2.2.1 (Quarkus 3.39.5+)"
layout: release
description: Requires Quarkus 3.39.5+. Markdown twins for AI agents, the roq migrate command, theme scaffolding, plugin extra data, and large site fixes.
author: ia3andy
tags: release, patch
date: 2026-11-18 12:00:00 +0000
released-version: 2.2.1
---

> **Important:** Roq 2.2.1 requires **Quarkus 3.39.5 or later**. Update your Quarkus platform version before upgrading Roq.

**Highlights:**

- **Markdown twin plugin**: generates a Markdown twin of each content page for AI agents, advertised with `<link rel="alternate" type="text/markdown">`
- **`roq migrate` CLI command**: migrate existing Jekyll and Gatsby sites, with a validation stage and todo items reported for anything that needs manual work
- **Theme scaffolding command**: scaffold a new theme, standalone or inside a project
- **Plugin extra data**: plugins can contribute page data through `ExtraDataBuildItem`, with arrays merged across plugins
- **Roq data write back**: data beans can be written back to the file they came from, with Jackson time module support
- **Large sites**: fixes for encoding, generator selection and static files serialization on very large sites
- **Theme hot reload** fixed in multi-module projects
- **Resume theme**: copy-link anchors on sections and items, long URLs wrap on phones
- **Editor**: image upload follows page path changes
- **SEO**: any subset of `webmasterVerifications` and `facebook` keys is now allowed
- **Escaping fixes**: no more empty CDATA blocks, `{|` and `|}` handled in escaped content
- Build no longer fails on a site with no sources

Welcome to our new contributor @jnbdz!

See the [GitHub Release](https://github.com/quarkiverse/quarkus-roq/releases/tag/2.2.1) for the full changelog.
