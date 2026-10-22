package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.vertx.core.json.JsonObject;

class ConfiguredAttributesTest {

    private final RoqPluginMarkdownTwinProcessor processor = new RoqPluginMarkdownTwinProcessor();

    @Test
    void trueBecomesAnEmptyValueAndOtherValuesAreKept() {
        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "quarkus.asciidoc.attributes.sectanchors", "true",
                        "quarkus.asciidoc.attributes.icons", "font",
                        "quarkus.asciidoc.attributes.\"!toc\"", "true",
                        "quarkus.asciidoc.safe-mode", "safe"), "test", 100))
                .build();

        // The unset marker is kept for the converter, which applies it as Asciidoctor does; it is written with the
        // value true, as any flag, because SmallRye Config drops a property whose value is empty.
        assertThat(processor.configuredAttributes(config))
                .containsExactlyInAnyOrderEntriesOf(Map.of("sectanchors", "", "icons", "font", "!toc", ""));
    }

    @Test
    void frontMatterAttributesAreWrittenAsText() {
        final JsonObject data = new JsonObject().put("asciidoc-attributes",
                new JsonObject().put("icons", "image").put("release", 2.2).put("sectanchors", true));
        assertThat(processor.frontMatterAttributes(data))
                .containsExactlyInAnyOrderEntriesOf(Map.of("icons", "image", "release", "2.2", "sectanchors", "true"));
        // A list is written as the page writes it, from the raw map, not as JSON.
        assertThat(processor.frontMatterAttributes(new JsonObject().put("asciidoc-attributes",
                new JsonObject().put("list", List.of("a", "b"))))).containsExactlyEntriesOf(Map.of("list", "[a, b]"));
        assertThat(processor.frontMatterAttributes(new JsonObject())).isEmpty();
        assertThat(processor.frontMatterAttributes(new JsonObject().put("asciidoc-attributes", "not a map"))).isEmpty();
    }
}
