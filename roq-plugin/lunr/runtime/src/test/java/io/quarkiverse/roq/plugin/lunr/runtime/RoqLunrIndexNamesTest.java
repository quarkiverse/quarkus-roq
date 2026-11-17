package io.quarkiverse.roq.plugin.lunr.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;

class RoqLunrIndexNamesTest {

    @Test
    void membershipDefaultsToDefaultIndexWhenMissing() {
        assertThat(RoqLunrIndexNames.membership(null)).containsExactly("default");
    }

    @Test
    void membershipDefaultsToDefaultIndexWhenEmptyString() {
        assertThat(RoqLunrIndexNames.membership("  ")).containsExactly("default");
    }

    @Test
    void membershipDefaultsToDefaultIndexWhenEmptyList() {
        assertThat(RoqLunrIndexNames.membership(new JsonArray())).containsExactly("default");
    }

    @Test
    void membershipReadsSingleName() {
        assertThat(RoqLunrIndexNames.membership("recipes")).containsExactly("recipes");
    }

    @Test
    void membershipReadsFrontMatterList() {
        assertThat(RoqLunrIndexNames.membership(new JsonArray().add("posts").add("default")))
                .containsExactly("posts", "default");
    }

    @Test
    void membershipReadsCommaSeparatedString() {
        assertThat(RoqLunrIndexNames.membership("posts, default")).containsExactly("posts", "default");
    }

    @Test
    void resolveKeepsKnownNamesInRequestedOrder() {
        var resolved = RoqLunrIndexNames.resolve("recipes,posts", List.of("default", "posts", "recipes"));
        assertThat(resolved.names()).containsExactly("recipes", "posts");
        assertThat(resolved.unknown()).isEmpty();
    }

    @Test
    void resolveRemovesDuplicates() {
        var resolved = RoqLunrIndexNames.resolve("posts posts", List.of("default", "posts"));
        assertThat(resolved.names()).containsExactly("posts");
    }

    @Test
    void resolveDropsAndReportsUnknownNames() {
        var resolved = RoqLunrIndexNames.resolve("posts,nope", List.of("default", "posts"));
        assertThat(resolved.names()).containsExactly("posts");
        assertThat(resolved.unknown()).containsExactly("nope");
    }

    @Test
    void resolveExpandsAllToEveryKnownIndex() {
        var resolved = RoqLunrIndexNames.resolve("all", List.of("default", "posts", "recipes"));
        assertThat(resolved.names()).containsExactly("default", "posts", "recipes");
    }

    @Test
    void resolveReturnsNothingForMissingValue() {
        var resolved = RoqLunrIndexNames.resolve(null, List.of("default"));
        assertThat(resolved.names()).isEmpty();
        assertThat(resolved.unknown()).isEmpty();
    }

    @Test
    void resolveReadsFrontMatterList() {
        var resolved = RoqLunrIndexNames.resolve(new JsonArray().add("posts"), List.of("default", "posts"));
        assertThat(resolved.names()).containsExactly("posts");
    }

    @Test
    void validNamesAreLettersDigitsDashAndUnderscore() {
        assertThat(RoqLunrIndexNames.isValidName("recipes")).isTrue();
        assertThat(RoqLunrIndexNames.isValidName("my_index-2")).isTrue();
    }

    @Test
    void namesWithMarkupOrSeparatorsAreInvalid() {
        assertThat(RoqLunrIndexNames.isValidName("</script>")).isFalse();
        assertThat(RoqLunrIndexNames.isValidName("a b")).isFalse();
        assertThat(RoqLunrIndexNames.isValidName("")).isFalse();
    }

    @Test
    void reservedAllIsNotAValidIndexName() {
        assertThat(RoqLunrIndexNames.isValidName("all")).isFalse();
    }
}
