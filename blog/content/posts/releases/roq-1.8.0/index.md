---
title: "Roq 1.8.0"
layout: release
description: Internal API refactor for safely including files from the whole site directory when using AsciidocJ.
author: ia3andy
tags: release, patch, breaking
date: 2025-08-08 12:00:00 +0000
released-version: 1.8.0
---

This release includes a refactor to allow safely including files from the whole site directory when using AsciidocJ (requested by a user).

This mostly changes internal API, but can eventually break really specific usage.

## Migration guide

| Applies to | Before | After | Action |
|---|---|---|---|
| All users | `page.info()` | `page.source()` and `page.source().template()` | Update page info calls |
| Plugin authors | Previous BuildItems API | Restructured BuildItems | Update plugin code |
