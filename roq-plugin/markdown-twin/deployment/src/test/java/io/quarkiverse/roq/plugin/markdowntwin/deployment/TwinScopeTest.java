package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Which pages the configured include and exclude patterns keep in scope.
 */
class TwinScopeTest {

    private static TwinScope scope(List<String> include, List<String> exclude) {
        return TwinScope.of(new MarkdownTwinConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Optional<List<String>> include() {
                return include == null ? Optional.empty() : Optional.of(include);
            }

            @Override
            public Optional<List<String>> exclude() {
                return exclude == null ? Optional.empty() : Optional.of(exclude);
            }
        });
    }

    @Test
    void everythingIsInScopeWhenNothingIsConfigured() {
        final TwinScope scope = scope(null, null);
        assertThat(scope.includes("guides/http-reference")).isTrue();
        assertThat(scope.includes("posts/welcome-to-roq/")).isTrue();
    }

    @Test
    void includeLimitsToTheMatchingTrees() {
        final TwinScope scope = scope(List.of("/guides/**", "/posts/**"), null);
        assertThat(scope.includes("guides/http-reference")).isTrue();
        assertThat(scope.includes("posts/welcome-to-roq/")).isTrue();
        assertThat(scope.includes("version/3.15/guides/http-reference")).isFalse();
        assertThat(scope.includes("about")).isFalse();
    }

    @Test
    void excludeWinsOverInclude() {
        final TwinScope scope = scope(List.of("/guides/**"), List.of("/guides/all-config"));
        assertThat(scope.includes("guides/http-reference")).isTrue();
        assertThat(scope.includes("guides/all-config")).isFalse();
    }

    @Test
    void excludeAloneKeepsTheRestInScope() {
        final TwinScope scope = scope(null, List.of("/version/**"));
        assertThat(scope.includes("guides/http-reference")).isTrue();
        assertThat(scope.includes("version/3.15/guides/http-reference")).isFalse();
    }

    @Test
    void leadingSlashIsOptionalOnBothSides() {
        assertThat(scope(List.of("guides/**"), null).includes("/guides/http-reference")).isTrue();
        assertThat(scope(List.of("/guides/**"), null).includes("guides/http-reference")).isTrue();
        assertThat(scope(List.of("glob:/guides/**"), null).includes("guides/http-reference")).isTrue();
        assertThat(scope(List.of("regex:/guides/.*"), null).includes("guides/http-reference")).isTrue();
    }

    @Test
    void singleStarDoesNotCrossASlashButDoubleStarDoes() {
        assertThat(scope(List.of("/guides/*"), null).includes("guides/http-reference")).isTrue();
        assertThat(scope(List.of("/guides/*"), null).includes("guides/nested/page")).isFalse();
        assertThat(scope(List.of("/guides/**"), null).includes("guides/nested/page")).isTrue();
    }

    @Test
    void anExplicitRegexIsHonoured() {
        final TwinScope scope = scope(List.of("regex:guides/.*"), null);
        assertThat(scope.includes("guides/http-reference")).isTrue();
        assertThat(scope.includes("guides/nested/page")).isTrue();
        assertThat(scope.includes("about")).isFalse();
    }

    /**
     * A regex is matched against the path string, not against a {@link java.nio.file.Path}, whose {@code toString()}
     * is backslash-separated on Windows. Without that, a regex written with slashes matches nothing on Windows.
     */
    @Test
    void aRegexSeparatorIsASlashOnEveryPlatform() {
        assertThat(scope(List.of("regex:guides/.*"), null).includes("guides/http-reference")).isTrue();
        assertThat(scope(null, List.of("regex:version/.*")).includes("version/3.15/guides/http-reference")).isFalse();
    }

    /**
     * A directory-style page has a trailing slash in its resource path, which a glob drops through {@code Path} but a
     * regex would see; both must match the page written without it.
     */
    @Test
    void aTrailingSlashOfADirectoryStylePageIsIgnored() {
        assertThat(scope(null, List.of("regex:guides/all-config")).includes("guides/all-config/")).isFalse();
        assertThat(scope(null, List.of("guides/all-config")).includes("guides/all-config/")).isFalse();
        assertThat(scope(List.of("regex:posts/welcome-to-roq"), null).includes("posts/welcome-to-roq/")).isTrue();
    }

    /** The landing page of a tree is in the tree: {@code /guides/**} covers {@code /guides/} and so does an exclude. */
    @Test
    void aTreePatternCoversTheLandingPageOfTheTree() {
        assertThat(scope(List.of("/guides/**"), null).includes("guides/")).isTrue();
        assertThat(scope(List.of("regex:guides/.*"), null).includes("guides/")).isTrue();
        assertThat(scope(null, List.of("/version/**")).includes("version/")).isFalse();
        assertThat(scope(List.of("/guides/**"), null).includes("guides")).isFalse();
        // A file-style page is matched as it is, never as an index.
        assertThat(scope(List.of("/guides/**"), null).includes("guides.html")).isFalse();
    }

    /** A pattern written with a trailing slash, the way a directory URL appears, names that page. */
    @Test
    void aTrailingSlashInAPatternIsIgnored() {
        assertThat(scope(List.of("/guides/all-config/"), null).includes("guides/all-config/")).isTrue();
        assertThat(scope(List.of("/guides/all-config/"), null).includes("guides/http-reference/")).isFalse();
        assertThat(scope(null, List.of("/guides/all-config/")).includes("guides/all-config/")).isFalse();
        assertThat(scope(List.of("/guides/*/"), null).includes("guides/all-config/")).isTrue();
        assertThat(scope(List.of("regex:guides/all-config/"), null).includes("guides/all-config/")).isTrue();
    }

    @Test
    void anInvalidPatternNamesThePropertyAndThePattern() {
        assertThatThrownBy(() -> scope(List.of("/guides/{http"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TwinScope.INCLUDE_PROPERTY)
                .hasMessageContaining("/guides/{http")
                .hasMessageContaining("\\,");
        assertThatThrownBy(() -> scope(null, List.of("regex:(")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TwinScope.EXCLUDE_PROPERTY);
    }

    @Test
    void blankPatternsAreIgnoredRatherThanMatchingNothing() {
        final TwinScope scope = scope(List.of("  "), null);
        assertThat(scope.includes("guides/http-reference")).isTrue();
    }
}
