---
name: quarkus-roq-hybrid
description: "Enable hybrid mode for Roq: runtime page caching, future page scheduling, and cache management service."
categories: web
---

# Quarkus Roq Hybrid Plugin

The hybrid plugin adds runtime caching to Roq, bridging the gap between static site generation and dynamic rendering. Pages are rendered once and cached (in memory or on disk), making subsequent requests as fast as serving static files while still supporting dynamic CDI content.

## Adding the Extension

Add the `quarkus-roq-plugin-hybrid` dependency to your project. No code changes are required for basic caching.

## Cache Modes

Each page can use one of three cache modes, set globally or per-page via frontmatter:

- `lazy` (default): rendered on first request, cached for subsequent requests
- `startup`: pre-rendered at application startup, served from cache immediately
- `false`: never cached, rendered on every request (use for pages with dynamic CDI/DB content)

**Global config:**
```properties
site.hybrid.cache-mode=lazy
```

**Per-page override via frontmatter:**
```yaml
---
title: My Dynamic Page
cache: false
---
```

Per-page TTL can also be set via frontmatter:
```yaml
---
title: Semi-Dynamic Page
cache: lazy
cache-ttl: 5m
---
```

## Cache Stores

Two cache backends are available:

### Filesystem (default)
Rendered HTML is written to disk. Survives restarts, low memory usage, similar to static generation.

```properties
site.hybrid.cache-store=filesystem
site.hybrid.cache-dir=/path/to/cache   # optional, defaults to temp directory
```

Writes are atomic (write-to-temp then rename), so readers never see partial content.

### Memory
Caffeine-backed in-memory cache with LRU eviction. Faster than filesystem, lost on restart.

```properties
site.hybrid.cache-store=memory
site.hybrid.cache-max-size=1000   # max cached pages (default: 1000)
site.hybrid.cache-ttl=5m          # optional TTL (e.g. 5m, 1h, 30s)
```

## Future Pages

When the hybrid plugin is present, future-dated pages are automatically included at build time and date-checked at runtime. A page with a future date returns 404 until its scheduled date, then becomes available automatically without a rebuild. No configuration needed.

## Dev Mode

Caching is automatically disabled in dev and test mode so that template changes are always reflected immediately. To test caching behavior during development:

```properties
site.hybrid.cache-in-dev-mode=true
```

## HTTP Caching

The handler sets `Last-Modified` headers on all responses and supports `304 Not Modified` via `If-Modified-Since`, following the same pattern as Vert.x StaticHandler.

## Cache Management Service

The `RoqCacheManager` CDI bean is available for injection. Use it to build your own cache management endpoints or integrate with your application logic.

**Available methods:**

- `get(String key)` returns the cached entry (content + lastModified) or null
- `put(String key, String value)` stores a rendered page
- `invalidate(String key)` removes a specific cached page
- `invalidateAll()` clears the entire cache
- `size()` returns the number of cached entries
- `hitRate()` returns the cache hit rate (memory store only)
- `getCacheMode(Page page)` resolves the cache mode for a page (frontmatter or global config)
- `cacheKey(Page page)` computes the cache key for a page (the page id)

**Example: custom cache invalidation endpoint:**

```java
@Path("/_admin/cache")
@RolesAllowed("admin")
public class CacheAdminResource {

    @Inject
    RoqCacheManager cacheManager;

    @POST
    @Path("/invalidate")
    public Response invalidateAll() {
        cacheManager.invalidateAll();
        return Response.ok().build();
    }

    @POST
    @Path("/invalidate/{path: .+}")
    public Response invalidatePath(@PathParam("path") String path) {
        cacheManager.invalidate(path);
        return Response.ok().build();
    }

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stats() {
        return Response.ok(Map.of(
            "size", cacheManager.size(),
            "hitRate", cacheManager.hitRate()
        )).build();
    }
}
```

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `site.hybrid.cache-mode` | `lazy` | Default cache mode: `lazy`, `startup`, or `false` |
| `site.hybrid.cache-store` | `filesystem` | Cache backend: `filesystem` or `memory` |
| `site.hybrid.cache-dir` | (temp dir) | Directory for filesystem cache (filesystem store only) |
| `site.hybrid.cache-max-size` | `1000` | Max cached pages (memory store only) |
| `site.hybrid.cache-ttl` | (none) | Time-to-live for cached entries, e.g. `5m`, `1h` |
| `site.hybrid.cache-build-id` | `${quarkus.uuid}` | Build identifier for filesystem cache subdirectory. Set to a fixed value (e.g. git hash) to persist cache across restarts |
| `site.hybrid.cache-in-dev-mode` | `false` | Enable caching in dev/test mode |

Per-page frontmatter `cache: lazy|startup|false` overrides the global `cache-mode`.
Per-page frontmatter `cache-ttl: 5m` overrides the global `cache-ttl` (e.g. `30s`, `5m`, `1h`).

## When to Use Each Cache Mode

| Scenario | Cache Mode |
|----------|-----------|
| Static content (blog posts, docs) | `lazy` or `startup` |
| Pages with CDI beans or DB queries | `false` or `lazy` with a TTL |
| Pages that must always be fresh | `false` |
| Landing pages with high traffic | `startup` |

Pages with `cache: false` in frontmatter always bypass the cache, regardless of the global setting.
