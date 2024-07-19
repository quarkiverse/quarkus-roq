package io.quarkiverse.roq.data.deployment.items;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class ParentMappingBuildItem extends MultiBuildItem {
    private final DotName parentType;
    private final DotName listType;
    private final Object json;

    public ParentMappingBuildItem(DotName parentType, DotName listType, Object json) {
        this.parentType = parentType;
        this.listType = listType;
        this.json = json;
    }

    public DotName getListType() {
        return listType;
    }

    public DotName getParentType() {
        return parentType;
    }

    public Object getJson() {
        return json;
    }
}
