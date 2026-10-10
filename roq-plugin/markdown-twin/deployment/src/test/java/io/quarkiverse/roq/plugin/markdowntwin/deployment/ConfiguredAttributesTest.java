package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class ConfiguredAttributesTest {

    private final RoqPluginMarkdownTwinProcessor processor = new RoqPluginMarkdownTwinProcessor();

    @Test
    void trueBecomesAnEmptyValueAndOtherValuesAreKept() {
        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "quarkus.asciidoc.attributes.sectanchors", "true",
                        "quarkus.asciidoc.attributes.icons", "font",
                        "quarkus.asciidoc.attributes.\"!toc\"", "",
                        "quarkus.asciidoc.safe-mode", "safe"), "test", 100))
                .build();

        assertThat(processor.configuredAttributes(config))
                .containsExactlyInAnyOrderEntriesOf(Map.of("sectanchors", "", "icons", "font"));
    }
}
