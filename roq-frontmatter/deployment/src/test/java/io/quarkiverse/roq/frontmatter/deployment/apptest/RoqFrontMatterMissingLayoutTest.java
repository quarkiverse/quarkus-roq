package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Site: {@code missing-layout-site} (resource)
 * <p>
 * Features tested: build fails with a clear error when a page references a layout that does not exist.
 */
@DisplayName("Roq FrontMatter - Missing layout error")
public class RoqFrontMatterMissingLayoutTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "missing-layout-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("missing-layout-site"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(RoqLayoutNotFoundException.class);
            });

    @Test
    @DisplayName("Build fails with RoqLayoutNotFoundException")
    public void testMissingLayoutFails() {
        // This test verifies that the build fails — the assertException above does the work
    }
}
