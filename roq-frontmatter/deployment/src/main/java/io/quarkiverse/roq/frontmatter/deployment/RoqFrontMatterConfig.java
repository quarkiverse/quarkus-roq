package io.quarkiverse.roq.frontmatter.deployment;

import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.roq.frontmatter")
public interface RoqFrontMatterConfig {

    String INCLUDES_DIR = "_layouts,_includes";
    Map<String, String> DEFAULT_COLLECTIONS = Map.of("_posts", "posts");

    /**
     * The directory names containing includes and layouts (in the Roq site directory)
     */
    @WithDefault(INCLUDES_DIR)
    List<String> includesDirs();

    /**
     * The directory names containing collections as key and the corresponding collection name as value (in the Roq site
     * directory)
     */
    @ConfigDocDefault("_posts=post")
    @WithDefault("")
    Map<String, String> collections();

    default Map<String, String> collectionsOrDefaults() {
        if (collections().isEmpty()) {
            return DEFAULT_COLLECTIONS;
        }
        return collections();
    };

}
