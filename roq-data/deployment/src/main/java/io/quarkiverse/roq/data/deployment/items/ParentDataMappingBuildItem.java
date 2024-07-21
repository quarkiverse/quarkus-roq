package io.quarkiverse.roq.data.deployment.items;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Represents the metadata of the class mapped by {@link io.quarkiverse.roq.data.runtime.annotations.DataMapping} class.
 *
 * This {@link io.quarkus.builder.item.BuildItem} is used just to map a data file containing a list:
 *
 * <pre>
 * <code>- name: John Doe
 * - name: Mary Jane
 * </code>
 * </pre>
 */
public final class ParentDataMappingBuildItem extends MultiBuildItem {

    /**
     * Represents the {@link DotName} of parent type.
     */
    private final DotName parentType;

    /**
     * Represents the {@link DotName} of list type.
     */
    private final DotName listType;

    /**
     * Represents the data file content.
     */
    private final Object json;

    public ParentDataMappingBuildItem(DotName parentType, DotName listType, Object json) {
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
