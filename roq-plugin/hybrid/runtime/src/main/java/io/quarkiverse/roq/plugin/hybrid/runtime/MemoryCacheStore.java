package io.quarkiverse.roq.plugin.hybrid.runtime;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class MemoryCacheStore implements RoqCacheStore {

    private final Cache<String, CacheEntry> cache;

    public MemoryCacheStore(RoqHybridConfig config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(config.cacheMaxSize())
                .recordStats();
        config.cacheTtl().ifPresent(builder::expireAfterWrite);
        this.cache = builder.build();
    }

    @Override
    public CacheEntry get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void put(String key, String value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
    }

    @Override
    public void invalidate(String key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Override
    public long size() {
        return cache.estimatedSize();
    }

    public double hitRate() {
        return cache.stats().hitRate();
    }
}
