package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class MemoryCacheStore implements RoqCacheStore {

    private final Cache<String, CacheEntry> lazyCache;
    private final Map<String, CacheEntry> startupCache = new ConcurrentHashMap<>();

    public MemoryCacheStore(RoqHybridConfig config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(config.cacheMaxSize())
                .recordStats();
        this.lazyCache = builder.build();
    }

    @Override
    public CacheEntry get(String key) {
        CacheEntry entry = startupCache.get(key);
        if (entry != null) {
            return entry;
        }
        return lazyCache.getIfPresent(key);
    }

    @Override
    public void put(String key, String value) {
        lazyCache.put(key, new CacheEntry(value, System.currentTimeMillis()));
    }

    @Override
    public void putStartup(String key, String value) {
        startupCache.put(key, new CacheEntry(value, System.currentTimeMillis()));
    }

    @Override
    public void invalidate(String key) {
        startupCache.remove(key);
        lazyCache.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        startupCache.clear();
        lazyCache.invalidateAll();
    }

    @Override
    public long size() {
        return startupCache.size() + lazyCache.estimatedSize();
    }

    public double hitRate() {
        return lazyCache.stats().hitRate();
    }
}
