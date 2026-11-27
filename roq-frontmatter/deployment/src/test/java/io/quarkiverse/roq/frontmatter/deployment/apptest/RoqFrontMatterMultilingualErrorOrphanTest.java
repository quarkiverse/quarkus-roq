package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code multilingual-errors/orphan} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: orphan translation (no default variant)
 */
@DisplayName("Roq FrontMatter - Multilingual error: orphan translation (no default variant)")
public class RoqFrontMatterMultilingualErrorOrphanTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = MultilingualErrorAsserts.failingSite("orphan", "posts/a",
            "default-language variant");

    @Test
    public void buildFails() {
        // assertException above does the work
    }
}
