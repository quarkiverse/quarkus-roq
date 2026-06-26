package io.quarkiverse.roq.plugin.prism.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Unit tests for {@link RoqPluginPrismProcessor#resolveLanguages(List, JsonObject)}, the build-time
 * dependency-graph resolver that walks Prism's {@code components.json} {@code require} edges.
 */
class ResolveLanguagesTest {

    /**
     * A minimal subset of Prism's {@code components.json}. Models the {@code require} field
     * as either a String or a JsonArray, plus a couple of leaf languages with no requires.
     */
    private static final JsonObject LANGUAGES_META = new JsonObject()
            .put("clike", new JsonObject())
            .put("markup", new JsonObject())
            .put("java", new JsonObject().put("require", "clike"))
            .put("javascript", new JsonObject().put("require", "clike"))
            .put("markup-templating", new JsonObject().put("require", "markup"))
            .put("bash", new JsonObject())
            .put("yaml", new JsonObject())
            .put("multi", new JsonObject().put("require", new JsonArray().add("clike").add("markup")))
            .put("cycle-a", new JsonObject().put("require", "cycle-b"))
            .put("cycle-b", new JsonObject().put("require", "cycle-a"));

    @Test
    void leafLanguageReturnsItself() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("bash"), LANGUAGES_META);
        assertThat(resolved).containsExactly("bash");
    }

    @Test
    void singleRequirePrecedesDependent() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("java"), LANGUAGES_META);
        assertThat(resolved).containsExactly("clike", "java");
    }

    @Test
    void chainedRequiresAreResolvedInOrder() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("markup-templating"), LANGUAGES_META);
        assertThat(resolved).containsExactly("markup", "markup-templating");
    }

    @Test
    void multipleLanguagesShareTransitiveDeps() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("java", "javascript"), LANGUAGES_META);
        assertThat(resolved).containsExactly("clike", "java", "javascript");
    }

    @Test
    void duplicateRequestsAreDeduplicated() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("java", "java"), LANGUAGES_META);
        assertThat(resolved).containsExactly("clike", "java");
    }

    @Test
    void requireListIsHandled() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("multi"), LANGUAGES_META);
        assertThat(resolved).containsExactly("clike", "markup", "multi");
    }

    @Test
    void inputOrderIsPreservedForIndependentLanguages() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("yaml", "bash"), LANGUAGES_META);
        assertThat(resolved).containsExactly("yaml", "bash");
    }

    @Test
    void unknownLanguageRaisesConfigurationException() {
        assertThatThrownBy(() -> RoqPluginPrismProcessor.resolveLanguages(
                List.of("not-a-real-language"), LANGUAGES_META))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("not-a-real-language");
    }

    @Test
    void cyclicRequireRaisesConfigurationException() {
        assertThatThrownBy(() -> RoqPluginPrismProcessor.resolveLanguages(
                List.of("cycle-a"), LANGUAGES_META))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    void leadingAndTrailingWhitespaceIsTrimmed() {
        LinkedHashSet<String> resolved = RoqPluginPrismProcessor.resolveLanguages(
                List.of("  java  "), LANGUAGES_META);
        assertThat(resolved).containsExactly("clike", "java");
    }
}
