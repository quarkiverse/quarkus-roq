package io.quarkiverse.roq.frontmatter.runtime;

import io.vertx.core.json.JsonObject;

public record Site(NormalPage page, java.util.List<NormalPage> pages, RoqCollections collections) implements Page {

    @Override
    public String id() {
        return page.id();
    }

    @Override
    public JsonObject data() {
        return page().data();
    }

    public RoqCollection.Paginator paginator() {
        return page().paginator();
    }

    @Override
    public RootUrl rootUrl() {
        return page.rootUrl();
    }
}