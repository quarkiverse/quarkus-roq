package io.quarkiverse.roq.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

class FrontMatterTest {

    final YAMLMapper mapper = new YAMLMapper();

    @Test
    void detectsFrontMatter() {
        assertThat(FrontMatter.isPresent("---\ntitle: A\n---\nBody")).isTrue();
        assertThat(FrontMatter.isPresent("---\r\ntitle: A\r\n---\r\nBody")).isTrue();
        assertThat(FrontMatter.isPresent("Body\n---\ntitle: A\n---\n")).isFalse();
        assertThat(FrontMatter.isPresent("No front matter")).isFalse();
    }

    @Test
    void stripsFrontMatter() {
        assertThat(FrontMatter.strip("---\ntitle: A\n---\nBody\n")).isEqualTo("Body\n");
        assertThat(FrontMatter.strip("Body\n")).isEqualTo("Body\n");
    }

    @Test
    void readsFieldsInOrder() throws Exception {
        var data = FrontMatter.read(mapper, "---\ntitle: A\naliases:\n  - /a\n  - /b\n---\nBody");

        assertThat(data.properties()).extracting(e -> e.getKey()).containsExactly("title", "aliases");
        assertThat(data.get("title").asText()).isEqualTo("A");
        assertThat(data.get("aliases")).hasSize(2);
    }

    @Test
    void readsEmptyOrNonObjectFrontMatterAsNoFields() throws Exception {
        assertThat(FrontMatter.read(mapper, "---\n---\nBody")).isEmpty();
        assertThat(FrontMatter.read(mapper, "---\njust text\n---\nBody")).isEmpty();
    }

    @Test
    void shipsJekyllFiltersExtensionSourceAsAResource() {
        // Copied into migrated sites; must be available when the tool runs from a jar
        assertThat(getClass().getResource("/io/quarkus/tools/migration/JekyllFiltersExtension.java")).isNotNull();
    }
}
