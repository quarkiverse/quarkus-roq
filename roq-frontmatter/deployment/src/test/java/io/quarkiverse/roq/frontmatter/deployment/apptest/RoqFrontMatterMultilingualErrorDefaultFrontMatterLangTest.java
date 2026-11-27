package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code multilingual-errors/default-fm-lang} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: frontmatter lang on the default variant differs from site.default-locale
 */
@DisplayName("Roq FrontMatter - Multilingual error: frontmatter lang on the default variant differs from site.default-locale")
public class RoqFrontMatterMultilingualErrorDefaultFrontMatterLangTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = MultilingualErrorAsserts.failingSite("default-fm-lang", "posts/a/index.adoc",
            "default-locale");

    @Test
    public void buildFails() {
        // assertException above does the work
    }
}
