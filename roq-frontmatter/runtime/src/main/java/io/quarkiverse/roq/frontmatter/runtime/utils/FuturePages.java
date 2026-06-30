package io.quarkiverse.roq.frontmatter.runtime.utils;

import java.time.ZonedDateTime;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;

public final class FuturePages {

    private FuturePages() {
    }

    /**
     * Check if a page with the given date should be hidden because it is scheduled for later.
     * Returns false (not hidden) when global or collection-level future is enabled.
     */
    public static boolean isFutureDateEnforced(RoqSiteConfig config, ConfiguredCollection collection,
            ZonedDateTime date) {
        if (date == null) {
            return false;
        }
        if (config.future()) {
            return false;
        }
        if (collection != null && collection.future()) {
            return false;
        }
        return date.isAfter(ZonedDateTime.now());
    }
}
