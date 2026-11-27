package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code multilingual-errors/default-suffix} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: the default language used as a file suffix (index.fr.adoc) is a build error.
 */
@DisplayName("Roq FrontMatter - Multilingual error: default language used as a file suffix")
public class RoqFrontMatterMultilingualErrorDefaultSuffixTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = MultilingualErrorAsserts.failingSite("default-suffix", "index.fr.adoc",
            "is the default language");

    @Test
    public void buildFails() {
        // assertException above does the work
    }
}
