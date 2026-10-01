package io.quarkiverse.roq.plugin.aliases.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code alias-page-conflict-site} (resource)
 * <p>
 * Features tested: build fails when an alias claims the path of an existing page.
 */
@DisplayName("Roq Aliases - Alias conflicting with a page path")
public class RoqAliasesPageConflictTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "alias-page-conflict-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("alias-page-conflict-site"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(RoqPathConflictException.class);
                assertThat(e.getMessage())
                        .contains("Path conflict")
                        // the order of the two sources depends on the build step order, so each is checked on its own
                        .contains("Duplicate path 'about/' claimed by both ")
                        .contains("page 'about.html'")
                        .contains("alias '/about' of page 'old-about.html'");
            });

    @Test
    @DisplayName("Build fails with RoqPathConflictException")
    public void testAliasConflictingWithPageFails() {
        // This test verifies that the build fails — the assertException above does the work
    }
}
