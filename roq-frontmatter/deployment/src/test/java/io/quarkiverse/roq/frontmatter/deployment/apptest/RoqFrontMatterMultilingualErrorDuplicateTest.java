package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code multilingual-errors/duplicate} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: two variants with the same language
 */
@DisplayName("Roq FrontMatter - Multilingual error: two variants with the same language")
public class RoqFrontMatterMultilingualErrorDuplicateTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = MultilingualErrorAsserts.failingSite("duplicate", "posts/a",
            "duplicate language");

    @Test
    public void buildFails() {
        // assertException above does the work
    }
}
