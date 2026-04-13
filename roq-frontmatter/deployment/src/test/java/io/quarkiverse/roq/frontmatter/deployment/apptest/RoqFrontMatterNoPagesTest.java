package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies the app starts when no content pages exist
 * (e.g. plugin tests where only base layouts are on the classpath).
 */
@DisplayName("Roq FrontMatter - No pages")
public class RoqFrontMatterNoPagesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.dir", "src/test/no-pages-site");

    @Test
    @DisplayName("App starts without content pages")
    public void testStartsWithoutPages() {
        // The app should start without errors when there are no content pages
    }
}
