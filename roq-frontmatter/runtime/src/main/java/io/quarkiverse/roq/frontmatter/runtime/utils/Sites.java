package io.quarkiverse.roq.frontmatter.runtime.utils;

import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Arc;

public final class Sites {
    private Sites() {
    }

    public static Site getSite() {
        return Arc.container().beanInstanceSupplier(Site.class).get().get();
    }

}
