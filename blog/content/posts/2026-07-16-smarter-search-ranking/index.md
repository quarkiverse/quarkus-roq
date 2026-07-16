---
title: Smarter Search Ranking
description: How we fixed search boost to let keyword relevance shine.
image: https://images.unsplash.com/photo-1520500807606-4ac9ae633574?q=80&w=1200&auto=format&fit=crop
author: ia3andy
tags: improvement,plugin
date: 2026-07-16 14:00:00 +0200
---

Searching for "qr code" on this site used to show the plugin reference page first, with the blog post buried far below. The reference page had `search-boost: 20`, making it score 20x higher regardless of keyword matches. Blog posts about QR codes could never compete.

### Why boost was too strong

Lunr uses [BM25](https://en.wikipedia.org/wiki/Okapi_BM25) for scoring, where term frequency saturates quickly. A page with 9 keyword matches scores only ~1.94x higher than one with 1 match. Any `search-boost` above 2 overrides keyword relevance entirely.

### What changed

- **Boost values reduced to the BM25 range.** Marketplace pages now use `search-boost: 1.2` instead of `20`. The reference doc still ranks above unrelated pages, but a blog post with strong keyword matches now appears right after it.
- **Section heading boost fixed.** h2 sections now rank above h6 (was inverted). Sections are slightly demoted (h2: ×0.96, h6: ×0.92) while full pages get a 10% boost, ensuring pages always rank above their own sections.
- **Field boosts rebalanced.** All field boosts (title, tags, content) are now between 1 and 2, letting BM25 handle relevance naturally.
- **Search results now show the page URL**, making it easier to see where each result links before clicking.

### Using search-boost

Keep values between 0 and 2. A boost of `1.2` gives a gentle advantage. Above `2`, boost starts overriding keyword matches. See the [Lunr Search plugin docs](/plugin/lunr-search/) for details.
