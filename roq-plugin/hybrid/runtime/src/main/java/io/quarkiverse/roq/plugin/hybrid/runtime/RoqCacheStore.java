package io.quarkiverse.roq.plugin.hybrid.runtime;

public interface RoqCacheStore {

    record CacheEntry(String content, long cachedAt) {
        boolean isOutOfDate(long ttlMillis) {
            if (ttlMillis <= 0) {
                return false;
            }
            return System.currentTimeMillis() - cachedAt > ttlMillis;
        }
    }

    CacheEntry get(String key);

    void put(String key, String value);

    void invalidate(String key);

    void invalidateAll();

    long size();
}
