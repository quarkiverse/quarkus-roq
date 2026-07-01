package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.runtime.DefaultRoqPageRenderer;
import io.quarkiverse.roq.frontmatter.runtime.RoqPageRenderer;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.qute.Template;
import io.quarkus.runtime.LaunchMode;

@Singleton
@Alternative
@Priority(1)
public class RoqCachingRenderer implements RoqPageRenderer {

    private final DefaultRoqPageRenderer delegate;
    private final RoqCacheManager cacheManager;
    private final boolean cachingEnabled;

    @Inject
    public RoqCachingRenderer(Instance<DefaultRoqPageRenderer> delegate, RoqCacheManager cacheManager,
            RoqHybridConfig config) {
        this.delegate = delegate.get();
        this.cacheManager = cacheManager;
        this.cachingEnabled = !LaunchMode.current().isDevOrTest() || config.cacheInDevMode();
    }

    @Override
    public CompletionStage<String> render(Page page, Template template, String locale) {
        if (!cachingEnabled) {
            return delegate.render(page, template, locale);
        }

        RoqHybridConfig.CacheMode cacheMode = cacheManager.getCacheMode(page);
        if (cacheMode == RoqHybridConfig.CacheMode.FALSE) {
            return delegate.render(page, template, locale);
        }

        String cacheKey = cacheManager.cacheKey(page);
        RoqCacheStore.CacheEntry entry = cacheManager.get(cacheKey);
        if (entry != null) {
            long ttl = cacheManager.getTtlMillis(page);
            if (entry.isOutOfDate(ttl)) {
                cacheManager.invalidate(cacheKey);
            } else {
                return CompletableFuture.completedFuture(entry.content());
            }
        }

        return delegate.render(page, template, locale).thenApply(result -> {
            if (result != null) {
                cacheManager.put(cacheKey, result);
            }
            return result;
        });
    }
}
