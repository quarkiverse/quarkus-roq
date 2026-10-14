---
title: "Roq 2.0.0"
layout: release
description: Lightning filesystem watcher, web directory at root, simplified app structure with TailwindCSS support, and directory-based data iteration.
author: ia3andy
tags: release, major, breaking
date: 2025-12-05 14:30:00 +0000
released-version: 2.0.0
---

Roq 2.0 brings significant improvements to the developer experience and project structure:

- **Lightning filesystem watcher** for live reload
- **`web` directory at root** of the Roq site
- **Simplified default app structure**: supports `web/app.js` and `web/app.scss` (or `web/app/app.js` like before)
- **[TailwindCSS](https://docs.quarkiverse.io/quarkus-web-bundler/dev/integrations.html#tailwindcss) support** without any config
- **Directory support for data** and iteration on nested data files using the directory name

## Migration guide

| Applies to | Before | After | Action |
|---|---|---|---|
| All users | Nested data mapped with `_` (e.g. `dir_bar`) | Mapped with `/` (e.g. `dir/bar`) | Update data references |
