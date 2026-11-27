package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code multilingual-errors/two-defaults} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: two default-language variants (index.adoc and index.md) in a translated folder are two variants
 * with the same language.
 */
@DisplayName("Roq FrontMatter - Multilingual error: two default variants in a translated folder")
public class RoqFrontMatterMultilingualErrorTwoDefaultsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = MultilingualErrorAsserts.failingSite("two-defaults", "posts/a",
            "duplicate language");

    @Test
    public void buildFails() {
        // assertException above does the work
    }
}
