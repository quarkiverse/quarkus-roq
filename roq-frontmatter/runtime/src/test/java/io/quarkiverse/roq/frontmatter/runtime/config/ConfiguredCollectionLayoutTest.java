package io.quarkiverse.roq.frontmatter.runtime.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConfiguredCollection - default layout from collection id")
public class ConfiguredCollectionLayoutTest {

    @Test
    @DisplayName("Trailing 's' is stripped to derive default layout (posts -> post)")
    public void testTrailingSStripped() {
        ConfiguredCollection collection = new ConfiguredCollection(
                "posts", false, false, false, null, "/default/", Optional.empty());
        assertEquals("post", collection.layout());
    }

    @Test
    @DisplayName("Default layout uses full id when name does not end with 's'")
    public void testNoTrailingSKeepsId() {
        ConfiguredCollection collection = new ConfiguredCollection(
                "data", false, false, false, null, "/default/", Optional.empty());
        assertEquals("data", collection.layout());
    }

    @Test
    @DisplayName("Single-letter trailing 's' is preserved as id")
    public void testSingleLetterIdPreserved() {
        ConfiguredCollection collection = new ConfiguredCollection(
                "s", false, false, false, null, "/default/", Optional.empty());
        assertEquals("s", collection.layout());
    }

    @Test
    @DisplayName("Explicit layout is preserved over the default")
    public void testExplicitLayoutWins() {
        ConfiguredCollection collection = new ConfiguredCollection(
                "posts", false, false, false, "article", "/default/", Optional.empty());
        assertEquals("article", collection.layout());
    }

    @Test
    @DisplayName("Empty layout falls back to the default")
    public void testEmptyLayoutFallsBackToDefault() {
        ConfiguredCollection collection = new ConfiguredCollection(
                "guides", false, false, false, "", "/default/", Optional.empty());
        assertEquals("guide", collection.layout());
    }

    @Test
    @DisplayName("Null id is rejected with a configuration exception")
    public void testNullIdIsRejected() {
        assertThrows(RoqFrontMatterConfigException.class, () -> new ConfiguredCollection(
                null, false, false, false, null, "/default/", Optional.empty()));
    }
}
