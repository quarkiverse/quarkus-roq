package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Site: {@code theme-layout-no-theme-site} (resource)
 * <p>
 * Verifies build fails when {@code theme-layout:} is used without a theme dependency.
 */
@DisplayName("Roq FrontMatter - theme-layout without theme fails")
public class RoqFrontMatterThemeLayoutNoThemeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "theme-layout-no-theme-site")
            .overrideConfigKey("site.theme", "")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("theme-layout-no-theme-site"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(RoqThemeConfigurationException.class);
            });

    @Test
    @DisplayName("Build fails with RoqThemeConfigurationException")
    public void testThemeLayoutWithoutThemeFails() {
        // The assertException above does the work
    }
}
