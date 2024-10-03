package io.quarkiverse.roq.frontmatter.deployment.record;

import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterCollectionBuildItem extends MultiBuildItem {
    private final String name;
    private final List<Supplier<DocumentPage>> documents;

    public RoqFrontMatterCollectionBuildItem(String name, List<Supplier<DocumentPage>> documents) {
        this.name = name;
        this.documents = documents;
    }

    public String name() {
        return name;
    }

    public List<Supplier<DocumentPage>> documents() {
        return documents;
    }
}
