package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.json.JsonObject;

@Recorder
public class RoqFrontMatterRecorder {

    private final RoqSiteConfig config;

    public RoqFrontMatterRecorder(RoqSiteConfig config) {
        this.config = config;
    }

    public Supplier<RoqCollections> createRoqCollections(Map<String, List<Supplier<Page>>> collections) {
        return new Supplier<RoqCollections>() {
            @Override
            public RoqCollections get() {
                final var c = new HashMap<String, RoqCollection>();
                for (Map.Entry<String, List<Supplier<Page>>> e : collections.entrySet()) {
                    List<Page> pages = new ArrayList<>();
                    for (Supplier<Page> v : e.getValue()) {
                        pages.add(v.get());
                    }
                    c.put(e.getKey(), new RoqCollection(pages));
                }
                return new RoqCollections(Map.copyOf(c));
            }
        };
    }

    public Supplier<Page> createPage(String name, JsonObject merged) {
        return new Supplier<Page>() {
            @Override
            public Page get() {
                return new Page(config, name, merged);
            }
        };
    }
}
