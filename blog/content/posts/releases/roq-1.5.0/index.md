---
title: "Roq 1.5.0"
layout: release
description: Custom favicon support and renamed site.root-path to site.path-prefix.
author: ia3andy
tags: release, minor, breaking
date: 2025-04-24 13:48:47 +0000
released-version: 1.5.0
---

**Highlights:**

- Custom favicon support
- More static file types allowed

## Migration guide

| Applies to | Before | After | Action |
|---|---|---|---|
| All users | `site.root-path` | `site.path-prefix` | Rename configuration property |
| All users | `fileExists` / `imageExists` | Renamed methods | Update template calls |

See the [GitHub Release](https://github.com/quarkiverse/quarkus-roq/releases/tag/1.5.0) for the full changelog.
