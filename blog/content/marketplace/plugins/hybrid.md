---
title: Hybrid
description: Runtime page caching, future page scheduling, and cache management for dynamic Roq sites
layout: marketplace-plugin
icon: fa-solid fa-bolt
install-name: hybrid
tags: [performance, caching, dynamic]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/hybrid
search-boost: 20
---

Enable hybrid mode for Roq: pages are rendered once and cached (in memory or on disk), making subsequent requests as fast as serving static files while still supporting dynamic CDI content.

## Cache Modes

Set globally or per-page via frontmatter:

```properties
# application.properties
site.hybrid.cache-mode=lazy
```

```yaml
# Per-page override in frontmatter
---
title: My Dynamic Page
cache: false
---
```

- **lazy** (default): rendered on first request, cached for subsequent requests
- **startup**: pre-rendered at application startup
- **false**: never cached, rendered on every request

## Cache Stores

### Filesystem (default)

Rendered HTML is written to disk. Survives restarts, low memory usage.

```properties
site.hybrid.cache-store=filesystem
site.hybrid.cache-dir=/path/to/cache
```

### Memory

Caffeine-backed in-memory cache with LRU eviction.

```properties
site.hybrid.cache-store=memory
site.hybrid.cache-max-size=1000
site.hybrid.cache-ttl=5m
```

## Future Pages

Future-dated pages are automatically included and date-checked at runtime. A page with a future date returns 404 until its scheduled date, then becomes available without a rebuild.

## Cache Management Service

Inject `RoqCacheManager` to manage the cache from your application code:

```java
@Inject
RoqCacheManager cacheManager;

// Clear the entire cache
cacheManager.invalidateAll();

// Invalidate a specific page
cacheManager.invalidate("posts/my-post/");

// Check cache stats
long size = cacheManager.size();
```

> Caching is automatically disabled in dev mode so that template changes are always reflected immediately. Set `site.hybrid.cache-in-dev-mode=true` to test caching during development.
