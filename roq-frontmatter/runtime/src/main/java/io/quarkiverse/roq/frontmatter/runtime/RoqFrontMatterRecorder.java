package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqFrontMatterRecorder {

    public RuntimeValue<Page> createPage(Page page) {
        return new RuntimeValue<>(page);
    }

    public Supplier<RoqCollections> createRoqCollections(Map<String, List<RuntimeValue<Page>>> collections) {
        return new Supplier<RoqCollections>() {
            @Override
            public RoqCollections get() {
                final var c = new HashMap<String, RoqCollection>();
                for (Map.Entry<String, List<RuntimeValue<Page>>> e : collections.entrySet()) {
                    List<Page> pages = new ArrayList<>();
                    for (RuntimeValue<Page> v : e.getValue()) {
                        pages.add(v.getValue());
                    }
                    c.put(e.getKey(), new RoqCollection(pages));
                }
                return new RoqCollections(Map.copyOf(c));
            }
        };
    }
}
