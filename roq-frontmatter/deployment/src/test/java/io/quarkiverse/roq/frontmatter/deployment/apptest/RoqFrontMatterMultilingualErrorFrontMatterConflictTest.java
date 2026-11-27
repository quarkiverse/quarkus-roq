package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code multilingual-errors/fm-conflict} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: frontmatter lang differs from the suffix
 */
@DisplayName("Roq FrontMatter - Multilingual error: frontmatter lang differs from the suffix")
public class RoqFrontMatterMultilingualErrorFrontMatterConflictTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = MultilingualErrorAsserts.failingSite("fm-conflict", "index.en.adoc",
            "frontmatter lang");

    @Test
    public void buildFails() {
        // assertException above does the work
    }
}
