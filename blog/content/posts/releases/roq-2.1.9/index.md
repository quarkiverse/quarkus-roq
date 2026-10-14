---
title: "Roq 2.1.9"
layout: release
description: Fix URL parent path handling and routing test isolation for Quarkus 3.39+.
author: ia3andy
tags: release, patch
date: 2026-09-02 14:54:16 +0000
released-version: 2.1.9
---

**Fixes:**

- Fix `getParentPath` to not mangle URLs with host and scheme
- Isolate routing test config to prevent dev mode test failure on Quarkus 3.39+

See the [GitHub Release](https://github.com/quarkiverse/quarkus-roq/releases/tag/2.1.9) for the full changelog.
