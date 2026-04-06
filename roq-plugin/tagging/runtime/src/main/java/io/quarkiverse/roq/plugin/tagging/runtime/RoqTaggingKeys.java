package io.quarkiverse.roq.plugin.tagging.runtime;

/**
 * Frontmatter keys for the Roq Tagging plugin.
 */
public interface RoqTaggingKeys {
    /**
     * Tagging config on layout — e.g. {@code tagging: {collection: posts}}
     * <br>
     * ▸ Scope: page (layout)
     * <br>
     * ▸ Access: {@code page.data.get("tagging")}
     */
    String TAGGING = "tagging";

    /**
     * Tag value injected into generated tag pages
     * <br>
     * ▸ Scope: page (generated)
     * <br>
     * ▸ Access: {@code page.data.getString("tag")}
     */
    String TAG = "tag";

    /**
     * Tag collection name injected into generated tag pages
     * <br>
     * ▸ Scope: page (generated)
     * <br>
     * ▸ Access: {@code page.data.getString("tagCollection")}
     */
    String TAG_COLLECTION = "tagCollection";
}
