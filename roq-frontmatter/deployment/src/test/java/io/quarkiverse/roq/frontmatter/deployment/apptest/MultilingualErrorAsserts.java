package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqFrontMatterConfigException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Builds a {@link QuarkusExtensionTest} on a {@code multilingual-errors/<case>} site (default locale fr) that
 * must fail the build with a {@link RoqFrontMatterConfigException} whose message
 * contains every expected fragment.
 */
final class MultilingualErrorAsserts {

    private MultilingualErrorAsserts() {
    }

    static QuarkusExtensionTest failingSite(String site, String... expectedFragments) {
        return new QuarkusExtensionTest()
                .overrideConfigKey("quarkus.roq.resource-dir", "multilingual-errors/" + site)
                .overrideConfigKey("site.default-locale", "fr")
                .withApplicationRoot((jar) -> jar.addAsResource("multilingual-errors/" + site))
                .assertException(e -> {
                    Throwable t = e;
                    while (t != null && !(t instanceof RoqFrontMatterConfigException)) {
                        t = t.getCause();
                    }
                    assertThat(t).as("a RoqFrontMatterConfigException in the cause chain of %s", e)
                            .isInstanceOf(RoqFrontMatterConfigException.class);
                    for (String fragment : expectedFragments) {
                        assertThat(t.getMessage()).contains(fragment);
                    }
                });
    }
}
