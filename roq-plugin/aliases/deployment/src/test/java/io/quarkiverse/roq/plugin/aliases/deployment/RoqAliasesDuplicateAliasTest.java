package io.quarkiverse.roq.plugin.aliases.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code alias-duplicate-site} (resource)
 * <p>
 * Features tested: build fails when two pages declare the same alias.
 */
@DisplayName("Roq Aliases - Same alias declared by two pages")
public class RoqAliasesDuplicateAliasTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "alias-duplicate-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("alias-duplicate-site"))
            .assertException(e -> {
                assertThat(e).isInstanceOf(RoqPathConflictException.class);
                assertThat(e.getMessage())
                        .contains("Path conflict")
                        .contains("Duplicate path 'legacy/' claimed by both ")
                        .contains("alias '/legacy' of page 'first.html'")
                        .contains("alias '/legacy' of page 'second.html'");
            });

    @Test
    @DisplayName("Build fails with RoqPathConflictException")
    public void testDuplicateAliasFails() {
        // This test verifies that the build fails — the assertException above does the work
    }
}
