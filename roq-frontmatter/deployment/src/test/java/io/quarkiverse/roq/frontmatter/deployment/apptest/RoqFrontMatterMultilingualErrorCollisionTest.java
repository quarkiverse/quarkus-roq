package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code multilingual-errors/collision} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: language code collides with a top-level content directory
 */
@DisplayName("Roq FrontMatter - Multilingual error: language code collides with a top-level content directory")
public class RoqFrontMatterMultilingualErrorCollisionTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = MultilingualErrorAsserts.failingSite("collision", "en", "top-level");

    @Test
    public void buildFails() {
        // assertException above does the work
    }
}
